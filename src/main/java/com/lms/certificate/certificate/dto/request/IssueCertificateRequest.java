package com.lms.certificate.certificate.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
/** Admin manually issues a certificate for a student+course pair. */
public record IssueCertificateRequest(
        @NotNull UUID studentId,
        @NotNull UUID courseId
) {}