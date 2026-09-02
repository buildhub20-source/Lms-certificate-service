package com.lms.certificate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Infrastructure beans — S3/R2 client and outbound RestTemplate.
 */
@Configuration
public class InfraConfig {

    @Bean
    public S3Client s3Client(R2Config r2Config) {
        return S3Client.builder()
                .endpointOverride(URI.create(r2Config.getEndpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2Config.getAccessKey(), r2Config.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    @Bean(name = "lmsRestTemplate")
    public RestTemplate lmsRestTemplate(LmsBackendConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getTimeoutMs());
        factory.setReadTimeout(config.getTimeoutMs());
        return new RestTemplate(factory);
    }
}