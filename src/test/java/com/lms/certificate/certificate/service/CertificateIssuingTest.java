package com.lms.certificate.certificate.service;

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
    private final R2StorageService storage = mock(R2StorageService.class);
    private final CertificateMailService mail = mock(CertificateMailService.class);
    private final TemplateResolver resolver = mock(TemplateResolver.class);
    private final NotificationClient notifications = mock(NotificationClient.class);

    private final CertificateService service = new CertificateService(
            certificates, lmsClient, pdfGenerator, storage, mail, resolver, notifications);

    private final UUID studentId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(certificates.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        // save() returns what it was given, as the real repository does.
        when(certificates.save(any(Certificate.class))).thenAnswer(call -> call.getArgument(0));
        when(lmsClient.getCourse(any())).thenReturn(
                new LmsInternalClient.CourseDto(courseId, "Data Structures", null, null, null));
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
        verify(pdfGenerator, never()).generate(any(), any());
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
    @DisplayName("with a published template the certificate is produced and delivered")
    void issuedWhenTemplateExists() {
        CertificateTemplate template = new CertificateTemplate();
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), eq(template))).thenReturn(new byte[] {1, 2, 3});
        when(storage.uploadCertificatePdf(any(), any(), anyString())).thenReturn("https://files/cert.pdf");

        service.issue(studentId, courseId, null);

        Certificate saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
        assertThat(saved.getPdfUrl()).isEqualTo("https://files/cert.pdf");
        verify(mail).sendCertificateIssued(any(), any(), any());
        verify(notifications).notifyCertificateReady(any(), eq(studentId), eq("Data Structures"), any());
    }

    @Test
    @DisplayName("a failure while rendering holds the certificate rather than leaving it undownloadable")
    void renderFailureHoldsRatherThanIssues() {
        CertificateTemplate template = new CertificateTemplate();
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), eq(template)))
                .thenThrow(new IllegalStateException("renderer unavailable"));

        service.issue(studentId, courseId, null);

        // ACTIVE with no PDF would show the learner a download that cannot work.
        assertThat(captureSaved().getStatus()).isEqualTo(CertificateStatus.PENDING);
        verify(mail, never()).sendCertificateIssued(any(), any(), any());
    }

    @Test
    @DisplayName("publishing a template releases the certificates that were waiting")
    void pendingAreReleasedOnPublish() {
        CertificateTemplate template = new CertificateTemplate();
        when(resolver.resolve(any(), eq(courseId))).thenReturn(Optional.of(template));
        when(pdfGenerator.generate(any(), eq(template))).thenReturn(new byte[] {1});
        when(storage.uploadCertificatePdf(any(), any(), anyString())).thenReturn("https://files/cert.pdf");

        Certificate waiting = Certificate.builder()
                .serialNumber("CERT-PENDING-1").studentId(studentId).courseId(courseId)
                .status(CertificateStatus.PENDING).build();
        when(certificates.findByCourseIdAndStatus(courseId, CertificateStatus.PENDING))
                .thenReturn(List.of(waiting));

        int completed = service.completePendingForCourse(courseId);

        assertThat(completed).isEqualTo(1);
        assertThat(waiting.getStatus()).isEqualTo(CertificateStatus.ACTIVE);
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
