package com.seple.ThingsBoard_Bot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DotenvEnvironmentPostProcessorTest {

    @Test
    void parsesKeyValuePairs() {
        Map<String, Object> result = DotenvEnvironmentPostProcessor.parseLines(List.of(
                "TB_URL=https://example.cloud",
                "TB_USER=info@seple.in"));
        assertEquals("https://example.cloud", result.get("TB_URL"));
        assertEquals("info@seple.in", result.get("TB_USER"));
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        Map<String, Object> result = DotenvEnvironmentPostProcessor.parseLines(List.of(
                "# a comment",
                "",
                "   ",
                "KEY=value"));
        assertEquals(1, result.size());
        assertEquals("value", result.get("KEY"));
    }

    @Test
    void keepsEqualsAndAmpersandInsideValue() {
        Map<String, Object> result = DotenvEnvironmentPostProcessor.parseLines(List.of(
                "URL=jdbc:postgresql://h:5432/db?sslmode=require&reWriteBatchedInserts=true"));
        assertEquals("jdbc:postgresql://h:5432/db?sslmode=require&reWriteBatchedInserts=true", result.get("URL"));
    }

    @Test
    void stripsSurroundingQuotes() {
        Map<String, Object> result = DotenvEnvironmentPostProcessor.parseLines(List.of(
                "DQ=\"quoted value\"",
                "SQ='single quoted'"));
        assertEquals("quoted value", result.get("DQ"));
        assertEquals("single quoted", result.get("SQ"));
    }

    @Test
    void skipsMalformedLines() {
        Map<String, Object> result = DotenvEnvironmentPostProcessor.parseLines(List.of(
                "no_equals_here",
                "=leading_equals"));
        assertFalse(result.containsKey("no_equals_here"));
        assertNull(result.get(""));
        assertEquals(0, result.size());
    }
}
