package com.seple.ThingsBoard_Bot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(securityProperties.getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        var mapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        // allowedOrigins("*") and allowCredentials(true) are mutually exclusive in Spring.
        // Use allowedOriginPatterns for the wildcard case so credentials still work.
        if (origins.length == 1 && "*".equals(origins[0])) {
            log.warn("[SECURITY] CORS configured to allow ALL origins (*). Restrict "
                    + "iotchatbot.security.allowed-origins before public exposure.");
            mapping.allowedOriginPatterns("*");
        } else {
            mapping.allowedOrigins(origins).allowCredentials(true);
        }
    }
}
