package com.seple.ThingsBoard_Bot.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Sets {@code Content-Security-Policy: frame-ancestors ...} so the chat widget can only be embedded
 * by the allowed ThingsBoard hosts (audit #17). Without it, modern browsers fall back to defaults
 * and the iframe may be blocked, or the page may be embeddable anywhere.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String frameAncestors = securityProperties.getFrameAncestors();
        if (frameAncestors != null && !frameAncestors.isBlank()) {
            response.setHeader("Content-Security-Policy", "frame-ancestors " + frameAncestors.trim());
        }
        chain.doFilter(request, response);
    }
}
