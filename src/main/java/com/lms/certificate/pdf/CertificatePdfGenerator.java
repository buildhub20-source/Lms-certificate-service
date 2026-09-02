package com.lms.certificate.pdf;

import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.repository.CertificateTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;
import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
/**
 * Generates a PDF certificate from an HTML template using Flying Saucer.
 *
 * <p>The template is a Thymeleaf-style HTML string populated via simple
 * string replacement to avoid a Thymeleaf dependency. The HTML is rendered
 * to PDF by Flying Saucer (XHTML renderer) + OpenPDF.
 */
@Service
public class CertificatePdfGenerator {
    private static final Logger log = LoggerFactory.getLogger(CertificatePdfGenerator.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM dd, yyyy").withZone(ZoneOffset.UTC);
    private final CertificateTemplateRepository templateRepository;
    private final LmsInternalClient lmsClient;
    public CertificatePdfGenerator(CertificateTemplateRepository templateRepository, LmsInternalClient lmsClient) {
        this.templateRepository = templateRepository;
        this.lmsClient = lmsClient;
    }
    /**
     * Generates a PDF byte array for the given certificate.
     *
     * @param cert the certificate to render
     * @return PDF bytes
     */
    public byte[] generate(Certificate cert) {
        LmsInternalClient.UserDto    user    = lmsClient.getUser(cert.getStudentId());
        LmsInternalClient.CourseDto  course  = lmsClient.getCourse(cert.getCourseId());
        LmsInternalClient.OrgSettingsDto org = lmsClient.getOrgSettings();
        CertificateTemplate template = templateRepository
                .findByOrganizationId(cert.getOrganizationId() != null ? cert.getOrganizationId() : null)
                .or(templateRepository::findByDefaultTemplateTrue)
                .orElseGet(CertificateTemplate::new);
        String html = buildHtml(user, course, org, cert, template);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            log.info("Generated PDF for certificate {}", cert.getSerialNumber());
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF generation failed for " + cert.getSerialNumber(), ex);
        }
    }

    // ─── HTML builder ─────────────────────────────────────────────────────────
    private String buildHtml(LmsInternalClient.UserDto user,
                             LmsInternalClient.CourseDto course,
                             LmsInternalClient.OrgSettingsDto org,
                             Certificate cert,
                             CertificateTemplate template) {
        String primaryColor = template.getPrimaryColor() != null
                ? template.getPrimaryColor()
                : (org.primaryColor() != null ? org.primaryColor() : "#3B82F6");
        String bgStyle = template.getBackgroundImageUrl() != null
                ? "background-image: url('" + template.getBackgroundImageUrl() + "'); background-size: cover;"
                : "background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);";
        String issuedDate = DATE_FMT.format(cert.getIssuedAt());
        String signatoryName  = template.getSignatoryName()  != null ? template.getSignatoryName()  : org.name();
        String signatoryTitle = template.getSignatoryTitle() != null ? template.getSignatoryTitle() : "Platform Administrator";
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
              <style type="text/css">
                @page { size: A4 landscape; margin: 0; }
                body { margin: 0; padding: 0; font-family: Georgia, serif; %s }
                .container { width: 277mm; height: 190mm; display: flex; flex-direction: column;
                             align-items: center; justify-content: center; padding: 24pt; box-sizing: border-box; }
                .org-name { font-size: 13pt; color: #64748b; letter-spacing: 3px;
                            text-transform: uppercase; margin-bottom: 8pt; }
                .title { font-size: 36pt; font-weight: bold; color: %s; margin-bottom: 6pt; }
                .subtitle { font-size: 11pt; color: #64748b; margin-bottom: 20pt; }
                .student-name { font-size: 26pt; font-weight: bold; color: #1e293b;
                                border-bottom: 2pt solid %s; padding-bottom: 6pt; margin-bottom: 10pt; }
                .course-label { font-size: 10pt; color: #64748b; margin-bottom: 4pt; }
                .course-title { font-size: 16pt; font-weight: bold; color: #334155; margin-bottom: 24pt; }
                .footer { display: flex; justify-content: space-between; width: 100%%; margin-top: 20pt; }
                .footer-block { text-align: center; }
                .footer-line { font-size: 9pt; color: #64748b; }
                .footer-label { font-size: 8pt; color: #94a3b8; letter-spacing: 1px;
                                text-transform: uppercase; margin-top: 2pt; }
                .sig-img { height: 32pt; margin-bottom: 4pt; }
                .serial { font-size: 7pt; color: #cbd5e1; margin-top: 12pt; letter-spacing: 1px; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="org-name">%s</div>
                <div class="title">Certificate of Completion</div>
                <div class="subtitle">This is to certify that</div>
                <div class="student-name">%s</div>
                <div class="course-label">has successfully completed</div>
                <div class="course-title">%s</div>
                <div class="footer">
                  <div class="footer-block">
                    <div class="footer-line">%s</div>
                    <div class="footer-label">Issue Date</div>
                  </div>
                    %s
                    <div class="footer-label">%s</div>
                </div>
                <div class="serial">Certificate ID: %s</div>
              </div>
            </body>
            </html>
            """.formatted(
                bgStyle, primaryColor, primaryColor,
                org.name(),
                user.name(),
                course.title(),
                issuedDate,
                template.getSignatureImageUrl() != null
                    ? "<img class='sig-img' src='" + template.getSignatureImageUrl() + "' alt='Signature'/>"
                    : "",
                signatoryName,
                signatoryTitle,
                cert.getSerialNumber()
        );
    }
}