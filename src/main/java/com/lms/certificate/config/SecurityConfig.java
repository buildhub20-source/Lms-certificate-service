package com.lms.certificate.config;

import com.lms.certificate.security.JwtAuthFilter;
import com.lms.certificate.security.JwtValidator;
import com.lms.certificate.security.ServiceKeyAuthFilter;
import com.lms.certificate.tenancy.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/public/**",
            "/api/v1/.well-known/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**",
            "/actuator/info"
    };

    private final JwtValidator jwtValidator;
    private final InternalConfig internalConfig;
    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(JwtValidator jwtValidator, InternalConfig internalConfig,
                          TenantContextFilter tenantContextFilter) {
        this.jwtValidator = jwtValidator;
        this.internalConfig = internalConfig;
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers("/api/v1/internal/**").permitAll()
                // Instructors may manage templates used by courses they own; service-level
                // ownership checks are the next extension when instructor course ownership is exposed.
                .requestMatchers("/api/v1/admin/certificate-templates/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "INSTRUCTOR")
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(serviceKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(tenantContextFilter, ServiceKeyAuthFilter.class)
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public ServiceKeyAuthFilter serviceKeyAuthFilter() {
        return new ServiceKeyAuthFilter(internalConfig);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtValidator);
    }
}
