package com.lms.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Outbound settings for LMS-Notification-Service. */
@ConfigurationProperties(prefix = "cert.notification-service")
public class NotificationServiceConfig {

    private String baseUrl = "http://localhost:3002";

    /** When false the certificate still issues; only the bell entry is skipped. */
    private boolean enabled = false;

    private int timeoutMs = 2000;

    public String getBaseUrl()            { return baseUrl; }
    public void setBaseUrl(String v)      { this.baseUrl = v; }
    public boolean isEnabled()            { return enabled; }
    public void setEnabled(boolean v)     { this.enabled = v; }
    public int getTimeoutMs()             { return timeoutMs; }
    public void setTimeoutMs(int v)       { this.timeoutMs = v; }
}
