package com.lms.certificate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
/**
 * Entry point for the LMS Certificate microservice.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Issue e-certificates when a student completes a course</li>
 *   <li>Generate PDF certificates and store them in Cloudflare R2</li>
 *   <li>Email students the certificate PDF on issuance</li>
 *   <li>Allow admins to manage org-specific certificate templates</li>
 *   <li>Expose a public endpoint for certificate verification</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableAsync
public class CertificateServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CertificateServiceApplication.class, args);
    }
}