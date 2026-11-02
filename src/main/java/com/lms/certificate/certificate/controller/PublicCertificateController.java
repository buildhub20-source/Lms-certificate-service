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

    /** Verify by the token in a certificate's QR code. */
    @GetMapping("/verify/token/{token}")
    public PublicCertificateResponse verifyToken(@PathVariable String token) {
        return certificateService.verifyByToken(requireToken(token));
    }

    /** Token verification for a tenant; the tenant filter selects the database from the path. */
    @GetMapping("/tenants/{tenantSlug}/verify/token/{token}")
    public PublicCertificateResponse verifyTokenForTenant(@PathVariable String tenantSlug,
                                                          @PathVariable String token) {
        return certificateService.verifyByToken(requireToken(token));
    }

    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{32,64}");

    /** A malformed token cannot match anything, so it is simply not found. */
    private static String requireToken(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new jakarta.persistence.EntityNotFoundException("Certificate not found");
        }
        return token;
    }
}
