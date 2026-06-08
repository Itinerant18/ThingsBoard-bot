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

    public boolean isWebhookHmacEnabled() {
        return webhookHmacSecret != null && !webhookHmacSecret.isBlank();
    }

    public boolean isAdminGuardEnabled() {
        return adminToken != null && !adminToken.isBlank();
    }

    public boolean isJwtVerificationEnabled() {
        return jwtSigningKey != null && !jwtSigningKey.isBlank();
    }
}
