package com.lms.certificate.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.entity.CertificateTemplateSource;
import com.lms.certificate.template.repository.CertificateTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final ObjectMapper objectMapper;
    public CertificatePdfGenerator(CertificateTemplateRepository templateRepository, LmsInternalClient lmsClient,
                                   ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.lmsClient = lmsClient;
        this.objectMapper = objectMapper;
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
                .findByOrganizationIdAndDefaultTemplateTrue(cert.getOrganizationId())
                .or(templateRepository::findByDefaultTemplateTrue)
                .orElseGet(CertificateTemplate::new);
        if (template.getSourceType() == CertificateTemplateSource.INSTITUTION_PDF
                && template.getPdfTemplateUrl() != null && !template.getPdfTemplateUrl().isBlank()) {
            return stampInstitutionPdf(user, course, org, cert, template);
        }
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

    /**
     * Produces a new, learner-specific PDF on top of the original institution template.
     * Coordinates use PDF points, measured from the lower-left of the page. The source PDF in
     * R2 is read-only and is never changed.
     */
    private byte[] stampInstitutionPdf(LmsInternalClient.UserDto user,
                                       LmsInternalClient.CourseDto course,
                                       LmsInternalClient.OrgSettingsDto org,
                                       Certificate cert,
                                       CertificateTemplate template) {
        Map<String, FieldPlacement> layout = parseLayout(template.getFieldLayout());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("learnerFullName", user.name());
        values.put("courseTitle", course.title());
        values.put("organizationName", org.name());
        values.put("issuedDate", DATE_FMT.format(cert.getIssuedAt()));
        values.put("certificateNumber", cert.getSerialNumber());

        try (InputStream input = openTemplate(template.getPdfTemplateUrl());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(input);
            try (PdfStamper stamper = new PdfStamper(reader, output)) {
                BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    FieldPlacement placement = layout.get(entry.getKey());
                    if (placement == null || entry.getValue() == null) continue;
                    int page = Math.max(1, Math.min(placement.page(), reader.getNumberOfPages()));
                    PdfContentByte layer = stamper.getOverContent(page);
                    layer.beginText();
                    layer.setFontAndSize(baseFont, Math.max(6, Math.min(72, placement.fontSize())));
                    layer.setColorFill(java.awt.Color.BLACK);
                    layer.endText();
                    int alignment = "RIGHT".equalsIgnoreCase(placement.align()) ? ElementAlignment.RIGHT
                            : "LEFT".equalsIgnoreCase(placement.align()) ? ElementAlignment.LEFT
                            : ElementAlignment.CENTER;
                    ColumnText.showTextAligned(layer, alignment, new Phrase(entry.getValue(), new Font(baseFont,
                            Math.max(6, Math.min(72, placement.fontSize())))), placement.x(), placement.y(), 0);
                }
            } finally {
                reader.close();
            }
            log.info("Stamped institution template for certificate {}", cert.getSerialNumber());
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Institution PDF stamping failed for " + cert.getSerialNumber(), ex);
        }
    }

    private InputStream openTemplate(String templateUrl) throws Exception {
        URLConnection connection = URI.create(templateUrl).toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(15_000);
        return connection.getInputStream();
    }

    private Map<String, FieldPlacement> parseLayout(String fieldLayout) {
        if (fieldLayout == null || fieldLayout.isBlank()) {
            return Map.of("learnerFullName", new FieldPlacement(1, 421, 330, 24, "CENTER"));
        }
        try {
            Map<String, FieldPlacement> parsed = objectMapper.readValue(fieldLayout,
                    new TypeReference<Map<String, FieldPlacement>>() { });
            return parsed == null || parsed.isEmpty()
                    ? Map.of("learnerFullName", new FieldPlacement(1, 421, 330, 24, "CENTER")) : parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Certificate template fieldLayout is not valid JSON", ex);
        }
    }

    private record FieldPlacement(int page, float x, float y, float fontSize, String align) { }

    /** Avoids coupling the rendering code to OpenPDF's integer constants in callers. */
    private static final class ElementAlignment {
        private static final int LEFT = com.lowagie.text.Element.ALIGN_LEFT;
        private static final int CENTER = com.lowagie.text.Element.ALIGN_CENTER;
        private static final int RIGHT = com.lowagie.text.Element.ALIGN_RIGHT;
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
