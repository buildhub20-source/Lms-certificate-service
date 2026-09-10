package com.lms.certificate.template.entity;

import com.lms.certificate.common.audit.Timestamped;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
/**
 * Defines the visual design of a certificate for a specific organisation.
 *
 * <p>When {@code organizationId} is {@code null} this record is the global
 * platform default template, used when no org-specific template exists.
 */
@Entity
@Table(name = "certificate_templates")
public class CertificateTemplate extends Timestamped {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "organization_id")
    private UUID organizationId;
    @Column(name = "name", nullable = false, length = 160)
    private String name = "Certificate template";
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private CertificateTemplateSource sourceType = CertificateTemplateSource.LMS_RENDERED;
    /** Original institution PDF. It is never modified; every issued certificate is a stamped copy. */
    @Column(name = "pdf_template_url", length = 1024)
    private String pdfTemplateUrl;
    /** JSON object of field names to page/x/y/fontSize placement values, in PDF points. */
    @Column(name = "field_layout", columnDefinition = "TEXT")
    private String fieldLayout;
    @Column(name = "background_image_url", length = 1024)
    private String backgroundImageUrl;
    @Column(name = "signatory_name", length = 160)
    private String signatoryName;
    @Column(name = "signatory_title", length = 160)
    private String signatoryTitle;
    @Column(name = "signature_image_url", length = 1024)
    private String signatureImageUrl;
    @Column(name = "primary_color", length = 16)
    private String primaryColor;
    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;
    // ─── Getters ──────────────────────────────────────────────────────────────
    public UUID getId()                   { return id; }
    public UUID getOrganizationId()       { return organizationId; }
    public String getName()               { return name; }
    public CertificateTemplateSource getSourceType() { return sourceType; }
    public String getPdfTemplateUrl()     { return pdfTemplateUrl; }
    public String getFieldLayout()        { return fieldLayout; }
    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public String getSignatoryName()      { return signatoryName; }
    public String getSignatoryTitle()     { return signatoryTitle; }
    public String getSignatureImageUrl()  { return signatureImageUrl; }
    public String getPrimaryColor()       { return primaryColor; }
    public boolean isDefaultTemplate()    { return defaultTemplate; }
    // ─── Setters ──────────────────────────────────────────────────────────────
    public void setId(UUID id)                             { this.id = id; }
    public void setOrganizationId(UUID organizationId)     { this.organizationId = organizationId; }
    public void setName(String v)                           { this.name = v; }
    public void setSourceType(CertificateTemplateSource v) { this.sourceType = v == null ? CertificateTemplateSource.LMS_RENDERED : v; }
    public void setPdfTemplateUrl(String v)                { this.pdfTemplateUrl = v; }
    public void setFieldLayout(String v)                   { this.fieldLayout = v; }
    public void setBackgroundImageUrl(String v)            { this.backgroundImageUrl = v; }
    public void setSignatoryName(String v)                 { this.signatoryName = v; }
    public void setSignatoryTitle(String v)                { this.signatoryTitle = v; }
    public void setSignatureImageUrl(String v)             { this.signatureImageUrl = v; }
    public void setPrimaryColor(String v)                  { this.primaryColor = v; }
    public void setDefaultTemplate(boolean v)              { this.defaultTemplate = v; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CertificateTemplate t)) return false;
        return id != null && id.equals(t.id);
    }
    public int hashCode() { return Objects.hashCode(id); }
}
