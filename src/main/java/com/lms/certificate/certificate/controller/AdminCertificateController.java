package com.lms.certificate.certificate.controller;

import com.lms.certificate.certificate.dto.request.IssueCertificateRequest;
import com.lms.certificate.certificate.dto.request.RevokeCertificateRequest;
import com.lms.certificate.certificate.dto.response.CertificateResponse;
import com.lms.certificate.certificate.service.CertificateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/certificates")
public class AdminCertificateController {

    private final CertificateService certificateService;

    public AdminCertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping
    public Page<CertificateResponse> listAll(@PageableDefault(size = 20) Pageable pageable) {
        return certificateService.listAll(pageable);
    }

    @GetMapping("/course/{courseId}")
    public Page<CertificateResponse> listByCourse(@PathVariable UUID courseId,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return certificateService.listByCourse(courseId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateResponse issue(@Valid @RequestBody IssueCertificateRequest request,
                                     Authentication auth) {
        UUID adminId = (UUID) auth.getPrincipal();
        return certificateService.issue(request.studentId(), request.courseId(), adminId);
    }

    @PatchMapping("/{certId}/revoke")
    public CertificateResponse revoke(@PathVariable UUID certId,
                                      @Valid @RequestBody RevokeCertificateRequest request,
                                      Authentication auth) {
        UUID adminId = (UUID) auth.getPrincipal();
        return certificateService.revoke(certId, adminId, request);
    }
}