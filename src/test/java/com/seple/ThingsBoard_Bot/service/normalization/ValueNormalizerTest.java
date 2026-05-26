package com.seple.ThingsBoard_Bot.service.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;

class ValueNormalizerTest {

    private ValueNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ValueNormalizer();
    }

    @Test
    void shouldResolveCorruptedNullsToUnknownOrNull() {
        // States
        assertEquals(NormalizedState.UNKNOWN, normalizer.toState("null1"));
        assertEquals(NormalizedState.UNKNOWN, normalizer.toState("null11"));
        assertEquals(NormalizedState.UNKNOWN, normalizer.toState("not_found"));
        assertEquals(NormalizedState.UNKNOWN, normalizer.toState("not found"));
        assertEquals(NormalizedState.UNKNOWN, normalizer.toState(null));

        // Booleans
        assertNull(normalizer.toBoolean("null1"));
        assertNull(normalizer.toBoolean("null11"));
        assertNull(normalizer.toBoolean("not_found"));
        assertNull(normalizer.toBoolean(null));

        // Doubles
        assertNull(normalizer.toDouble("null1"));
        assertNull(normalizer.toDouble("null11"));
        assertNull(normalizer.toDouble("not_found"));
        assertNull(normalizer.toDouble("N/A"));
        assertNull(normalizer.toDouble(null));

        // Integers
        assertEquals(99, normalizer.toInt("null1", 99));
        assertEquals(99, normalizer.toInt("null11", 99));
        assertEquals(99, normalizer.toInt("not_found", 99));
        assertEquals(99, normalizer.toInt("N/A", 99));
        assertEquals(99, normalizer.toInt(null, 99));
    }

    @Test
    void shouldNormalizeValidStates() {
        assertEquals(NormalizedState.ONLINE, normalizer.toState("online"));
        assertEquals(NormalizedState.ONLINE, normalizer.toState("ON"));
        assertEquals(NormalizedState.ONLINE, normalizer.toState("1"));

        assertEquals(NormalizedState.OFFLINE, normalizer.toState("offline"));
        assertEquals(NormalizedState.OFFLINE, normalizer.toState("off"));
        assertEquals(NormalizedState.OFFLINE, normalizer.toState("0"));

        assertEquals(NormalizedState.FAULT, normalizer.toState("fault"));
        assertEquals(NormalizedState.FAULT, normalizer.toState("alarm"));

        assertEquals(NormalizedState.NOT_INSTALLED, normalizer.toState("N/A"));
        assertEquals(NormalizedState.NOT_INSTALLED, normalizer.toState("-"));
    }

    @Test
    void shouldNormalizeValidBooleans() {
        assertTrue(normalizer.toBoolean("true"));
        assertTrue(normalizer.toBoolean("1"));
        assertTrue(normalizer.toBoolean("yes"));
        assertTrue(normalizer.toBoolean("online"));

        assertFalse(normalizer.toBoolean("false"));
        assertFalse(normalizer.toBoolean("0"));
        assertFalse(normalizer.toBoolean("off"));
        assertFalse(normalizer.toBoolean("inactive"));
    }

    @Test
    void shouldParseValidNumericTypes() {
        assertEquals(57.452, normalizer.toDouble("57.452"));
        assertEquals(14.0, normalizer.toDouble(14));
        
        assertEquals(1800, normalizer.toInt("1800", 0));
        assertEquals(0, normalizer.toInt("0", 99));
    }
}
