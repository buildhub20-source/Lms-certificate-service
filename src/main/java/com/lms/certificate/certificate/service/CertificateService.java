package com.lms.certificate.certificate.service;

import com.lms.certificate.certificate.dto.request.RevokeCertificateRequest;
import com.lms.certificate.certificate.dto.response.CertificateResponse;
import com.lms.certificate.certificate.dto.response.PublicCertificateResponse;
import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.certificate.entity.CertificateStatus;
import com.lms.certificate.certificate.repository.CertificateRepository;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.client.NotificationClient;
import com.lms.certificate.mail.CertificateMailService;
import com.lms.certificate.pdf.CertificatePdfGenerator;
import com.lms.certificate.storage.R2StorageService;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.service.TemplateResolver;
import com.lms.certificate.tenancy.TenantConnection;
import com.lms.certificate.tenancy.TenantContext;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository   certificateRepository;
    private final LmsInternalClient       lmsClient;
    private final CertificatePdfGenerator pdfGenerator;
    private final R2StorageService        storageService;
    private final CertificateMailService  mailService;
    private final TemplateResolver        templateResolver;
    private final NotificationClient      notificationClient;

    public CertificateService(CertificateRepository certificateRepository,
                              LmsInternalClient lmsClient,
                              CertificatePdfGenerator pdfGenerator,
                              R2StorageService storageService,
                              CertificateMailService mailService,
                              TemplateResolver templateResolver,
                              NotificationClient notificationClient) {
        this.certificateRepository = certificateRepository;
        this.lmsClient = lmsClient;
        this.pdfGenerator = pdfGenerator;
        this.storageService = storageService;
        this.mailService = mailService;
        this.templateResolver = templateResolver;
        this.notificationClient = notificationClient;
    }

    // ─── Public ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicCertificateResponse verify(String serialNumber) {
        Certificate cert = findBySerial(serialNumber);
        LmsInternalClient.UserDto   user   = lmsClient.getUser(cert.getStudentId());
        LmsInternalClient.CourseDto course = lmsClient.getCourse(cert.getCourseId());
        LmsInternalClient.OrgSettingsDto org = lmsClient.getOrgSettings();
        return new PublicCertificateResponse(
                cert.getSerialNumber(), user.name(), course.title(),
                org.name(), cert.getIssuedAt(), cert.getExpiresAt(), cert.getStatus());
    }

    // ─── Student ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CertificateResponse> listMyCertificates(UUID studentId, Pageable pageable) {
        return certificateRepository.findByStudentId(studentId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public byte[] downloadMyCertificate(UUID studentId, UUID certId) {
        Certificate cert = certificateRepository.findByIdAndStudentId(certId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + certId));
        if (cert.getStatus() != CertificateStatus.ACTIVE || (cert.getExpiresAt() != null && cert.getExpiresAt().isBefore(Instant.now()))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE, "Certificate is revoked or expired");
        }
        return pdfGenerator.generate(cert);
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
    public void handleEnrollmentCompleted(UUID studentId, UUID courseId) {
        log.info("Enrollment-completed event: student={} course={}", studentId, courseId);
        issue(studentId, courseId, null);
    }

    // ─── Private ───────────────────────────────────────────────────────────────

    private CertificateResponse doIssue(UUID studentId, UUID courseId) {
        String serial = generateSerialNumber();
        TenantConnection tenant = TenantContext.current().orElse(null);
        UUID tenantId = tenant != null ? tenant.tenantId() : null;

        Optional<CertificateTemplate> template = templateResolver.resolve(tenantId, courseId);

        Certificate cert = Certificate.builder()
                .serialNumber(serial)
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
     * Produces the PDF, stores it and tells the learner.
     *
     * <p>A failure here leaves the certificate PENDING rather than ACTIVE
     * without a PDF: a learner being shown a download that cannot work is
     * worse than one that is openly still being prepared, and pending
     * certificates are retried when a template is next published.
     */
    private void render(Certificate cert, CertificateTemplate template, TenantConnection tenant) {
        UUID tenantId = tenant != null ? tenant.tenantId() : null;
        try {
            byte[] pdf = pdfGenerator.generate(cert, template);
            cert.setPdfUrl(storageService.uploadCertificatePdf(pdf, tenantId, cert.getSerialNumber()));
            cert.setStatus(CertificateStatus.ACTIVE);
            Certificate stored = certificateRepository.save(cert);

            mailService.sendCertificateIssued(stored, pdf, tenant != null ? tenant.slug() : null);
            notificationClient.notifyCertificateReady(
                    tenantId, stored.getStudentId(), courseTitle(stored.getCourseId()), stored.getId());
        } catch (Exception ex) {
            cert.setStatus(CertificateStatus.PENDING);
            certificateRepository.save(cert);
            log.warn("Could not produce certificate {} - held pending: {}",
                    cert.getSerialNumber(), ex.getMessage());
        }
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

    private String courseTitle(UUID courseId) {
        try {
            LmsInternalClient.CourseDto course = lmsClient.getCourse(courseId);
            return course != null && course.title() != null ? course.title() : "your course";
        } catch (Exception ex) {
            return "your course";
        }
    }

    private CertificateResponse toResponse(Certificate cert) {
        LmsInternalClient.UserDto   user   = null;
        LmsInternalClient.CourseDto course = null;
        try {
            user   = lmsClient.getUser(cert.getStudentId());
            course = lmsClient.getCourse(cert.getCourseId());
        } catch (Exception ex) {
            log.debug("Could not enrich certificate {}: {}", cert.getId(), ex.getMessage());
        }
        return new CertificateResponse(
                cert.getId(), cert.getSerialNumber(),
                cert.getStudentId(), user   != null ? user.name()    : null,
                                     user   != null ? user.email()   : null,
                cert.getCourseId(),  course != null ? course.title() : null,
                cert.getIssuedAt(), cert.getExpiresAt(), cert.getStatus(),
                cert.getPdfUrl(), cert.getRevokedAt(), cert.getRevokeReason());
    }

    private Certificate findBySerial(String serialNumber) {
        return certificateRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + serialNumber));
    }

    private String generateSerialNumber() {
        String year   = String.valueOf(Year.now().getValue());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "CERT-" + year + "-" + suffix;
    }
}
