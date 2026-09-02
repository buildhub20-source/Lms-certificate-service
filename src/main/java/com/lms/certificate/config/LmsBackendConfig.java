package com.lms.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "cert.lms-backend")
public class LmsBackendConfig {
    private String baseUrl = "http://localhost:8080";
    private int timeoutMs = 5000;
    public String getBaseUrl()          { return baseUrl; }
    public int getTimeoutMs()           { return timeoutMs; }
    public void setBaseUrl(String v)    { this.baseUrl = v; }
    public void setTimeoutMs(int v)     { this.timeoutMs = v; }
}