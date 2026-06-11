package com.seple.ThingsBoard_Bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Security knobs for the ingestion/admin surface.
 *
 * <p>All values default to empty/wildcard so existing local development keeps working
 * unchanged. When a value is configured, the corresponding guard is enforced. This lets
 * production lock things down via env vars without code changes:
 * <ul>
 *   <li>{@code IOTCHATBOT_SECURITY_WEBHOOK_HMAC_SECRET}</li>
 *   <li>{@code IOTCHATBOT_SECURITY_ADMIN_TOKEN}</li>
 *   <li>{@code IOTCHATBOT_SECURITY_ALLOWED_ORIGINS}</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "iotchatbot.security")
public class SecurityProperties {

    /** Shared secret for verifying the {@code X-HMAC-SHA256} header on inbound webhooks. Blank = verification disabled. */
    private String webhookHmacSecret = "";

    /** Shared token required on the {@code X-Admin-Token} header for /api/v1/admin/** routes. Blank = guard disabled. */
    private String adminToken = "";

    /** Comma-separated CORS allow-list. Use {@code *} to allow any origin (not recommended in production). */
    private String allowedOrigins = "http://localhost:5173,http://localhost:8080";

    /**
     * Base64-encoded HMAC key ThingsBoard signs its JWTs with. When set, {@link com.seple.ThingsBoard_Bot.util.JwtParserUtil}
     * cryptographically verifies token signatures; blank = unverified decode (advisory claims only, dev default).
     */
    private String jwtSigningKey = "";

    /**
     * When {@code true}, a request whose ThingsBoard customer has no internal mapping is rejected
     * (fail closed) instead of silently falling back to the "BOI" tenant. Default {@code false}
     * preserves local-dev behaviour; production MUST set this to {@code true} to enforce tenant
     * isolation. Bound from {@code IOTCHATBOT_SECURITY_STRICT_CUSTOMER_MAPPING}.
     */
    private boolean strictCustomerMapping = false;

    /**
     * When {@code true}, the application refuses to start unless a JWT signing key is configured,
     * making signature verification mandatory. Default {@code false} preserves local-dev's
     * unverified-decode mode; production MUST set this to {@code true}. Bound from
     * {@code IOTCHATBOT_SECURITY_REQUIRE_JWT_VERIFICATION}.
     */
    private boolean requireJwtVerification = false;

    /**
     * Comma-separated allowlist of ThingsBoard hosts the backend may issue outbound requests to.
     * A client-supplied {@code X-TB-Host} (or JWT {@code iss}) outside this list is ignored in
     * favour of the configured default URL. Bound from {@code IOTCHATBOT_SECURITY_ALLOWED_THINGSBOARD_HOSTS}.
     */
    private String allowedThingsboardHosts = "app.swatch360.seple.in,www.dexterhms.com,dexterhms.com";

    public boolean isWebhookHmacEnabled() {
        return webhookHmacSecret != null && !webhookHmacSecret.isBlank();
    }

    public boolean isStrictCustomerMappingEnabled() {
        return strictCustomerMapping;
    }

    public boolean isRequireJwtVerification() {
        return requireJwtVerification;
    }

    public java.util.List<String> allowedThingsboardHostList() {
        return java.util.Arrays.stream(allowedThingsboardHosts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean isAdminGuardEnabled() {
        return adminToken != null && !adminToken.isBlank();
    }

    public boolean isJwtVerificationEnabled() {
        return jwtSigningKey != null && !jwtSigningKey.isBlank();
    }
}
