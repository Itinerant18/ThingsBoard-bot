package com.seple.ThingsBoard_Bot.config;

import com.seple.ThingsBoard_Bot.util.JwtParserUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pushes the configured signing key into the static {@link JwtParserUtil} at startup, so the
 * parser can verify JWT signatures when {@code IOTCHATBOT_JWT_SIGNING_KEY} is set while keeping
 * its existing static call sites unchanged.
 */
@Component
@RequiredArgsConstructor
public class JwtParserInitializer {

    private final SecurityProperties securityProperties;

    @PostConstruct
    void init() {
        if (securityProperties.isRequireJwtVerification() && !securityProperties.isJwtVerificationEnabled()) {
            throw new IllegalStateException(
                    "IOTCHATBOT_SECURITY_REQUIRE_JWT_VERIFICATION is set but no IOTCHATBOT_JWT_SIGNING_KEY is "
                    + "configured. Refusing to start with unverified JWTs in an environment that requires "
                    + "signature verification. Set the signing key, or disable the requirement for local dev.");
        }
        JwtParserUtil.configure(securityProperties.getJwtSigningKey());
    }
}
