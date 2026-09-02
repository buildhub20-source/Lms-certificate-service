package com.lms.certificate.certificate.service;

import com.lms.certificate.certificate.dto.request.RevokeCertificateRequest;
import com.lms.certificate.certificate.dto.response.CertificateResponse;
import com.lms.certificate.certificate.dto.response.PublicCertificateResponse;
import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.certificate.entity.CertificateStatus;
import com.lms.certificate.certificate.repository.CertificateRepository;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.mail.CertificateMailService;
import com.lms.certificate.pdf.CertificatePdfGenerator;
import com.lms.certificate.storage.R2StorageService;
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
import java.util.UUID;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository   certificateRepository;
    private final LmsInternalClient       lmsClient;
    private final CertificatePdfGenerator pdfGenerator;
    private final R2StorageService        storageService;
    private final CertificateMailService  mailService;

    public CertificateService(CertificateRepository certificateRepository,
                              LmsInternalClient lmsClient,
                              CertificatePdfGenerator pdfGenerator,
                              R2StorageService storageService,
                              CertificateMailService mailService) {
        this.certificateRepository = certificateRepository;
        this.lmsClient = lmsClient;
        this.pdfGenerator = pdfGenerator;
        this.storageService = storageService;
        this.mailService = mailService;
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
        return certificateRepository.findAll().stream()
                .filter(c -> c.getStudentId().equals(studentId) && c.getCourseId().equals(courseId))
                .findFirst()
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
        Certificate cert = Certificate.builder()
                .serialNumber(serial)
                .studentId(studentId)
                .courseId(courseId)
                .organizationId(tenant != null ? tenant.tenantId() : null)
                .issuedAt(Instant.now())
                .status(CertificateStatus.ACTIVE)
                .build();

        Certificate saved = certificateRepository.save(cert);

        try {
            byte[] pdf = pdfGenerator.generate(saved);
            String pdfUrl = storageService.uploadCertificatePdf(pdf,
                    tenant != null ? tenant.tenantId() : null, serial);
            saved.setPdfUrl(pdfUrl);
            saved = certificateRepository.save(saved);
            mailService.sendCertificateIssued(saved, pdf, tenant != null ? tenant.slug() : null);
        } catch (Exception ex) {
            log.warn("PDF/upload failed for certificate {} — cert saved without PDF: {}",
                    serial, ex.getMessage());
        }

        log.info("Certificate issued: {} student={} course={}", serial, studentId, courseId);
        return toResponse(saved);
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
