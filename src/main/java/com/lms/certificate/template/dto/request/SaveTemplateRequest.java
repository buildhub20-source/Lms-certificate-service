package com.lms.certificate.template.dto.request;

import java.util.UUID;
public record SaveTemplateRequest(
        UUID organizationId,        // null = global default
        String backgroundImageUrl,
        String signatoryName,
        String signatoryTitle,
        String signatureImageUrl,
        String primaryColor,
        boolean isDefault
) {}