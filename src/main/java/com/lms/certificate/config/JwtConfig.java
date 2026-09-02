package com.lms.certificate.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
@Validated
@ConfigurationProperties(prefix = "cert.jwt")
public class JwtConfig {
    @NotBlank private String secret;
    private String issuer = "lms-backend";
    private Duration clockSkew = Duration.ofSeconds(30);
    public String getSecret()       { return secret; }
    public String getIssuer()       { return issuer; }
    public Duration getClockSkew()  { return clockSkew; }
    public void setSecret(String secret)          { this.secret = secret; }
    public void setIssuer(String issuer)          { this.issuer = issuer; }
    public void setClockSkew(Duration clockSkew)  { this.clockSkew = clockSkew; }
}