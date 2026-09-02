package com.lms.certificate.client;

import com.lms.certificate.config.InternalConfig;
import com.lms.certificate.config.LmsBackendConfig;
import com.lms.certificate.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Client for fetching data from LMS-BackEnd's internal API.
 * All requests carry X-Service-Key for authentication.
 */
@Component
public class LmsInternalClient {

    private static final Logger log = LoggerFactory.getLogger(LmsInternalClient.class);
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String TENANT_HEADER = "X-Tenant-Slug";

    private final RestTemplate lmsRestTemplate;
    private final LmsBackendConfig lmsConfig;
    private final InternalConfig internalConfig;

    public LmsInternalClient(@Qualifier("lmsRestTemplate") RestTemplate lmsRestTemplate,
                             LmsBackendConfig lmsConfig,
                             InternalConfig internalConfig) {
        this.lmsRestTemplate = lmsRestTemplate;
        this.lmsConfig = lmsConfig;
        this.internalConfig = internalConfig;
    }

    // ─── DTOs ───────────────────────────────────────────────────────────────

    public record UserDto(UUID id, String name, String email) {}
    public record CourseDto(UUID id, String title, String description,
                            Integer durationMinutes, String thumbnailKey) {}
    public record EnrollmentDto(UUID id, UUID studentId, UUID courseId,
                                String status, Instant enrolledAt, Instant completedAt) {}
    public record OrgSettingsDto(String name, String domain, String logoUrl,
                                 String primaryColor, String supportEmail) {}

    // ─── Methods ─────────────────────────────────────────────────────────────

    public UserDto getUser(UUID userId) {
        return get("/api/v1/internal/users/" + userId, UserDto.class, currentTenantSlug());
    }

    public UserDto getUser(UUID userId, String tenantSlug) {
        return get("/api/v1/internal/users/" + userId, UserDto.class, tenantSlug);
    }

    public CourseDto getCourse(UUID courseId) {
        return get("/api/v1/internal/courses/" + courseId, CourseDto.class, currentTenantSlug());
    }

    public CourseDto getCourse(UUID courseId, String tenantSlug) {
        return get("/api/v1/internal/courses/" + courseId, CourseDto.class, tenantSlug);
    }

    public EnrollmentDto getEnrollment(UUID studentId, UUID courseId) {
        return get("/api/v1/internal/enrollments?studentId=" + studentId
                + "&courseId=" + courseId, EnrollmentDto.class, currentTenantSlug());
    }

    public OrgSettingsDto getOrgSettings() {
        return get("/api/v1/internal/org-settings", OrgSettingsDto.class, currentTenantSlug());
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private <T> T get(String path, Class<T> responseType, String tenantSlug) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(SERVICE_KEY_HEADER, internalConfig.getServiceKey());
        if (tenantSlug != null && !tenantSlug.isBlank()) headers.set(TENANT_HEADER, tenantSlug);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = lmsConfig.getBaseUrl() + path;
        return lmsRestTemplate.exchange(url, HttpMethod.GET, entity, responseType).getBody();
    }

    private String currentTenantSlug() {
        return TenantContext.current().map(connection -> connection.slug()).orElse(null);
    }
}
