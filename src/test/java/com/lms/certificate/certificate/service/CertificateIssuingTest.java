package com.lms.certificate.certificate.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens when a learner earns a certificate, depending on whether the
 * organisation has published a template for that course.
 */
@DisplayName("issuing a certificate")
class CertificateIssuingTest {

    private final CertificateRepository certificates = mock(CertificateRepository.class);
    private final LmsInternalClient lmsClient = mock(LmsInternalClient.class);
    private final CertificatePdfGenerator pdfGenerator = mock(CertificatePdfGenerator.class);
    private final CertificateMailService mail = mock(CertificateMailService.class);
    private final TemplateResolver resolver = mock(TemplateResolver.class);
    private final NotificationClient notifications = mock(NotificationClient.class);

    private final CertificateService service = new CertificateService(
            certificates, lmsClient, pdfGenerator, mail, resolver, notifications, new VerificationConfig());

    private final UUID studentId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(certificates.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        // save() returns what it was given, as the real repository does.
        when(certificates.save(any(Certificate.class))).thenAnswer(call -> call.getArgument(0));
        when(lmsClient.getUser(any())).thenReturn(
                new LmsInternalClient.UserDto(studentId, "Ada Learner", "ada@example.test"));
        when(lmsClient.getCourse(any())).thenReturn(
                new LmsInternalClient.CourseDto(courseId, "Data Structures", null, null, null));
        when(lmsClient.getOrgSettings()).thenReturn(
                new LmsInternalClient.OrgSettingsDto("SRP Institute", null, null, "#112233", null));
    }

    private Certificate captureSaved() {
        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificates, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("with no published template the certificate is held, not issued empty")
    void heldWhenNoTemplate() {
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.empty());

        service.issue(studentId, courseId, null);

        assertThat(captureSaved().getStatus()).isEqualTo(CertificateStatus.PENDING);
        // Nothing to render from, so nothing is attempted or promised.
        verify(pdfGenerator, never()).generate(any(), any(), any());
        verify(mail, never()).sendCertificateIssued(any(), any(), any());
        verify(notifications, never()).notifyCertificateReady(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("the organisation is told a certificate is waiting on a template")
    void organisationIsNotified() {
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.empty());

        service.issue(studentId, courseId, null);

        verify(mail).sendTemplateMissing(any(Certificate.class), eq("Data Structures"), any());
    }

    @Test
    @DisplayName("with a published template the certificate is produced and delivered, but not stored")
    void issuedWhenTemplateExists() {
        CertificateTemplate template = new CertificateTemplate();
        template.setSignatoryName("Dr. Rao");
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), any(), anyString())).thenReturn(new byte[] {1, 2, 3});

        service.issue(studentId, courseId, null);

        Certificate saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
        assertThat(saved.getPdfUrl()).isNull();
        verify(mail).sendCertificateIssued(any(), eq(new byte[] {1, 2, 3}), any());
        verify(notifications).notifyCertificateReady(any(), eq(studentId), eq("Data Structures"), any());
    }

    @Test
    @DisplayName("what is printed is fixed when the certificate is issued")
    void snapshotIsFixedAtIssue() {
        CertificateTemplate template = new CertificateTemplate();
        template.setSignatoryName("Dr. Rao");
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), any(), anyString())).thenReturn(new byte[] {1});

        service.issue(studentId, courseId, null);

        Certificate saved = captureSaved();
        assertThat(saved.getRecipientName()).isEqualTo("Ada Learner");
        assertThat(saved.getCourseTitle()).isEqualTo("Data Structures");
        assertThat(saved.getIssuerName()).isEqualTo("SRP Institute");
        TemplateSnapshot snapshot = TemplateSnapshot.fromJson(saved.getTemplateSnapshot());
        assertThat(snapshot.signatoryName()).isEqualTo("Dr. Rao");
        // The template set no colour, so the organisation's at the time is kept.
        assertThat(snapshot.primaryColor()).isEqualTo("#112233");
    }

    @Test
    @DisplayName("each certificate gets its own unguessable verification link")
    void verificationTokenIsIssued() {
        CertificateTemplate template = new CertificateTemplate();
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), any(), anyString())).thenReturn(new byte[] {1});

        service.issue(studentId, courseId, null);

        String token = captureSaved().getVerificationToken();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        verify(pdfGenerator).generate(any(), any(), eq("http://localhost:3000/verify/t/" + token));
    }

    @Test
    @DisplayName("a failure while rendering holds the certificate rather than leaving it undownloadable")
    void renderFailureHoldsRatherThanIssues() {
        CertificateTemplate template = new CertificateTemplate();
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), any(), anyString()))
                .thenThrow(new IllegalStateException("renderer unavailable"));

        service.issue(studentId, courseId, null);

        // ACTIVE with no PDF would show the learner a download that cannot work.
        assertThat(captureSaved().getStatus()).isEqualTo(CertificateStatus.PENDING);
        verify(mail, never()).sendCertificateIssued(any(), any(), any());
    }

    @Test
    @DisplayName("an unreachable LMS holds the certificate rather than issuing it with blank names")
    void lmsFailureHolds() {
        CertificateTemplate template = new CertificateTemplate();
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(lmsClient.getUser(any())).thenThrow(new IllegalStateException("LMS down"));

        service.issue(studentId, courseId, null);

        assertThat(captureSaved().getStatus()).isEqualTo(CertificateStatus.PENDING);
        verify(pdfGenerator, never()).generate(any(), any(), any());
    }

    @Test
    @DisplayName("publishing a template releases the certificates that were waiting")
    void pendingAreReleasedOnPublish() {
        CertificateTemplate template = new CertificateTemplate();
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), any(), anyString())).thenReturn(new byte[] {1});

        Certificate waiting = Certificate.builder()
                .serialNumber("CERT-PENDING-1").verificationToken("t".repeat(43))
                .studentId(studentId).courseId(courseId)
                .status(CertificateStatus.PENDING).build();
        when(certificates.findByCourseIdAndStatus(courseId, CertificateStatus.PENDING))
                .thenReturn(List.of(waiting));

        int completed = service.completePendingForCourse(courseId);

        assertThat(completed).isEqualTo(1);
        assertThat(waiting.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
        assertThat(waiting.hasSnapshot()).isTrue();
        verify(mail).sendCertificateIssued(any(), any(), any());
    }

    @Test
    @DisplayName("nothing is released while the course still has no template")
    void nothingReleasedWithoutTemplate() {
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.empty());

        assertThat(service.completePendingForCourse(courseId)).isZero();
        verify(certificates, never()).findByCourseIdAndStatus(any(), any());
    }
}
