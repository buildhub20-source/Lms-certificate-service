package com.lms.certificate.tenancy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** Reads active tenant connection metadata from the LMS platform control plane. */
@Repository
public class TenantRegistry {
    private final JdbcTemplate jdbcTemplate;

    public TenantRegistry(@Qualifier("controlPlaneDataSource") DataSource controlPlaneDataSource) {
        this.jdbcTemplate = new JdbcTemplate(controlPlaneDataSource);
    }

    public Optional<EncryptedTenantConnection> findActiveBySlug(String slug) {
        return jdbcTemplate.query(
                "select id, slug, jdbc_url, database_username, encrypted_database_password "
                        + "from platform.tenants where slug = ? and status = 'ACTIVE'",
                resultSet -> resultSet.next()
                        ? Optional.of(new EncryptedTenantConnection(
                                UUID.fromString(resultSet.getString("id")),
                                resultSet.getString("slug"),
                                resultSet.getString("jdbc_url"),
                                resultSet.getString("database_username"),
                                resultSet.getString("encrypted_database_password")))
                        : Optional.empty(),
                slug);
    }

    public record EncryptedTenantConnection(UUID tenantId, String slug, String jdbcUrl,
                                             String username, String encryptedPassword) {}
}
