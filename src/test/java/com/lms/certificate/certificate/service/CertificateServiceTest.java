package com.lms.certificate.certificate.service;

import com.lms.certificate.certificate.dto.response.CertificateResponse;
import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.certificate.entity.CertificateStatus;
import com.lms.certificate.certificate.repository.CertificateRepository;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.mail.CertificateMailService;
import com.lms.certificate.pdf.CertificatePdfGenerator;
import com.lms.certificate.storage.R2StorageService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CertificateServiceTest {

    private final CertificateRepository certificateRepository = mock(CertificateRepository.class);
    private final LmsInternalClient lmsClient = mock(LmsInternalClient.class);
    private final CertificatePdfGenerator pdfGenerator = mock(CertificatePdfGenerator.class);
    private final R2StorageService storageService = mock(R2StorageService.class);
    private final CertificateMailService mailService = mock(CertificateMailService.class);
    private final CertificateService service = new CertificateService(
            certificateRepository, lmsClient, pdfGenerator, storageService, mailService);

    @Test
    void issueReturnsExistingCertificateUsingTargetedStudentAndCourseLookup() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Certificate existing = Certificate.builder()
                .serialNumber("CERT-2026-EXISTING")
                .studentId(studentId)
                .courseId(courseId)
                .issuedAt(Instant.now())
                .status(CertificateStatus.ACTIVE)
                .build();

        when(certificateRepository.findByStudentIdAndCourseId(studentId, courseId))
                .thenReturn(Optional.of(existing));
        when(lmsClient.getUser(studentId))
                .thenReturn(new LmsInternalClient.UserDto(studentId, "Ada Learner", "ada@example.test"));
        when(lmsClient.getCourse(courseId))
                .thenReturn(new LmsInternalClient.CourseDto(courseId, "Secure LMS", null, null, null));

        CertificateResponse result = service.issue(studentId, courseId, UUID.randomUUID());

        assertThat(result.serialNumber()).isEqualTo("CERT-2026-EXISTING");
        verify(certificateRepository).findByStudentIdAndCourseId(studentId, courseId);
        verify(certificateRepository, never()).findAll();
        verify(certificateRepository, never()).save(any());
        verifyNoInteractions(pdfGenerator, storageService, mailService);
    }
}
