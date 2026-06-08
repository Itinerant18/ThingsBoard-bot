package com.seple.ThingsBoard_Bot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtParserUtilTest {

    // 64 bytes -> valid for HS512; deterministic so the test is reproducible.
    private static final byte[] KEY_BYTES = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final SecretKey KEY = Keys.hmacShaKeyFor(KEY_BYTES);
    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(KEY_BYTES);

    @AfterEach
    void reset() {
        JwtParserUtil.configure(""); // back to unverified default so other tests are unaffected
    }

    private String signedToken(SecretKey key) {
        return Jwts.builder()
                .claim("customerId", "BOI")
                .claim("iss", "seple.iot-private.cloud")
                .claim("firstName", "Jane")
                .signWith(key)
                .compact();
    }

    @Test
    void verifiesAndExtractsClaimsWhenKeyMatches() {
        JwtParserUtil.configure(BASE64_KEY);
        String token = signedToken(KEY);
        assertEquals("BOI", JwtParserUtil.extractCustomerId(token));
        assertEquals("Jane", JwtParserUtil.extractClaim(token, "firstName"));
        assertEquals("https://seple.iot-private.cloud", JwtParserUtil.extractHost(token));
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        JwtParserUtil.configure(BASE64_KEY);
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = signedToken(otherKey);
        assertNull(JwtParserUtil.extractCustomerId(forged), "forged token must yield no claims");
    }

    @Test
    void unverifiedModeStillDecodesClaims() {
        JwtParserUtil.configure(""); // verification disabled
        String token = signedToken(KEY);
        assertEquals("BOI", JwtParserUtil.extractCustomerId(token),
                "unverified decode should still read advisory claims");
    }

    @Test
    void invalidKeyFallsBackToUnverified() {
        JwtParserUtil.configure("!!!not-base64!!!"); // unusable key -> disabled, app keeps running
        String token = signedToken(KEY);
        assertEquals("BOI", JwtParserUtil.extractCustomerId(token));
    }

    @Test
    void isExpiredDetectsPastExpiry() {
        JwtParserUtil.configure("");
        String expired = Jwts.builder()
                .claim("customerId", "BOI")
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(KEY)
                .compact();
        assertTrue(JwtParserUtil.isExpired(expired));
    }

    @Test
    void isExpiredFalseForNoExpClaim() {
        JwtParserUtil.configure("");
        assertFalse(JwtParserUtil.isExpired(signedToken(KEY)));
    }
}
