package com.lms.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "cert.storage.r2")
public class R2Config {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String publicUrl;
    public String getEndpoint()          { return endpoint; }
    public String getAccessKey()         { return accessKey; }
    public String getSecretKey()         { return secretKey; }
    public String getBucket()            { return bucket; }
    public String getPublicUrl()         { return publicUrl; }
    public void setEndpoint(String v)    { this.endpoint = v; }
    public void setAccessKey(String v)   { this.accessKey = v; }
    public void setSecretKey(String v)   { this.secretKey = v; }
    public void setBucket(String v)      { this.bucket = v; }
    public void setPublicUrl(String v)   { this.publicUrl = v; }
}