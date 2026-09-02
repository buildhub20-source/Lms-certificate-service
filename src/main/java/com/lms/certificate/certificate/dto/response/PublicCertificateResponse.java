package com.lms.certificate.certificate.dto.response;

import com.lms.certificate.certificate.entity.CertificateStatus;
import java.time.Instant;
import java.util.UUID;
/** Minimal public response for the verification endpoint (no PII). */
public record PublicCertificateResponse(
        String serialNumber,
        String studentName,
        String courseTitle,
        String organizationName,
        Instant issuedAt,
        Instant expiresAt,
        CertificateStatus status
) {}