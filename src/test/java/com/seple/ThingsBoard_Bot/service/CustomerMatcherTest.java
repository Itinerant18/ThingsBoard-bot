package com.seple.ThingsBoard_Bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CustomerMatcherTest {

    private static final List<String> DB_IDS = List.of("BOI", "SBI", "SEPL", "PNB");

    @Test
    void exactUniqueMatchWins() {
        assertEquals("SEPL", CustomerMatcher.match("SEPL", DB_IDS, Map.of()));
        assertEquals("PNB", CustomerMatcher.match("pnb", DB_IDS, Map.of()));
    }

    @Test
    void explicitOverrideMapsTitleToPrefix() {
        Map<String, String> explicit = Map.of(CustomerMatcher.normalize("Bank of India"), "BOI");
        assertEquals("BOI", CustomerMatcher.match("Bank of India", DB_IDS, explicit));
    }

    @Test
    void noMatchReturnsNullInsteadOfGuessing() {
        // "Bank of India" normalizes to BANKOFINDIA which is not equal to any prefix -> no guess.
        assertNull(CustomerMatcher.match("Bank of India", DB_IDS, Map.of()));
        // Unknown customer entirely.
        assertNull(CustomerMatcher.match("Random Corp", DB_IDS, Map.of()));
    }

    @Test
    void ambiguousMultipleMatchesReturnsNull() {
        // Two db ids normalize to the same value -> ambiguous -> skip.
        assertNull(CustomerMatcher.match("CB", List.of("CB", "C.B", "OTHER"), Map.of()));
    }

    @Test
    void explicitTargetMissingFromDbReturnsNull() {
        Map<String, String> explicit = Map.of(CustomerMatcher.normalize("Foo Bank"), "FOO");
        assertNull(CustomerMatcher.match("Foo Bank", DB_IDS, explicit));
    }
}
