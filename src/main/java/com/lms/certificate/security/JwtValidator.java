package com.lms.certificate.security;

import com.lms.certificate.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Validates JWT tokens issued by LMS-BackEnd (HS256 symmetric).
 * The shared secret must be identical to LMS-BackEnd's JWT_SECRET.
 */
@Component
public class JwtValidator {

    private final JwtConfig jwtConfig;

    public JwtValidator(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(jwtConfig.getIssuer())
                .clockSkewSeconds(jwtConfig.getClockSkew().getSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        Object userId = claims.get("uid");
        if (userId == null) throw new IllegalArgumentException("JWT does not contain a user id");
        return UUID.fromString(String.valueOf(userId));
    }

    public UUID extractTenantId(Claims claims) {
        Object tenantId = claims.get("tid");
        return tenantId == null ? null : UUID.fromString(String.valueOf(tenantId));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public boolean isValid(String token) {
        try {
            validateAndParse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
