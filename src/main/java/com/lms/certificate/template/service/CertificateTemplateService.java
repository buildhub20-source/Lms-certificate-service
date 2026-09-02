package com.lms.certificate.template.service;

import com.lms.certificate.template.dto.request.SaveTemplateRequest;
import com.lms.certificate.template.dto.response.TemplateResponse;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.repository.CertificateTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CertificateTemplateService {

    private final CertificateTemplateRepository templateRepository;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listAll() {
        return templateRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TemplateResponse save(SaveTemplateRequest request) {
        // If this is the new default, clear existing default
        if (request.isDefault()) {
            templateRepository.findByDefaultTemplateTrue().ifPresent(t -> {
                t.setDefaultTemplate(false);
                templateRepository.save(t);
            });
        }

        // Upsert: find existing org template or create new
        CertificateTemplate template = request.organizationId() != null
                ? templateRepository.findByOrganizationId(request.organizationId())
                        .orElseGet(CertificateTemplate::new)
                : (request.isDefault()
                        ? templateRepository.findByDefaultTemplateTrue().orElseGet(CertificateTemplate::new)
                        : new CertificateTemplate());

        template.setOrganizationId(request.organizationId());
        template.setBackgroundImageUrl(request.backgroundImageUrl());
        template.setSignatoryName(request.signatoryName());
        template.setSignatoryTitle(request.signatoryTitle());
        template.setSignatureImageUrl(request.signatureImageUrl());
        template.setPrimaryColor(request.primaryColor());
        template.setDefaultTemplate(request.isDefault());

        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public void delete(UUID templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw new EntityNotFoundException("Template not found: " + templateId);
        }
        templateRepository.deleteById(templateId);
    }

    private TemplateResponse toResponse(CertificateTemplate t) {
        return new TemplateResponse(
                t.getId(), t.getOrganizationId(),
                t.getBackgroundImageUrl(), t.getSignatoryName(), t.getSignatoryTitle(),
                t.getSignatureImageUrl(), t.getPrimaryColor(), t.isDefaultTemplate(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}