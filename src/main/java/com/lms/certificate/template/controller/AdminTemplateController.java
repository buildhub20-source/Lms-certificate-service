package com.lms.certificate.template.controller;

import com.lms.certificate.template.dto.request.SaveTemplateRequest;
import com.lms.certificate.template.dto.response.TemplateResponse;
import com.lms.certificate.template.service.CertificateTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only template management API.
 */
@RestController
@RequestMapping("/api/v1/admin/certificate-templates")
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

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID templateId) {
        templateService.delete(templateId);
    }
}