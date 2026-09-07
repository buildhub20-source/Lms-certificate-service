package com.lms.certificate.certificate.entity;

import com.lms.certificate.common.audit.Timestamped;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/**
 * Represents a single e-certificate issued to a student for completing a course.
 * UUID references to LMS-BackEnd resources — no cross-service foreign keys.
 */
@Entity
@Table(
    name = "certificates",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cert_serial", columnNames = "serial_number"),
        @UniqueConstraint(name = "uq_cert_student_course", columnNames = {"student_id", "course_id"})
    }
)
public class Certificate extends Timestamped {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    /** Human-readable unique identifier. Format: CERT-YYYY-XXXXXXXX */
    @Column(name = "serial_number", nullable = false, updatable = false, length = 30)
    private String serialNumber;
    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;
    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;
    @Column(name = "organization_id")
    private UUID organizationId;
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "pdf_url", length = 1024)
    private String pdfUrl;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CertificateStatus status = CertificateStatus.ACTIVE;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "revoked_by")
    private UUID revokedBy;
    @Column(name = "revoke_reason", columnDefinition = "TEXT")
    private String revokeReason;
    // ─── Getters ──────────────────────────────────────────────────────────────
    public UUID getId()                   { return id; }
    public String getSerialNumber()       { return serialNumber; }
    public UUID getStudentId()            { return studentId; }
    public UUID getCourseId()             { return courseId; }
    public UUID getOrganizationId()       { return organizationId; }
    public Instant getIssuedAt()          { return issuedAt; }
    public Instant getExpiresAt()         { return expiresAt; }
    public String getPdfUrl()             { return pdfUrl; }
    public CertificateStatus getStatus()  { return status; }
    public Instant getRevokedAt()         { return revokedAt; }
    public UUID getRevokedBy()            { return revokedBy; }
    public String getRevokeReason()       { return revokeReason; }
    // ─── Setters ──────────────────────────────────────────────────────────────
    public void setId(UUID id)                           { this.id = id; }
    public void setSerialNumber(String serialNumber)     { this.serialNumber = serialNumber; }
    public void setStudentId(UUID studentId)             { this.studentId = studentId; }
    public void setCourseId(UUID courseId)               { this.courseId = courseId; }
    public void setOrganizationId(UUID v)                { this.organizationId = v; }
    public void setIssuedAt(Instant issuedAt)            { this.issuedAt = issuedAt; }
    public void setExpiresAt(Instant expiresAt)          { this.expiresAt = expiresAt; }
    public void setPdfUrl(String pdfUrl)                 { this.pdfUrl = pdfUrl; }
    public void setStatus(CertificateStatus status)      { this.status = status; }
    public void setRevokedAt(Instant revokedAt)          { this.revokedAt = revokedAt; }
    public void setRevokedBy(UUID revokedBy)             { this.revokedBy = revokedBy; }
    public void setRevokeReason(String revokeReason)     { this.revokeReason = revokeReason; }
    // ─── Builder ──────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final Certificate c = new Certificate();
        public Builder serialNumber(String v)       { c.serialNumber = v;   return this; }
        public Builder studentId(UUID v)            { c.studentId = v;      return this; }
        public Builder courseId(UUID v)             { c.courseId = v;       return this; }
        public Builder organizationId(UUID v)       { c.organizationId = v; return this; }
        public Builder issuedAt(Instant v)          { c.issuedAt = v;       return this; }
        public Builder expiresAt(Instant v)         { c.expiresAt = v;      return this; }
        public Builder pdfUrl(String v)             { c.pdfUrl = v;         return this; }
        public Builder status(CertificateStatus v)  { c.status = v;         return this; }
        public Certificate build()                  { return c; }
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Certificate c)) return false;
        return id != null && id.equals(c.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
