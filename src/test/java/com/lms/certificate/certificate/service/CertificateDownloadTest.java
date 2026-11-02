package com.lms.certificate.certificate.service;

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
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Downloading a certificate, and checking one from its QR code. */
@DisplayName("downloading and verifying a certificate")
class CertificateDownloadTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    private final CertificateRepository certificates = mock(CertificateRepository.class);
    private final LmsInternalClient lmsClient = mock(LmsInternalClient.class);
    private final CertificatePdfGenerator pdfGenerator = mock(CertificatePdfGenerator.class);
    private final TemplateResolver resolver = mock(TemplateResolver.class);

    private final CertificateService service = new CertificateService(
            certificates, lmsClient, pdfGenerator, mock(CertificateMailService.class), resolver,
            mock(NotificationClient.class), new VerificationConfig());

    private final UUID studentId = UUID.randomUUID();
    private final UUID certId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(certificates.save(any(Certificate.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Certificate issued(CertificateStatus status) {
        CertificateTemplate original = new CertificateTemplate();
        original.setSignatoryName("Dr. Rao");
        original.setPrimaryColor("#112233");
        Certificate cert = Certificate.builder()
                .serialNumber("CERT-2026-AB12CD34").verificationToken(TOKEN)
                .studentId(studentId).courseId(UUID.randomUUID())
                .issuedAt(Instant.now().minus(30, ChronoUnit.DAYS))
                .recipientName("Ada Learner").courseTitle("Data Structures").issuerName("SRP Institute")
                .status(status).build();
        cert.setId(certId);
        cert.setTemplateSnapshot(TemplateSnapshot.of(original, null).toJson());
        return cert;
    }

    private void owned(Certificate cert) {
        when(certificates.findByIdAndStudentId(certId, studentId)).thenReturn(Optional.of(cert));
    }

    private static int statusOf(Throwable thrown) {
        return ((ResponseStatusException) thrown).getStatusCode().value();
    }

    @Test
    @DisplayName("renders from what was fixed at issue, not from today's template or names")
    void rendersFromSnapshot() {
        owned(issued(CertificateStatus.ACTIVE));
        when(pdfGenerator.generate(any(), any(), anyString())).thenReturn(new byte[] {7});

        CertificateService.CertificateFile file = service.downloadMyCertificate(studentId, certId);

        assertThat(file.content()).containsExactly(7);
        assertThat(file.filename()).isEqualTo("certificate-CERT-2026-AB12CD34.pdf");
        ArgumentCaptor<CertificateTemplate> template = ArgumentCaptor.forClass(CertificateTemplate.class);
        verify(pdfGenerator).generate(any(), template.capture(), eq("http://localhost:3000/verify/t/" + TOKEN));
        assertThat(template.getValue().getSignatoryName()).isEqualTo("Dr. Rao");
        // Neither the current template nor the LMS is consulted.
        verifyNoInteractions(resolver, lmsClient);
        verify(certificates, never()).save(any());
    }

    @Test
    @DisplayName("someone else's certificate is simply not found")
    void otherStudentsCertificateIsNotFound() {
        when(certificates.findByIdAndStudentId(certId, studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadMyCertificate(studentId, certId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("a revoked certificate is gone (410)")
    void revokedIsGone() {
        owned(issued(CertificateStatus.REVOKED));

        assertThatThrownBy(() -> service.downloadMyCertificate(studentId, certId))
                .satisfies(thrown -> assertThat(statusOf(thrown)).isEqualTo(410));
        verifyNoInteractions(pdfGenerator);
    }

    @Test
    @DisplayName("an expired certificate is gone (410)")
    void expiredIsGone() {
        Certificate cert = issued(CertificateStatus.ACTIVE);
        cert.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        owned(cert);

        assertThatThrownBy(() -> service.downloadMyCertificate(studentId, certId))
                .satisfies(thrown -> assertThat(statusOf(thrown)).isEqualTo(410));
    }

    @Test
    @DisplayName("a pending certificate that still has no template is a conflict (409)")
    void pendingIsConflict() {
        owned(issued(CertificateStatus.PENDING));
        when(resolver.resolve(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadMyCertificate(studentId, certId))
                .satisfies(thrown -> assertThat(statusOf(thrown)).isEqualTo(409));
        verifyNoInteractions(pdfGenerator);
    }

    @Test
    @DisplayName("a pending certificate whose template has since appeared completes on download")
    void pendingCompletesOnDownload() {
        Certificate cert = issued(CertificateStatus.PENDING);
        owned(cert);
        when(resolver.resolve(any(), any())).thenReturn(Optional.of(new CertificateTemplate()));
        when(lmsClient.getUser(any())).thenReturn(new LmsInternalClient.UserDto(studentId, "Ada Learner", null));
        when(lmsClient.getCourse(any())).thenReturn(
                new LmsInternalClient.CourseDto(cert.getCourseId(), "Data Structures", null, null, null));
        when(lmsClient.getOrgSettings()).thenReturn(
                new LmsInternalClient.OrgSettingsDto("SRP Institute", null, null, null, null));
        when(pdfGenerator.generate(any(), any(), anyString())).thenReturn(new byte[] {1});

        service.downloadMyCertificate(studentId, certId);

        assertThat(cert.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
    }

    @Test
    @DisplayName("a certificate issued before snapshots is fixed on its first download")
    void legacyCertificateIsSnapshottedOnce() {
        Certificate cert = issued(CertificateStatus.ACTIVE);
        cert.setTemplateSnapshot(null);
        cert.setRecipientName(null);
        owned(cert);
        CertificateTemplate current = new CertificateTemplate();
        current.setSignatoryName("Current Signatory");
        when(resolver.resolve(any(), any())).thenReturn(Optional.of(current));
        when(lmsClient.getUser(any())).thenReturn(new LmsInternalClient.UserDto(studentId, "Ada Learner", null));
        when(lmsClient.getCourse(any())).thenReturn(
                new LmsInternalClient.CourseDto(cert.getCourseId(), "Data Structures", null, null, null));
        when(lmsClient.getOrgSettings()).thenReturn(
                new LmsInternalClient.OrgSettingsDto("SRP Institute", null, null, null, null));
        when(pdfGenerator.generate(any(), any(), anyString())).thenReturn(new byte[] {1});

        service.downloadMyCertificate(studentId, certId);

        verify(certificates).save(cert);
        assertThat(cert.getRecipientName()).isEqualTo("Ada Learner");
        assertThat(TemplateSnapshot.fromJson(cert.getTemplateSnapshot()).signatoryName())
                .isEqualTo("Current Signatory");
    }

    // ─── Verification ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a valid certificate verifies from its own record, without asking the LMS")
    void validByToken() {
        when(certificates.findByVerificationToken(TOKEN)).thenReturn(Optional.of(issued(CertificateStatus.ACTIVE)));

        PublicCertificateResponse result = service.verifyByToken(TOKEN);

        assertThat(result.verificationState()).isEqualTo(PublicCertificateResponse.VerificationState.VALID);
        assertThat(result.studentName()).isEqualTo("Ada Learner");
        assertThat(result.courseTitle()).isEqualTo("Data Structures");
        assertThat(result.organizationName()).isEqualTo("SRP Institute");
        assertThat(result.serialNumber()).isEqualTo("CERT-2026-AB12CD34");
        verifyNoInteractions(lmsClient);
    }

    @Test
    @DisplayName("a revoked certificate verifies as revoked")
    void revokedByToken() {
        Certificate cert = issued(CertificateStatus.REVOKED);
        cert.setRevokedAt(Instant.now());
        when(certificates.findByVerificationToken(TOKEN)).thenReturn(Optional.of(cert));

        PublicCertificateResponse result = service.verifyByToken(TOKEN);

        assertThat(result.verificationState()).isEqualTo(PublicCertificateResponse.VerificationState.REVOKED);
        assertThat(result.revokedAt()).isNotNull();
    }

    @Test
    @DisplayName("an expired certificate verifies as expired")
    void expiredByToken() {
        Certificate cert = issued(CertificateStatus.ACTIVE);
        cert.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(certificates.findByVerificationToken(TOKEN)).thenReturn(Optional.of(cert));

        assertThat(service.verifyByToken(TOKEN).verificationState())
                .isEqualTo(PublicCertificateResponse.VerificationState.EXPIRED);
    }

    @Test
    @DisplayName("an unknown or not-yet-issued certificate is not found")
    void pendingOrUnknownIsNotFound() {
        when(certificates.findByVerificationToken(TOKEN)).thenReturn(Optional.of(issued(CertificateStatus.PENDING)));
        assertThatThrownBy(() -> service.verifyByToken(TOKEN)).isInstanceOf(EntityNotFoundException.class);

        when(certificates.findByVerificationToken("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyByToken("missing")).isInstanceOf(EntityNotFoundException.class);
    }
}
