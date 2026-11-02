package com.lms.certificate.certificate.dto.response;

import com.lms.certificate.certificate.entity.CertificateStatus;
import java.time.Instant;
/**
 * What the public verification page shows: enough for someone holding the
 * certificate to compare it, and nothing else about the learner.
 */
public record PublicCertificateResponse(
        String serialNumber,
        String studentName,
        String courseTitle,
        String organizationName,
        Instant issuedAt,
        Instant expiresAt,
        CertificateStatus status,
        VerificationState verificationState,
        Instant revokedAt
) {
    /** The answer to "can this certificate be trusted right now?". */
    public enum VerificationState { VALID, REVOKED, EXPIRED }
}
