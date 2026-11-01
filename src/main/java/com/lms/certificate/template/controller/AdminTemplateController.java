package com.lms.certificate.template.controller;

import com.lms.certificate.template.dto.request.SaveTemplateRequest;
import com.lms.certificate.template.dto.response.TemplateResponse;
import com.lms.certificate.template.service.CertificateTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only template management API.
 */
@RestController
@RequestMapping("/api/v1/admin/certificate-templates")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class AdminTemplateController {

    private final CertificateTemplateService templateService;

    public AdminTemplateController(CertificateTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<TemplateResponse> listAll() {
        return templateService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse save(@RequestBody SaveTemplateRequest request) {
        return templateService.save(request);
    }

    /**
     * Stores the uploaded institution PDF as an immutable source template. fieldLayout is JSON,
     * for example {"learnerFullName":{"page":1,"x":421,"y":330,"fontSize":24,"align":"CENTER"}}.
     */
    @PostMapping(value = "/institution-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse uploadInstitutionPdf(@RequestPart("template") SaveTemplateRequest request,
                                                 @RequestPart("file") MultipartFile file) throws java.io.IOException {
        return templateService.uploadInstitutionPdf(request, file.getBytes(), file.getContentType());
    }

    /**
     * Renders the template with invented learner details so it can be checked
     * before publishing. Works on drafts; that is what drafts are for.
     */
    @GetMapping(value = "/{templateId}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable UUID templateId) {
        byte[] pdf = templateService.preview(templateId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate-preview.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Puts the template into use for its course and releases any certificates
     * that were held waiting for it.
     */
    @PostMapping("/{templateId}/publish")
    public TemplateResponse publish(@PathVariable UUID templateId) {
        return templateService.publish(templateId);
    }

    @PostMapping("/{templateId}/unpublish")
    public TemplateResponse unpublish(@PathVariable UUID templateId) {
        return templateService.unpublish(templateId);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID templateId) {
        templateService.delete(templateId);
    }
}
