package com.seple.ThingsBoard_Bot.util;

import java.net.URI;
import java.util.List;

/**
 * Validates a client-supplied ThingsBoard host against an allowlist before the backend will
 * issue outbound requests to it (audit finding #5 — SSRF via X-TB-Host).
 *
 * <p>Matching is on the parsed host, by exact equality or proper subdomain suffix — NOT a naive
 * substring check. A substring check would accept {@code app.swatch360.seple.in.evil.com}.
 */
public final class ThingsBoardHostValidator {

    private ThingsBoardHostValidator() {
    }

    /** Extracts the lowercased host from a full URL or a bare host:port string; null if unparseable. */
    public static String extractHost(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String v = value.trim();
            if (!v.contains("://")) {
                v = "https://" + v;
            }
            String host = URI.create(v).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Returns true only if {@code candidate}'s host exactly matches an allowed host or is a
     * subdomain of one (host equals {@code allowed} or ends with {@code "." + allowed}).
     */
    public static boolean isAllowed(String candidate, List<String> allowedHosts) {
        String host = extractHost(candidate);
        if (host == null || allowedHosts == null) {
            return false;
        }
        for (String allowed : allowedHosts) {
            if (allowed == null || allowed.isBlank()) {
                continue;
            }
            String a = allowed.trim().toLowerCase();
            if (host.equals(a) || host.endsWith("." + a)) {
                return true;
            }
        }
        return false;
    }
}
