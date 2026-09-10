package com.lms.certificate.template.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.lms.certificate.template.entity.CertificateTemplateSource;
public record TemplateResponse(
        UUID id,
        UUID organizationId,
        String name,
        CertificateTemplateSource sourceType,
        String pdfTemplateUrl,
        String fieldLayout,
        String backgroundImageUrl,
        String signatoryName,
        String signatoryTitle,
        String signatureImageUrl,
        String primaryColor,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {}
