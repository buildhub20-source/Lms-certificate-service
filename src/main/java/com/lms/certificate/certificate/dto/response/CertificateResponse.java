package com.lms.certificate.certificate.dto.response;

import com.lms.certificate.certificate.entity.CertificateStatus;
import java.time.Instant;
import java.util.UUID;
/** Full certificate response — admin and student views. */
public record CertificateResponse(
        UUID id,
        String serialNumber,
        UUID studentId,
        String studentName,
        String studentEmail,
        UUID courseId,
        String courseTitle,
        Instant issuedAt,
        Instant expiresAt,
        CertificateStatus status,
        String pdfUrl,
        Instant revokedAt,
        String revokeReason
) {}