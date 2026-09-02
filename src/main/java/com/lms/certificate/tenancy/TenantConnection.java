package com.lms.certificate.tenancy;

import java.util.UUID;

/** Short-lived connection metadata for one tenant request. */
public record TenantConnection(UUID tenantId, String slug, String jdbcUrl, String username, String password) {}
