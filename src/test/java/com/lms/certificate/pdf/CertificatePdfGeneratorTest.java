package com.lms.certificate.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.certificate.entity.CertificateStatus;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Renders real PDFs, in memory, from a certificate's snapshot. */
@DisplayName("rendering a certificate PDF")
class CertificatePdfGeneratorTest {

    private final LmsInternalClient lmsClient = mock(LmsInternalClient.class);
    private final CertificatePdfGenerator generator = new CertificatePdfGenerator(lmsClient, new ObjectMapper());

    private Certificate snapshotted() {
        return Certificate.builder()
                .serialNumber("CERT-2026-AB12CD34").verificationToken("token")
                .studentId(UUID.randomUUID()).courseId(UUID.randomUUID())
                .issuedAt(Instant.parse("2026-11-03T10:00:00Z"))
                .recipientName("Ada Learner").courseTitle("Data Structures").issuerName("SRP Institute")
                .status(CertificateStatus.ACTIVE).build();
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    @Test
    @DisplayName("prints the snapshot and a QR caption without calling the LMS")
    void rendersFromSnapshotWithQr() throws Exception {
        CertificateTemplate template = new CertificateTemplate();
        template.setPrimaryColor("#112233");

        byte[] pdf = generator.generate(snapshotted(), template, "https://lms.example/verify/t/token");

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = text(pdf);
        // The organisation name is styled upper-case.
        assertThat(text).contains("Ada Learner", "Data Structures", "SRP INSTITUTE");
        // Officials compare this ID with the verification page, so it must be printed.
        assertThat(text).contains("Certificate ID: CERT-2026-AB12CD34");
        assertThat(text).contains("Scan to verify");
        verifyNoInteractions(lmsClient);
    }

    @Test
    @DisplayName("without a verification link no QR code is drawn")
    void noQrWithoutLink() throws Exception {
        CertificateTemplate template = new CertificateTemplate();
        template.setPrimaryColor("#112233");

        byte[] pdf = generator.generate(snapshotted(), template, null);

        assertThat(text(pdf)).doesNotContain("Scan to verify");
    }

    @Test
    @DisplayName("the same certificate renders the same text every time")
    void repeatDownloadsMatch() throws Exception {
        CertificateTemplate template = TemplateSnapshot.fromJson(
                TemplateSnapshot.of(new CertificateTemplate(), "#112233").toJson()).toTemplate();

        String first = text(generator.generate(snapshotted(), template, "https://lms.example/verify/t/token"));
        String second = text(generator.generate(snapshotted(), template, "https://lms.example/verify/t/token"));

        assertThat(second).isEqualTo(first);
    }
}
