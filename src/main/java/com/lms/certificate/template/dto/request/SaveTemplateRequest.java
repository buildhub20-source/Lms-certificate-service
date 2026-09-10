package com.lms.certificate.template.dto.request;

import java.util.UUID;
import com.lms.certificate.template.entity.CertificateTemplateSource;
public record SaveTemplateRequest(
        UUID organizationId,        // null = global default
        String name,
        CertificateTemplateSource sourceType,
        String fieldLayout,
        String backgroundImageUrl,
        String signatoryName,
        String signatoryTitle,
        String signatureImageUrl,
        String primaryColor,
        boolean isDefault
) {}
