package com.lms.certificate.certificate.controller;

import com.lms.certificate.certificate.dto.response.PublicCertificateResponse;
import com.lms.certificate.certificate.service.CertificateService;
import org.springframework.web.bind.annotation.*;

/**
 * Unauthenticated public endpoint — anyone can verify a certificate by serial number.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicCertificateController {

    private final CertificateService certificateService;

    public PublicCertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    /**
     * Verify a certificate by serial number (e.g. CERT-2026-A1B2C3D4).
     */
    @GetMapping("/verify/{serialNumber}")
    public PublicCertificateResponse verify(@PathVariable String serialNumber) {
        return certificateService.verify(serialNumber);
    }

    /** Tenant-scoped verification URL used when database-per-tenant mode is enabled. */
    @GetMapping("/tenants/{tenantSlug}/verify/{serialNumber}")
    public PublicCertificateResponse verifyForTenant(@PathVariable String tenantSlug,
                                                     @PathVariable String serialNumber) {
        return certificateService.verify(serialNumber);
    }
}
