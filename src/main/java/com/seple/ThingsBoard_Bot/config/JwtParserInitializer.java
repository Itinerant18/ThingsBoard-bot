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
        JwtParserUtil.configure(securityProperties.getJwtSigningKey());
    }
}
