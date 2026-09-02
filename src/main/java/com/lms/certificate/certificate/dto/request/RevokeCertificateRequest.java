package com.lms.certificate.certificate.dto.request;

import jakarta.validation.constraints.NotBlank;
/** Admin revokes an active certificate. */
public record RevokeCertificateRequest(
        @NotBlank String reason
) {}