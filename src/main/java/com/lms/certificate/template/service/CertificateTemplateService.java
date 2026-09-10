package com.lms.certificate.template.service;

import com.lms.certificate.template.dto.request.SaveTemplateRequest;
import com.lms.certificate.template.dto.response.TemplateResponse;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.entity.CertificateTemplateSource;
import com.lms.certificate.template.repository.CertificateTemplateRepository;
import com.lms.certificate.storage.R2StorageService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CertificateTemplateService {

    private final CertificateTemplateRepository templateRepository;
    private final R2StorageService storageService;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository,
                                      R2StorageService storageService) {
        this.templateRepository = templateRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listAll() {
        return templateRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public TemplateResponse save(SaveTemplateRequest request) {
        // If this is the new default, clear existing default
        if (request.isDefault()) {
            templateRepository.findByOrganizationIdAndDefaultTemplateTrue(request.organizationId()).ifPresent(t -> {
                t.setDefaultTemplate(false);
                templateRepository.save(t);
            });
        }

        // An organisation may keep several templates. Only the default template is updated in place.
        CertificateTemplate template = request.isDefault()
                ? templateRepository.findByOrganizationIdAndDefaultTemplateTrue(request.organizationId())
                        .orElseGet(CertificateTemplate::new)
                : new CertificateTemplate();

        template.setOrganizationId(request.organizationId());
        template.setName(request.name() == null || request.name().isBlank()
                ? "Certificate template" : request.name().trim());
        template.setSourceType(request.sourceType());
        template.setFieldLayout(request.fieldLayout());
        template.setBackgroundImageUrl(request.backgroundImageUrl());
        template.setSignatoryName(request.signatoryName());
        template.setSignatoryTitle(request.signatoryTitle());
        template.setSignatureImageUrl(request.signatureImageUrl());
        template.setPrimaryColor(request.primaryColor());
        template.setDefaultTemplate(request.isDefault());

        return toResponse(templateRepository.save(template));
    }

    /** Uploads a reusable institution PDF and saves its dynamic-field layout. */
    @Transactional
    public TemplateResponse uploadInstitutionPdf(SaveTemplateRequest request, byte[] pdf, String contentType) {
        if (pdf == null || pdf.length == 0 || pdf.length > 15 * 1024 * 1024) {
            throw new IllegalArgumentException("Template PDF must be between 1 byte and 15 MB");
        }
        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Only PDF certificate templates are supported");
        }
        SaveTemplateRequest normalized = new SaveTemplateRequest(
                request.organizationId(), request.name(), CertificateTemplateSource.INSTITUTION_PDF,
                request.fieldLayout(), request.backgroundImageUrl(), request.signatoryName(),
                request.signatoryTitle(), request.signatureImageUrl(), request.primaryColor(), request.isDefault());
        TemplateResponse saved = save(normalized);
        CertificateTemplate template = templateRepository.findById(saved.id()).orElseThrow();
        template.setPdfTemplateUrl(storageService.uploadInstitutionTemplatePdf(
                pdf, request.organizationId(), template.getId()));
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
                t.getName(), t.getSourceType(), t.getPdfTemplateUrl(), t.getFieldLayout(),
                t.getBackgroundImageUrl(), t.getSignatoryName(), t.getSignatoryTitle(),
                t.getSignatureImageUrl(), t.getPrimaryColor(), t.isDefaultTemplate(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
