package com.seple.ThingsBoard_Bot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fail-fast guard for production security toggles (audit finding #6). When an operator opts into
 * requiring the webhook HMAC or admin token, the app refuses to start unless the corresponding
 * secret is actually configured — so a misconfiguration is caught at boot instead of silently
 * leaving the surface unprotected.
 *
 * <p>All flags default false, so local dev and the test suite are unaffected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator {

    private final SecurityProperties securityProperties;

    @PostConstruct
    void validate() {
        if (securityProperties.isRequireWebhookHmac() && !securityProperties.isWebhookHmacEnabled()) {
            throw new IllegalStateException(
                    "IOTCHATBOT_SECURITY_REQUIRE_WEBHOOK_HMAC is set but no webhook HMAC secret is "
                    + "configured. Refusing to start with an unauthenticated webhook surface.");
        }
        if (securityProperties.isRequireAdminToken() && !securityProperties.isAdminGuardEnabled()) {
            throw new IllegalStateException(
                    "IOTCHATBOT_SECURITY_REQUIRE_ADMIN_TOKEN is set but no admin token is configured. "
                    + "Refusing to start with unprotected /api/v1/admin/** endpoints.");
        }
    }
}
