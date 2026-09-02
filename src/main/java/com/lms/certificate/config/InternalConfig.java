package com.lms.certificate.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties(prefix = "cert.internal")
public class InternalConfig {
    @NotBlank private String serviceKey;
    public String getServiceKey()               { return serviceKey; }
    public void setServiceKey(String serviceKey){ this.serviceKey = serviceKey; }
}