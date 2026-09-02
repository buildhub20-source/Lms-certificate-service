package com.lms.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings shared with the LMS control plane for database-per-tenant routing. */
@ConfigurationProperties(prefix = "cert.tenancy")
public class TenantRuntimeConfig {
    private boolean enabled;
    private boolean tenantResolutionRequired;
    private String tenantHeader = "X-Tenant-Slug";
    private String credentialEncryptionKey;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isTenantResolutionRequired() { return tenantResolutionRequired; }
    public void setTenantResolutionRequired(boolean value) { this.tenantResolutionRequired = value; }
    public String getTenantHeader() { return tenantHeader; }
    public void setTenantHeader(String value) { this.tenantHeader = value; }
    public String getCredentialEncryptionKey() { return credentialEncryptionKey; }
    public void setCredentialEncryptionKey(String value) { this.credentialEncryptionKey = value; }
}
