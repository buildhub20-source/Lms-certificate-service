package com.lms.certificate.security;

import com.lms.certificate.config.InternalConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards {@code /api/v1/internal/**} with a shared {@code X-Service-Key} header.
 */
public class ServiceKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyAuthFilter.class);

    public static final String SERVICE_KEY_HEADER   = "X-Service-Key";
    public static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";

    private final InternalConfig internalConfig;

    public ServiceKeyAuthFilter(InternalConfig internalConfig) {
        this.internalConfig = internalConfig;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(SERVICE_KEY_HEADER);
        if (!internalConfig.getServiceKey().equals(provided)) {
            log.warn("Rejected internal call from {} — bad service key", request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}