package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class QueryIntentTest {

    @Test
    void phase0IntentsAreLocked() {
        assertDoesNotThrow(() -> QueryIntent.valueOf("HOW_TO"));
        assertDoesNotThrow(() -> QueryIntent.valueOf("NAVIGATION"));
        assertDoesNotThrow(() -> QueryIntent.valueOf("TROUBLESHOOTING"));
        assertDoesNotThrow(() -> QueryIntent.valueOf("CONCEPT_EXPLAIN"));
        assertDoesNotThrow(() -> QueryIntent.valueOf("GLOSSARY"));
        assertDoesNotThrow(() -> QueryIntent.valueOf("OUT_OF_SCOPE"));
        assertDoesNotThrow(() -> QueryIntent.valueOf("REFUSAL"));
    }

    @Test
    void responseFormatIsLocked() {
        assertDoesNotThrow(() -> ResponseFormat.valueOf("TABLE"));
        assertDoesNotThrow(() -> ResponseFormat.valueOf("SUMMARY"));
        assertDoesNotThrow(() -> ResponseFormat.valueOf("BULLETS"));
        assertDoesNotThrow(() -> ResponseFormat.valueOf("DETAILED"));
        assertDoesNotThrow(() -> ResponseFormat.valueOf("COMPARISON"));
        assertDoesNotThrow(() -> ResponseFormat.valueOf("SHORT"));
    }
}
