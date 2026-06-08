package com.seple.ThingsBoard_Bot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards the admin surface ({@code /api/v1/admin/**}) with a shared token.
 *
 * <p>Non-breaking by design: if no {@code iotchatbot.security.admin-token} is configured,
 * requests pass through (current behaviour) but a loud warning is logged so the gap is
 * visible. Once a token is set, every admin request must present a matching
 * {@code X-Admin-Token} header or it is rejected with 401.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/v1/admin/";
    private static final String HEADER = "X-Admin-Token";

    private final SecurityProperties securityProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains(ADMIN_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!securityProperties.isAdminGuardEnabled()) {
            log.warn("[SECURITY] Admin endpoint {} reached with NO admin-token configured — endpoint is UNPROTECTED. "
                    + "Set iotchatbot.security.admin-token to lock it down.", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(securityProperties.getAdminToken(), provided)) {
            log.warn("[SECURITY] Rejected unauthorized admin request to {} from {}",
                    request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
