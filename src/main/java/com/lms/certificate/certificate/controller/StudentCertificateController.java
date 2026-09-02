package com.lms.certificate.certificate.controller;

import com.lms.certificate.certificate.dto.response.CertificateResponse;
import com.lms.certificate.certificate.service.CertificateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Student-facing certificate API — scoped to the authenticated student's own records.
 */
@RestController
@RequestMapping("/api/v1/certificates")
public class StudentCertificateController {

    private final CertificateService certificateService;

    public StudentCertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping
    public Page<CertificateResponse> myList(@PageableDefault(size = 20) Pageable pageable,
                                            Authentication auth) {
        UUID studentId = (UUID) auth.getPrincipal();
        return certificateService.listMyCertificates(studentId, pageable);
    }

    @GetMapping("/{certId}")
    public CertificateResponse myGet(@PathVariable UUID certId, Authentication auth) {
        UUID studentId = (UUID) auth.getPrincipal();
        return certificateService.getMyCertificate(studentId, certId);
    }
}