package com.lms.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "cert.mail")
public class MailConfig {
    private boolean enabled = false;
    private String from = "no-reply@lms.local";
    private String fromName = "GradusMind Certificates";
    public boolean isEnabled()          { return enabled; }
    public String getFrom()             { return from; }
    public String getFromName()         { return fromName; }
    public void setEnabled(boolean v)   { this.enabled = v; }
    public void setFrom(String v)       { this.from = v; }
    public void setFromName(String v)   { this.fromName = v; }
}