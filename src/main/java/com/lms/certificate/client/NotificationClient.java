package com.lms.certificate.client;

import com.lms.certificate.config.InternalConfig;
import com.lms.certificate.config.NotificationServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Puts a notification in a learner's bell through LMS-Notification-Service.
 *
 * <p>Fire-and-forget: the certificate is already issued and the learner can
 * reach it from their certificates page regardless, so a notification service
 * that is down must not turn a successful issue into a failure.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final NotificationServiceConfig config;
    private final InternalConfig internalConfig;
    private final RestTemplate restTemplate;

    public NotificationClient(NotificationServiceConfig config,
                              InternalConfig internalConfig,
                              RestTemplate notificationRestTemplate) {
        this.config = config;
        this.internalConfig = internalConfig;
        this.restTemplate = notificationRestTemplate;
    }

    @Async
    public void notifyCertificateReady(UUID tenantId, UUID studentId, String courseTitle, UUID certificateId) {
        if (!config.isEnabled()) {
            log.debug("Notification service disabled — skipping bell notification for certificate {}", certificateId);
            return;
        }
        if (tenantId == null || studentId == null) {
            log.warn("Cannot notify about certificate {}: tenant or student unknown", certificateId);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Key", internalConfig.getServiceKey());
            headers.set("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<>();
            body.put("tenantId", tenantId.toString());
            body.put("userId", studentId.toString());
            body.put("type", "CERTIFICATE");
            body.put("title", "Your certificate is ready");
            body.put("message", "You have earned a certificate for " + courseTitle + ".");
            body.put("linkUrl", "/learn/certificates");

            restTemplate.exchange(config.getBaseUrl() + "/api/v1/internal/notifications",
                    HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);

            log.debug("Notified student {} about certificate {}", studentId, certificateId);
        } catch (Exception ex) {
            log.warn("Could not notify student {} about certificate {}: {}",
                    studentId, certificateId, ex.getMessage());
        }
    }
}
