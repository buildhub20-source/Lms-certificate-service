package com.lms.certificate.certificate.service;

import com.lms.certificate.certificate.dto.request.RevokeCertificateRequest;
import com.lms.certificate.certificate.dto.response.CertificateResponse;
import com.lms.certificate.certificate.dto.response.PublicCertificateResponse;
import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.certificate.entity.CertificateStatus;
import com.lms.certificate.certificate.repository.CertificateRepository;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.client.NotificationClient;
import com.lms.certificate.config.VerificationConfig;
import com.lms.certificate.mail.CertificateMailService;
import com.lms.certificate.pdf.CertificatePdfGenerator;
import com.lms.certificate.pdf.TemplateSnapshot;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.service.TemplateResolver;
import com.lms.certificate.tenancy.TenantConnection;
import com.lms.certificate.tenancy.TenantContext;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository   certificateRepository;
    private final LmsInternalClient       lmsClient;
    private final CertificatePdfGenerator pdfGenerator;
    private final CertificateMailService  mailService;
    private final TemplateResolver        templateResolver;
    private final NotificationClient      notificationClient;
    private final VerificationConfig      verificationConfig;

    public CertificateService(CertificateRepository certificateRepository,
                              LmsInternalClient lmsClient,
                              CertificatePdfGenerator pdfGenerator,
                              CertificateMailService mailService,
                              TemplateResolver templateResolver,
                              NotificationClient notificationClient,
                              VerificationConfig verificationConfig) {
        this.certificateRepository = certificateRepository;
        this.lmsClient = lmsClient;
        this.pdfGenerator = pdfGenerator;
        this.mailService = mailService;
        this.templateResolver = templateResolver;
        this.notificationClient = notificationClient;
        this.verificationConfig = verificationConfig;
    }

    /** A rendered certificate, ready to be returned as a download. */
    public record CertificateFile(String filename, byte[] content) { }

    // ─── Public ────────────────────────────────────────────────────────────────

    /** Verification by the serial number printed on the certificate. */
    @Transactional(readOnly = true)
    public PublicCertificateResponse verify(String serialNumber) {
        return toPublic(certificateRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found")));
    }

    /** Verification by the token in the certificate's QR code. */
    @Transactional(readOnly = true)
    public PublicCertificateResponse verifyByToken(String token) {
        return toPublic(certificateRepository.findByVerificationToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found")));
    }

    // ─── Student ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CertificateResponse> listMyCertificates(UUID studentId, Pageable pageable) {
        return certificateRepository.findByStudentId(studentId, pageable).map(this::toResponse);
    }

    /**
     * Renders the student's certificate in memory. Nothing is stored.
     *
     * <p>A pending certificate is given one more chance to complete here, in
     * case it was held only because the LMS could not be reached when it was
     * issued.
     *
     * @throws EntityNotFoundException not this student's certificate (404,
     *         so that certificate ids cannot be probed)
     * @throws ResponseStatusException 409 while pending, 410 once revoked or expired
     */
    @Transactional
    public CertificateFile downloadMyCertificate(UUID studentId, UUID certId) {
        Certificate cert = certificateRepository.findByIdAndStudentId(certId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + certId));
        if (cert.getStatus() == CertificateStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.GONE, "This certificate has been revoked");
        }
        if (isExpired(cert)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This certificate has expired");
        }

        TenantConnection tenant = TenantContext.current().orElse(null);
        if (cert.getStatus() == CertificateStatus.PENDING) {
            templateResolver.resolve(cert.getOrganizationId(), cert.getCourseId())
                    .ifPresent(template -> render(cert, template, tenant));
            if (cert.getStatus() == CertificateStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This certificate is still being prepared");
            }
        }

        CertificateTemplate template;
        if (cert.hasSnapshot()) {
            template = TemplateSnapshot.fromJson(cert.getTemplateSnapshot()).toTemplate();
        } else {
            // Issued before snapshots were kept: fix it now, so this and every
            // later download match.
            Optional<CertificateTemplate> current =
                    templateResolver.resolve(cert.getOrganizationId(), cert.getCourseId());
            if (current.isPresent()) {
                captureSnapshot(cert, current.get());
                certificateRepository.save(cert);
                template = TemplateSnapshot.fromJson(cert.getTemplateSnapshot()).toTemplate();
            } else {
                template = new CertificateTemplate();
            }
        }

        byte[] pdf = pdfGenerator.generate(cert, template, verifyUrl(cert, tenant));
        return new CertificateFile("certificate-" + safeFilenamePart(cert.getSerialNumber()) + ".pdf", pdf);
    }

    @Transactional(readOnly = true)
    public CertificateResponse getMyCertificate(UUID studentId, UUID certId) {
        Certificate cert = certificateRepository.findByIdAndStudentId(certId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + certId));
        return toResponse(cert);
    }

    // ─── Admin ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CertificateResponse> listAll(Pageable pageable) {
        return certificateRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CertificateResponse> listByCourse(UUID courseId, Pageable pageable) {
        return certificateRepository.findByCourseId(courseId, pageable).map(this::toResponse);
    }

    /**
     * Issues a certificate for a student+course pair. Idempotent — returns existing cert if already issued.
     */
    @Transactional
    public CertificateResponse issue(UUID studentId, UUID courseId, UUID issuedByAdminId) {
        return certificateRepository.findByStudentIdAndCourseId(studentId, courseId)
                .map(existing -> {
                    log.info("Certificate already exists: {}", existing.getSerialNumber());
                    return toResponse(existing);
                })
                .orElseGet(() -> doIssue(studentId, courseId));
    }

    @Transactional
    public CertificateResponse revoke(UUID certId, UUID adminId, RevokeCertificateRequest request) {
        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + certId));
        if (cert.getStatus() == CertificateStatus.REVOKED) {
            throw new IllegalStateException("Certificate already revoked: " + certId);
        }
        cert.setStatus(CertificateStatus.REVOKED);
        cert.setRevokedAt(Instant.now());
        cert.setRevokedBy(adminId);
        cert.setRevokeReason(request.reason());
        return toResponse(certificateRepository.save(cert));
    }

    // ─── Internal webhook ──────────────────────────────────────────────────────

    @Transactional
    public CertificateResponse handleEnrollmentCompleted(UUID studentId, UUID courseId) {
        log.info("Enrollment-completed event: student={} course={}", studentId, courseId);
        return issue(studentId, courseId, null);
    }

    // ─── Private ───────────────────────────────────────────────────────────────

    private CertificateResponse doIssue(UUID studentId, UUID courseId) {
        String serial = generateSerialNumber();
        TenantConnection tenant = TenantContext.current().orElse(null);
        UUID tenantId = tenant != null ? tenant.tenantId() : null;

        Optional<CertificateTemplate> template = templateResolver.resolve(tenantId, courseId);

        Certificate cert = Certificate.builder()
                .serialNumber(serial)
                .verificationToken(generateVerificationToken())
                .studentId(studentId)
                .courseId(courseId)
                .organizationId(tenantId)
                .issuedAt(Instant.now())
                // Earned either way; whether it can be rendered yet is what
                // separates PENDING from ACTIVE.
                .status(template.isPresent() ? CertificateStatus.ACTIVE : CertificateStatus.PENDING)
                .build();

        Certificate saved = certificateRepository.save(cert);

        if (template.isEmpty()) {
            log.info("Certificate {} held pending: no published template for course {}", serial, courseId);
            mailService.sendTemplateMissing(saved, courseTitle(courseId), tenant != null ? tenant.slug() : null);
            return toResponse(saved);
        }

        render(saved, template.get(), tenant);
        log.info("Certificate issued: {} student={} course={}", serial, studentId, courseId);
        return toResponse(saved);
    }

    /**
     * Fixes what the certificate will print, proves it renders, and tells the
     * learner. The rendered PDF is only attached to the email; it is not stored.
     *
     * <p>A failure here leaves the certificate PENDING rather than ACTIVE: a
     * learner being shown a download that cannot work is worse than one that is
     * openly still being prepared. Pending certificates are retried when a
     * template is next published, and when the learner tries to download.
     */
    private void render(Certificate cert, CertificateTemplate template, TenantConnection tenant) {
        UUID tenantId = tenant != null ? tenant.tenantId() : null;
        try {
            captureSnapshot(cert, template);
            byte[] pdf = pdfGenerator.generate(cert,
                    TemplateSnapshot.fromJson(cert.getTemplateSnapshot()).toTemplate(), verifyUrl(cert, tenant));
            cert.setStatus(CertificateStatus.ACTIVE);
            Certificate stored = certificateRepository.save(cert);

            mailService.sendCertificateIssued(stored, pdf, tenant != null ? tenant.slug() : null);
            notificationClient.notifyCertificateReady(
                    tenantId, stored.getStudentId(), stored.getCourseTitle(), stored.getId());
        } catch (Exception ex) {
            cert.setStatus(CertificateStatus.PENDING);
            certificateRepository.save(cert);
            log.warn("Could not produce certificate {} - held pending: {}",
                    cert.getSerialNumber(), ex.getMessage());
        }
    }

    /**
     * Records the names and template settings printed on the certificate, so
     * that later renames and template edits leave it unchanged.
     */
    private void captureSnapshot(Certificate cert, CertificateTemplate template) {
        LmsInternalClient.UserDto        user   = lmsClient.getUser(cert.getStudentId());
        LmsInternalClient.CourseDto      course = lmsClient.getCourse(cert.getCourseId());
        LmsInternalClient.OrgSettingsDto org    = lmsClient.getOrgSettings();
        if (user == null || user.name() == null || course == null || course.title() == null) {
            throw new IllegalStateException("Learner or course details are unavailable");
        }
        cert.setRecipientName(user.name());
        cert.setCourseTitle(course.title());
        cert.setIssuerName(org != null && org.name() != null ? org.name() : template.getSignatoryName());
        cert.setTemplateId(template.getId());
        cert.setTemplateSnapshot(TemplateSnapshot.of(template, org != null ? org.primaryColor() : null).toJson());
    }

    /**
     * Completes the certificates that were waiting on a template for this
     * course. Called when one is published.
     *
     * @return how many were completed
     */
    @Transactional
    public int completePendingForCourse(UUID courseId) {
        TenantConnection tenant = TenantContext.current().orElse(null);
        UUID tenantId = tenant != null ? tenant.tenantId() : null;

        Optional<CertificateTemplate> template = templateResolver.resolve(tenantId, courseId);
        if (template.isEmpty()) {
            return 0;
        }

        List<Certificate> waiting =
                certificateRepository.findByCourseIdAndStatus(courseId, CertificateStatus.PENDING);
        int completed = 0;
        for (Certificate cert : waiting) {
            render(cert, template.get(), tenant);
            if (cert.getStatus() == CertificateStatus.ACTIVE) {
                completed++;
            }
        }
        if (completed > 0) {
            log.info("Completed {} pending certificate(s) for course {}", completed, courseId);
        }
        return completed;
    }

    private String verifyUrl(Certificate cert, TenantConnection tenant) {
        return verificationConfig.verifyUrl(cert.getVerificationToken(), tenant != null ? tenant.slug() : null);
    }

    private static boolean isExpired(Certificate cert) {
        return cert.getExpiresAt() != null && cert.getExpiresAt().isBefore(Instant.now());
    }

    private String courseTitle(UUID courseId) {
        try {
            LmsInternalClient.CourseDto course = lmsClient.getCourse(courseId);
            return course != null && course.title() != null ? course.title() : "your course";
        } catch (Exception ex) {
            return "your course";
        }
    }

    /**
     * Pending certificates are not public: they have not been issued yet and
     * their token appears on nothing, so they verify as not found.
     */
    private PublicCertificateResponse toPublic(Certificate cert) {
        if (cert.getStatus() == CertificateStatus.PENDING) {
            throw new EntityNotFoundException("Certificate not found");
        }
        String recipient = cert.getRecipientName();
        String course    = cert.getCourseTitle();
        String issuer    = cert.getIssuerName();
        if (recipient == null) recipient = lmsClient.getUser(cert.getStudentId()).name();
        if (course == null)    course    = lmsClient.getCourse(cert.getCourseId()).title();
        if (issuer == null)    issuer    = lmsClient.getOrgSettings().name();

        PublicCertificateResponse.VerificationState state =
                cert.getStatus() == CertificateStatus.REVOKED ? PublicCertificateResponse.VerificationState.REVOKED
                : isExpired(cert) ? PublicCertificateResponse.VerificationState.EXPIRED
                : PublicCertificateResponse.VerificationState.VALID;
        return new PublicCertificateResponse(
                cert.getSerialNumber(), recipient, course, issuer,
                cert.getIssuedAt(), cert.getExpiresAt(), cert.getStatus(), state, cert.getRevokedAt());
    }

    private CertificateResponse toResponse(Certificate cert) {
        LmsInternalClient.UserDto   user   = null;
        LmsInternalClient.CourseDto course = null;
        try {
            user = lmsClient.getUser(cert.getStudentId());
            if (cert.getCourseTitle() == null) {
                course = lmsClient.getCourse(cert.getCourseId());
            }
        } catch (Exception ex) {
            log.debug("Could not enrich certificate {}: {}", cert.getId(), ex.getMessage());
        }
        String courseTitle = cert.getCourseTitle() != null ? cert.getCourseTitle()
                : course != null ? course.title() : null;
        String studentName = cert.getRecipientName() != null ? cert.getRecipientName()
                : user != null ? user.name() : null;
        TenantConnection tenant = TenantContext.current().orElse(null);
        return new CertificateResponse(
                cert.getId(), cert.getSerialNumber(),
                cert.getStudentId(), studentName,
                                     user != null ? user.email() : null,
                cert.getCourseId(),  courseTitle,
                cert.getIssuedAt(), cert.getExpiresAt(), cert.getStatus(),
                cert.getStatus() == CertificateStatus.PENDING ? null : verifyUrl(cert, tenant),
                cert.getRevokedAt(), cert.getRevokeReason());
    }

    /** Serials are generated here, but stay defensive about what reaches a header. */
    private static String safeFilenamePart(String value) {
        return value == null ? "download" : value.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private String generateSerialNumber() {
        String year   = String.valueOf(Year.now().getValue());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "CERT-" + year + "-" + suffix;
    }

    /** 256 random bits, URL-safe: 43 characters. */
    private static String generateVerificationToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
