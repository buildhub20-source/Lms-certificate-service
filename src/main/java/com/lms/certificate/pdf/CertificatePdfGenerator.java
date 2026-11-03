package com.lms.certificate.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.client.LmsInternalClient;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.entity.CertificateTemplateSource;
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
 *
 * <p>The PDF only ever exists in memory: it is produced for each download and
 * never stored. Every certificate carries a QR code linking to its public
 * verification page.
 */
@Service
public class CertificatePdfGenerator {
    private static final Logger log = LoggerFactory.getLogger(CertificatePdfGenerator.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM dd, yyyy").withZone(ZoneOffset.UTC);
    /** Side of the QR code in PDF points (72 pt = 1 inch): large enough to scan from a printout. */
    private static final float QR_SIZE = 72f;
    private static final float QR_MARGIN = 28f;
    private static final String QR_FIELD = "verificationQr";

    private final LmsInternalClient lmsClient;
    private final ObjectMapper objectMapper;
    public CertificatePdfGenerator(LmsInternalClient lmsClient, ObjectMapper objectMapper) {
        this.lmsClient = lmsClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Renders the certificate in memory.
     *
     * <p>The text comes from what was fixed on the certificate when it was
     * issued; the LMS is asked only for anything not yet fixed, which is the
     * case for previews and for certificates issued before that was kept.
     *
     * @param template  the template to render with: the certificate's own
     *                  snapshot for a download, the live one for a preview
     * @param verifyUrl the public verification link encoded in the QR code
     */
    public byte[] generate(Certificate cert, CertificateTemplate template, String verifyUrl) {
        Printed printed = printed(cert, template);
        byte[] pdf;
        if (template.getSourceType() == CertificateTemplateSource.INSTITUTION_PDF
                && template.getPdfTemplateUrl() != null && !template.getPdfTemplateUrl().isBlank()) {
            pdf = stampInstitutionPdf(printed, cert, template);
        } else {
            pdf = renderHtml(printed, cert, template);
        }
        return addVerificationQr(pdf, verifyUrl, parseLayout(template.getFieldLayout()).get(QR_FIELD),
                cert.getSerialNumber());
    }

    /** What is printed on a certificate besides its template's own settings. */
    public record Printed(String recipientName, String courseTitle, String issuerName, String primaryColor) { }

    /**
     * Resolves what will be printed, asking the LMS only for what the
     * certificate does not already hold.
     */
    public Printed printed(Certificate cert, CertificateTemplate template) {
        String recipient = cert.getRecipientName();
        String course    = cert.getCourseTitle();
        String issuer    = cert.getIssuerName();
        String color     = template.getPrimaryColor();
        if (recipient == null) recipient = lmsClient.getUser(cert.getStudentId()).name();
        if (course == null)    course    = lmsClient.getCourse(cert.getCourseId()).title();
        if (issuer == null || color == null) {
            LmsInternalClient.OrgSettingsDto org = lmsClient.getOrgSettings();
            if (issuer == null) issuer = org.name();
            if (color == null)  color  = org.primaryColor();
        }
        return new Printed(recipient, course, issuer, color != null ? color : "#3B82F6");
    }

    private byte[] renderHtml(Printed printed, Certificate cert, CertificateTemplate template) {
        String html = buildHtml(printed, cert, template);
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
     * Draws the verification QR code onto the certificate.
     *
     * <p>It is drawn as vector squares on a white backing, so it stays sharp
     * when printed and scannable on a dark or busy background. Institution
     * templates can position it with a {@code verificationQr} entry in their
     * field layout (x and y are its lower-left corner, {@code size} its side);
     * otherwise it sits in the bottom-right corner of the first page.
     */
    private byte[] addVerificationQr(byte[] pdf, String verifyUrl, FieldPlacement placement, String serial) {
        if (verifyUrl == null || verifyUrl.isBlank()) {
            return pdf;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(pdf);
            try (PdfStamper stamper = new PdfStamper(reader, output)) {
                int page = placement == null ? 1 : Math.max(1, Math.min(placement.page(), reader.getNumberOfPages()));
                Rectangle bounds = reader.getPageSize(page);
                float size = placement != null && placement.size() != null && placement.size() > 0
                        ? Math.max(48, Math.min(200, placement.size())) : QR_SIZE;
                float x = placement != null ? placement.x() : bounds.getRight() - QR_MARGIN - size;
                float y = placement != null ? placement.y() : bounds.getBottom() + QR_MARGIN;

                BitMatrix matrix = new QRCodeWriter().encode(verifyUrl, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                        EncodeHintType.MARGIN, 0,
                        EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
                int modules = matrix.getWidth();
                float module = size / modules;
                float quiet = module * 2;
                float caption = 9;

                PdfContentByte layer = stamper.getOverContent(page);
                layer.saveState();
                layer.setColorFill(java.awt.Color.WHITE);
                layer.rectangle(x - quiet, y - quiet - caption, size + 2 * quiet, size + 2 * quiet + caption);
                layer.fill();
                layer.setColorFill(java.awt.Color.BLACK);
                for (int row = 0; row < modules; row++) {
                    for (int col = 0; col < modules; col++) {
                        if (matrix.get(col, row)) {
                            layer.rectangle(x + col * module, y + size - (row + 1) * module, module, module);
                        }
                    }
                }
                layer.fill();
                layer.restoreState();

                BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                ColumnText.showTextAligned(layer, ElementAlignment.CENTER,
                        new Phrase("Scan to verify", new Font(font, 6.5f)), x + size / 2, y - caption + 1, 0);
            } finally {
                reader.close();
            }
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not add verification QR to " + serial, ex);
        }
    }

    /**
     * Produces a new, learner-specific PDF on top of the original institution template.
     * Coordinates use PDF points, measured from the lower-left of the page. The source PDF in
     * R2 is read-only and is never changed.
     */
    private byte[] stampInstitutionPdf(Printed printed, Certificate cert, CertificateTemplate template) {
        Map<String, FieldPlacement> layout = parseLayout(template.getFieldLayout());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("learnerFullName", printed.recipientName());
        values.put("courseTitle", printed.courseTitle());
        values.put("organizationName", printed.issuerName());
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

    /** {@code size} is read only for the QR code's placement. */
    private record FieldPlacement(int page, float x, float y, float fontSize, String align, Float size) {
        FieldPlacement(int page, float x, float y, float fontSize, String align) {
            this(page, x, y, fontSize, align, null);
        }
    }

    /** Avoids coupling the rendering code to OpenPDF's integer constants in callers. */
    private static final class ElementAlignment {
        private static final int LEFT = com.lowagie.text.Element.ALIGN_LEFT;
        private static final int CENTER = com.lowagie.text.Element.ALIGN_CENTER;
        private static final int RIGHT = com.lowagie.text.Element.ALIGN_RIGHT;
    }

    // ─── HTML builder ─────────────────────────────────────────────────────────
    private String buildHtml(Printed printed, Certificate cert, CertificateTemplate template) {
        String primaryColor = printed.primaryColor();
        String bgStyle = template.getBackgroundImageUrl() != null
                ? "background-image: url('" + template.getBackgroundImageUrl() + "'); background-size: cover;"
                : "background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);";
        String issuedDate = DATE_FMT.format(cert.getIssuedAt());
        String signatoryName  = template.getSignatoryName()  != null ? template.getSignatoryName()  : printed.issuerName();
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
                .serial { font-size: 8pt; color: #64748b; margin-top: 12pt; letter-spacing: 1px; }
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
                  <div class="footer-block">
                    %s
                    <div class="footer-line">%s</div>
                    <div class="footer-label">%s</div>
                  </div>
                </div>
                <div class="serial">Certificate ID: %s</div>
              </div>
            </body>
            </html>
            """.formatted(
                bgStyle, primaryColor, primaryColor,
                printed.issuerName(),
                printed.recipientName(),
                printed.courseTitle(),
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
