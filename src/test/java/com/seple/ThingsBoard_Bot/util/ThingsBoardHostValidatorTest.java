package com.seple.ThingsBoard_Bot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ThingsBoardHostValidatorTest {

    private static final List<String> ALLOWED = List.of("app.swatch360.seple.in", "dexterhms.com");

    @Test
    void allowsExactHost() {
        assertTrue(ThingsBoardHostValidator.isAllowed("https://app.swatch360.seple.in", ALLOWED));
    }

    @Test
    void allowsSubdomain() {
        assertTrue(ThingsBoardHostValidator.isAllowed("https://www.dexterhms.com/api", ALLOWED));
    }

    @Test
    void rejectsSubstringBypass() {
        // The old contains()-based check would have accepted these.
        assertFalse(ThingsBoardHostValidator.isAllowed("https://app.swatch360.seple.in.evil.com", ALLOWED));
        assertFalse(ThingsBoardHostValidator.isAllowed("https://dexterhms.com.attacker.net", ALLOWED));
    }

    @Test
    void rejectsUnknownAndMalformed() {
        assertFalse(ThingsBoardHostValidator.isAllowed("https://169.254.169.254/latest/meta-data", ALLOWED));
        assertFalse(ThingsBoardHostValidator.isAllowed(null, ALLOWED));
        assertFalse(ThingsBoardHostValidator.isAllowed("not a url", ALLOWED));
    }

    @Test
    void extractsHostFromBareValue() {
        assertEquals("app.swatch360.seple.in", ThingsBoardHostValidator.extractHost("app.swatch360.seple.in"));
    }
}
