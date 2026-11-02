package com.lms.certificate.pdf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.entity.CertificateTemplateSource;

/**
 * The template settings a certificate was issued with.
 *
 * <p>Templates stay editable after they are published, and certificates are
 * rendered again on every download, so each certificate keeps its own copy of
 * the settings it was issued with. Editing a template only changes the
 * certificates issued after the edit.
 *
 * <p>An institution PDF's own file is referenced by URL, not copied.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateSnapshot(
        CertificateTemplateSource sourceType,
        String pdfTemplateUrl,
        String fieldLayout,
        String backgroundImageUrl,
        String signatoryName,
        String signatoryTitle,
        String signatureImageUrl,
        String primaryColor
) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param fallbackColor the organisation's colour, used when the template
     *                      sets none, so a later rebrand does not recolour it
     */
    public static TemplateSnapshot of(CertificateTemplate template, String fallbackColor) {
        return new TemplateSnapshot(
                template.getSourceType(),
                template.getPdfTemplateUrl(),
                template.getFieldLayout(),
                template.getBackgroundImageUrl(),
                template.getSignatoryName(),
                template.getSignatoryTitle(),
                template.getSignatureImageUrl(),
                template.getPrimaryColor() != null ? template.getPrimaryColor() : fallbackColor);
    }

    /** A detached template for the renderer. It is never saved. */
    public CertificateTemplate toTemplate() {
        CertificateTemplate template = new CertificateTemplate();
        template.setSourceType(sourceType);
        template.setPdfTemplateUrl(pdfTemplateUrl);
        template.setFieldLayout(fieldLayout);
        template.setBackgroundImageUrl(backgroundImageUrl);
        template.setSignatoryName(signatoryName);
        template.setSignatoryTitle(signatoryTitle);
        template.setSignatureImageUrl(signatureImageUrl);
        template.setPrimaryColor(primaryColor);
        return template;
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise template snapshot", ex);
        }
    }

    public static TemplateSnapshot fromJson(String json) {
        try {
            return JSON.readValue(json, TemplateSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored template snapshot is not valid", ex);
        }
    }
}
