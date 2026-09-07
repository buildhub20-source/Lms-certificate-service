package com.lms.certificate.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
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

    @Bean(destroyMethod = "close")
    public CloseableHttpClient lmsHttpClient(LmsBackendConfig config) {
        Timeout timeout = Timeout.ofMilliseconds(config.getTimeoutMs());
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout)
                .setResponseTimeout(timeout)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMinutes(5))
                .build();
    }

    @Bean(name = "lmsRestTemplate")
    public RestTemplate lmsRestTemplate(CloseableHttpClient lmsHttpClient) {
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(lmsHttpClient);
        return new RestTemplate(factory);
    }
}
