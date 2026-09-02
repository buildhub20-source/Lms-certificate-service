package com.lms.certificate.config;

import com.lms.certificate.tenancy.TenantRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/** Keeps the legacy certificate database as the control plane and routes tenant requests dynamically. */
@Configuration
public class TenantDataSourceConfig {
    @Bean(name = "controlPlaneDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource controlPlaneDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("controlPlaneDataSource") DataSource controlPlaneDataSource) {
        return new TenantRoutingDataSource(controlPlaneDataSource);
    }
}
