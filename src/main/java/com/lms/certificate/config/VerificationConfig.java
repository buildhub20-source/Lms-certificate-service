package com.lms.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the QR code on a certificate sends whoever scans it. */
@ConfigurationProperties(prefix = "cert.verification")
public class VerificationConfig {
    /** Origin of the LMS frontend that hosts the public verify page. */
    private String baseUrl = "http://localhost:3000";
    public String getBaseUrl()          { return baseUrl; }
    public void setBaseUrl(String v)    { this.baseUrl = v; }

    /**
     * The link encoded in a certificate's QR code. The tenant travels in the
     * link because whoever scans it is not signed in to that tenant.
     */
    public String verifyUrl(String token, String tenantSlug) {
        String url = baseUrl.replaceAll("/+$", "") + "/verify/t/" + token;
        return tenantSlug == null || tenantSlug.isBlank() ? url : url + "?org=" + tenantSlug;
    }
}
