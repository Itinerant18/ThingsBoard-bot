package com.seple.ThingsBoard_Bot.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * HMAC-SHA256 helpers backed by the JDK (no external dependency).
 * Used to authenticate inbound webhook payloads against a shared secret.
 */
public final class HmacUtil {

    private static final String ALGO = "HmacSHA256";

    private HmacUtil() {
    }

    /** Compute the lowercase hex HMAC-SHA256 of {@code body} using {@code secret}. */
    public static String hexHmacSha256(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    /**
     * Constant-time comparison of an expected HMAC against a client-supplied signature.
     * Tolerates a {@code sha256=} prefix and case differences.
     */
    public static boolean isValid(String secret, String body, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        String provided = providedSignature.trim();
        int eq = provided.indexOf('=');
        if (eq >= 0 && provided.regionMatches(true, 0, "sha256", 0, 6)) {
            provided = provided.substring(eq + 1);
        }
        String expected = hexHmacSha256(secret, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
