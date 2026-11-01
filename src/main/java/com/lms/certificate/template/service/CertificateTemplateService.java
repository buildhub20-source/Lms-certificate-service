package com.lms.certificate.template.service;

import com.lms.certificate.certificate.entity.Certificate;
import com.lms.certificate.certificate.entity.CertificateStatus;
import com.lms.certificate.certificate.service.CertificateService;
import com.lms.certificate.pdf.CertificatePdfGenerator;
import com.lms.certificate.template.dto.request.SaveTemplateRequest;
import com.lms.certificate.template.dto.response.TemplateResponse;
import com.lms.certificate.template.entity.CertificateTemplate;
import com.lms.certificate.template.entity.CertificateTemplateSource;
import com.lms.certificate.template.repository.CertificateTemplateRepository;
import com.lms.certificate.storage.R2StorageService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CertificateTemplateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateTemplateService.class);

    private final CertificateTemplateRepository templateRepository;
    private final R2StorageService storageService;
    private final CertificatePdfGenerator pdfGenerator;
    private final CertificateService certificateService;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository,
                                      R2StorageService storageService,
                                      CertificatePdfGenerator pdfGenerator,
                                      CertificateService certificateService) {
        this.templateRepository = templateRepository;
        this.storageService = storageService;
        this.pdfGenerator = pdfGenerator;
        this.certificateService = certificateService;
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
        template.setCourseId(request.courseId());

        return toResponse(templateRepository.save(template));
    }

    /**
     * Renders the template against invented learner details so the
     * organisation can see what a real certificate will look like before
     * committing to it. Drafts can be previewed; that is the point of them.
     */
    @Transactional(readOnly = true)
    public byte[] preview(UUID templateId) {
        CertificateTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

        Certificate sample = Certificate.builder()
                .serialNumber("PREVIEW-0000-0000")
                .studentId(UUID.randomUUID())
                .courseId(template.getCourseId() != null ? template.getCourseId() : UUID.randomUUID())
                .organizationId(template.getOrganizationId())
                .issuedAt(Instant.now())
                .status(CertificateStatus.ACTIVE)
                .build();

        return pdfGenerator.generate(sample, template);
    }

    /**
     * Makes the template the one used when certificates are issued for its
     * course, and completes anything that was waiting on it.
     *
     * <p>Any template previously published for the same course is retired in
     * the same transaction, so the unique index never has to reject this.
     */
    @Transactional
    public TemplateResponse publish(UUID templateId) {
        CertificateTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

        if (template.getSourceType() == CertificateTemplateSource.INSTITUTION_PDF
                && (template.getPdfTemplateUrl() == null || template.getPdfTemplateUrl().isBlank())) {
            throw new IllegalStateException("Upload the certificate PDF before publishing this template");
        }

        if (template.getCourseId() != null) {
            templateRepository
                    .findByOrganizationIdAndCourseIdAndPublishedTrue(
                            template.getOrganizationId(), template.getCourseId())
                    .stream()
                    .filter(other -> !other.getId().equals(template.getId()))
                    .forEach(other -> {
                        other.setPublished(false);
                        templateRepository.save(other);
                    });
        }

        template.setPublished(true);
        template.setPublishedAt(Instant.now());
        TemplateResponse response = toResponse(templateRepository.save(template));

        // Flush before the certificates are rendered: completePendingForCourse
        // resolves the template again, and must see this one as published.
        templateRepository.flush();
        if (template.getCourseId() != null) {
            int completed = certificateService.completePendingForCourse(template.getCourseId());
            if (completed > 0) {
                log.info("Publishing template {} released {} held certificate(s)", templateId, completed);
            }
        }
        return response;
    }

    /** Takes a template out of use without deleting it. */
    @Transactional
    public TemplateResponse unpublish(UUID templateId) {
        CertificateTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));
        template.setPublished(false);
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
                request.organizationId(), request.courseId(), request.name(),
                CertificateTemplateSource.INSTITUTION_PDF,
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
                t.getId(), t.getOrganizationId(), t.getCourseId(),
                t.getName(), t.getSourceType(), t.getPdfTemplateUrl(), t.getFieldLayout(),
                t.getBackgroundImageUrl(), t.getSignatoryName(), t.getSignatoryTitle(),
                t.getSignatureImageUrl(), t.getPrimaryColor(), t.isDefaultTemplate(),
                t.isPublished(), t.getPublishedAt(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
