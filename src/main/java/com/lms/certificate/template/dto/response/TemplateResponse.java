package com.lms.certificate.template.dto.response;

import java.time.Instant;
import java.util.UUID;
public record TemplateResponse(
        UUID id,
        UUID organizationId,
        String backgroundImageUrl,
        String signatoryName,
        String signatoryTitle,
        String signatureImageUrl,
        String primaryColor,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {}