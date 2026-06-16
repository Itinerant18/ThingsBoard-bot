package com.seple.ThingsBoard_Bot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Extracts claims out of a ThingsBoard JWT for routing purposes (customerId, issuer host,
 * display name).
 *
 * <p><strong>Trust model — read before changing.</strong> Two modes, selected by whether a
 * signing key is configured via {@link #configure(String)} (bound from
 * {@code IOTCHATBOT_JWT_SIGNING_KEY}):
 * <ul>
 *   <li><strong>Verified</strong> (key set): the token signature and expiry are validated with
 *       jjwt before any claim is read. A token that fails verification yields no claims.</li>
 *   <li><strong>Unverified</strong> (key blank, the dev default): claims are base64-decoded
 *       without signature checking and are advisory only. ThingsBoard remains the authoritative
 *       validator — every privileged action forwards {@code X-TB-Token} to ThingsBoard, which
 *       checks signature/expiry/audience. A {@code [SECURITY]} warning is logged in this mode.</li>
 * </ul>
 * Either way the claims read here are used to pick a customer/zone, never as the sole basis of an
 * authorization decision.
 */
@Slf4j
public final class JwtParserUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** Set by {@link #configure(String)} at startup. Non-null = signature verification enforced. */
    private static volatile SecretKey verifyingKey;

    private JwtParserUtil() {
    }

    /**
     * Configures the HMAC verification key from a base64-encoded secret. Call once at startup.
     * Blank key disables verification (unverified decode, advisory claims). An unusable key is
     * logged and treated as disabled so the application still starts.
     */
    public static void configure(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            verifyingKey = null;
            log.warn("[SECURITY] IOTCHATBOT_JWT_SIGNING_KEY not set — JWT claims are read UNVERIFIED. "
                    + "Acceptable for local dev; set the key in production to enforce signature verification.");
            return;
        }
        try {
            verifyingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Key.trim()));
            log.info("[SECURITY] JWT signature verification ENABLED.");
        } catch (RuntimeException e) {
            verifyingKey = null;
            log.error("[SECURITY] Invalid IOTCHATBOT_JWT_SIGNING_KEY ({}). Falling back to UNVERIFIED decode.",
                    e.getMessage());
        }
    }

    /** Parse and return the JWT payload as a JsonNode, or {@code null} if malformed/unverifiable. */
    private static JsonNode payload(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String t = token.startsWith("Bearer ") ? token.substring(7) : token;
        SecretKey key = verifyingKey;
        return key != null ? verifiedPayload(t, key) : unverifiedPayload(t);
    }

    /** Signature- and expiry-verified payload via jjwt. Returns {@code null} if verification fails. */
    private static JsonNode verifiedPayload(String token, SecretKey key) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return mapper.valueToTree(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            return null;
        }
    }

    /** Unverified base64 decode of the JWT payload segment. Advisory claims only. */
    private static JsonNode unverifiedPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return mapper.readTree(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to parse JWT payload: {}", e.getMessage());
            return null;
        }
    }

    public static String extractCustomerId(String token) {
        JsonNode json = payload(token);
        if (json == null) {
            return null;
        }
        JsonNode customerIdNode = json.get("customerId");
        if (customerIdNode != null) {
            if (customerIdNode.isContainerNode() && customerIdNode.has("id")) {
                return customerIdNode.get("id").asText(null);
            }
            return customerIdNode.asText(null);
        }
        return null;
    }

    public static String extractClaim(String token, String claimName) {
        JsonNode json = payload(token);
        if (json == null) {
            return null;
        }
        JsonNode claimNode = json.get(claimName);
        return claimNode != null ? claimNode.asText(null) : null;
    }

    public static boolean hasScope(String token, String scope) {
        if (token == null || token.isBlank()) {
            return false;
        }
        JsonNode json = payload(token);
        if (json == null) {
            return false;
        }
        JsonNode scopesNode = json.get("scopes");
        if (scopesNode != null && scopesNode.isArray()) {
            for (JsonNode s : scopesNode) {
                if (scope.equals(s.asText())) {
                    return true;
                }
            }
        }
        return false;
    }


    public static String extractHost(String token) {
        String iss = extractClaim(token, "iss");
        if (iss != null && !iss.isBlank()) {
            if (!iss.startsWith("http://") && !iss.startsWith("https://")) {
                iss = "https://" + iss;
            }
            return iss;
        }
        return null;
    }

    /**
     * Returns {@code true} if the token carries an {@code exp} claim already in the past.
     * Advisory only — uses an unverified decode so a clearly-dead token can be short-circuited
     * regardless of verification mode. Unparseable tokens or those without {@code exp} are
     * treated as not-expired (fail open to ThingsBoard).
     */
    public static boolean isExpired(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.startsWith("Bearer ") ? token.substring(7) : token;
        JsonNode json = unverifiedPayload(t);
        if (json == null) {
            return false;
        }
        JsonNode exp = json.get("exp");
        if (exp == null || !exp.canConvertToLong()) {
            return false;
        }
        return exp.asLong() * 1000L < System.currentTimeMillis();
    }
}
