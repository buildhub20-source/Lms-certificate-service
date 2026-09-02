package com.lms.certificate.tenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Routes certificate JPA work to the selected tenant's dedicated database. */
public class TenantRoutingDataSource implements DataSource, AutoCloseable {
    private final DataSource controlPlane;
    private final Map<UUID, HikariDataSource> tenantPools = new ConcurrentHashMap<>();

    public TenantRoutingDataSource(DataSource controlPlane) { this.controlPlane = controlPlane; }

    @Override public Connection getConnection() throws SQLException { return selected().getConnection(); }
    @Override public Connection getConnection(String username, String password) throws SQLException {
        return selected().getConnection(username, password);
    }

    private DataSource selected() {
        return TenantContext.current().<DataSource>map(connection -> tenantPools.computeIfAbsent(
                connection.tenantId(), ignored -> createPool(connection))).orElse(controlPlane);
    }

    private HikariDataSource createPool(TenantConnection connection) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("certificate-tenant-" + connection.slug());
        config.setJdbcUrl(connection.jdbcUrl());
        config.setUsername(connection.username());
        config.setPassword(connection.password());
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(900_000);
        return new HikariDataSource(config);
    }

    @Override public PrintWriter getLogWriter() throws SQLException { return controlPlane.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { controlPlane.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { controlPlane.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return controlPlane.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return controlPlane.getParentLogger(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return selected().unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return selected().isWrapperFor(iface); }
    @Override public void close() { tenantPools.values().forEach(HikariDataSource::close); tenantPools.clear(); }
}
