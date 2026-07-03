package com.seple.ThingsBoard_Bot.service.query.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResponseFormat;

class ExtractionResultParserTest {

    private final ExtractionResultParser parser = new ExtractionResultParser();

    @Test
    void parsesValidSingleIntent() {
        ExtractionResult result = parser.parse("""
                {"intents":[{"intent":"BATTERY_VOLTAGE","entities":["Bally Bazar"],"format":"TABLE","confidence":0.96}]}
                """);

        assertEquals(1, result.intents().size());
        ExtractedIntent intent = result.intents().get(0);
        assertEquals(QueryIntent.BATTERY_VOLTAGE, intent.intent());
        assertEquals(List.of("Bally Bazar"), intent.entities());
        assertEquals(ResponseFormat.TABLE, intent.format());
        assertEquals(0.96, intent.confidence(), 1e-9);
    }

    @Test
    void parsesMultiIntent() {
        ExtractionResult result = parser.parse("""
                {"intents":[
                  {"intent":"BATTERY_VOLTAGE","entities":["Malda Town"],"confidence":0.9},
                  {"intent":"CCTV_STATUS","entities":["Malda Town"],"confidence":0.88}
                ]}
                """);

        assertEquals(2, result.intents().size());
        assertEquals(QueryIntent.BATTERY_VOLTAGE, result.intents().get(0).intent());
        assertEquals(QueryIntent.CCTV_STATUS, result.intents().get(1).intent());
        assertNull(result.intents().get(0).format());
    }

    @Test
    void malformedJsonReturnsEmpty() {
        assertTrue(parser.parse("not json at all").isEmpty());
        assertTrue(parser.parse("{\"intents\": \"oops\"}").isEmpty());
        assertTrue(parser.parse("{}").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("  ").isEmpty());
        assertTrue(parser.parse("{\"intents\":[]}").isEmpty());
    }

    @Test
    void unknownIntentCoercedToOutOfScopeAtZeroConfidence() {
        ExtractionResult result = parser.parse("""
                {"intents":[{"intent":"MAKE_COFFEE","entities":["Kitchen"],"confidence":0.99}]}
                """);

        ExtractedIntent intent = result.intents().get(0);
        assertEquals(QueryIntent.OUT_OF_SCOPE, intent.intent());
        assertEquals(0.0, intent.confidence());
        assertEquals(List.of("Kitchen"), intent.entities());
    }

    @Test
    void unknownFormatCoercesEntryToOutOfScope() {
        ExtractionResult result = parser.parse("""
                {"intents":[{"intent":"BATTERY_VOLTAGE","entities":["Malda Town"],"format":"HOLOGRAM","confidence":0.9}]}
                """);

        assertEquals(QueryIntent.OUT_OF_SCOPE, result.intents().get(0).intent());
        assertEquals(0.0, result.intents().get(0).confidence());
    }

    @Test
    void missingOrNullFormatMeansHandlerDefault() {
        ExtractionResult result = parser.parse("""
                {"intents":[
                  {"intent":"CCTV_STATUS","entities":["Liluah"],"confidence":0.9},
                  {"intent":"POWER_STATUS","entities":["Liluah"],"format":null,"confidence":0.9}
                ]}
                """);

        assertEquals(QueryIntent.CCTV_STATUS, result.intents().get(0).intent());
        assertNull(result.intents().get(0).format());
        assertEquals(QueryIntent.POWER_STATUS, result.intents().get(1).intent());
        assertNull(result.intents().get(1).format());
    }

    @Test
    void lowercaseEnumNamesAccepted() {
        ExtractionResult result = parser.parse("""
                {"intents":[{"intent":"battery_voltage","entities":[],"format":"table","confidence":0.8}]}
                """);

        assertEquals(QueryIntent.BATTERY_VOLTAGE, result.intents().get(0).intent());
        assertEquals(ResponseFormat.TABLE, result.intents().get(0).format());
    }

    @Test
    void entitiesCleanedOfBlankAndNonString() {
        ExtractionResult result = parser.parse("""
                {"intents":[{"intent":"CCTV_STATUS","entities":["Liluah", "", "  ", 42, null, "Dobson"],"confidence":0.9}]}
                """);

        assertEquals(List.of("Liluah", "Dobson"), result.intents().get(0).entities());
    }

    @Test
    void missingEntitiesTreatedAsEmpty() {
        ExtractionResult result = parser.parse("""
                {"intents":[{"intent":"GLOBAL_OVERVIEW","confidence":0.95}]}
                """);

        assertTrue(result.intents().get(0).entities().isEmpty());
    }

    @Test
    void confidenceClampedAndDefaulted() {
        ExtractionResult result = parser.parse("""
                {"intents":[
                  {"intent":"CCTV_STATUS","entities":[],"confidence":7.5},
                  {"intent":"POWER_STATUS","entities":[],"confidence":-1},
                  {"intent":"ALARM_STATUS","entities":[]}
                ]}
                """);

        assertEquals(1.0, result.intents().get(0).confidence());
        assertEquals(0.0, result.intents().get(1).confidence());
        assertEquals(0.0, result.intents().get(2).confidence());
    }

    @Test
    void moreThanThreeIntentsKeepsTopThreeByConfidence() {
        ExtractionResult result = parser.parse("""
                {"intents":[
                  {"intent":"CCTV_STATUS","entities":[],"confidence":0.7},
                  {"intent":"POWER_STATUS","entities":[],"confidence":0.95},
                  {"intent":"ALARM_STATUS","entities":[],"confidence":0.6},
                  {"intent":"BATTERY_VOLTAGE","entities":[],"confidence":0.9},
                  {"intent":"NETWORK_STATUS","entities":[],"confidence":0.85}
                ]}
                """);

        assertEquals(3, result.intents().size());
        assertEquals(QueryIntent.POWER_STATUS, result.intents().get(0).intent());
        assertEquals(QueryIntent.BATTERY_VOLTAGE, result.intents().get(1).intent());
        assertEquals(QueryIntent.NETWORK_STATUS, result.intents().get(2).intent());
    }

    @Test
    void refusalAndOutOfScopePassThrough() {
        ExtractionResult refusal = parser.parse("""
                {"intents":[{"intent":"REFUSAL","entities":[],"confidence":1.0}]}
                """);
        assertEquals(QueryIntent.REFUSAL, refusal.intents().get(0).intent());

        ExtractionResult oos = parser.parse("""
                {"intents":[{"intent":"OUT_OF_SCOPE","entities":[],"confidence":0.97}]}
                """);
        assertEquals(QueryIntent.OUT_OF_SCOPE, oos.intents().get(0).intent());
    }

    @Test
    void parserNeverThrows() {
        assertTrue(parser.parse("{\"intents\":[{\"intent\":123,\"entities\":\"x\",\"confidence\":\"high\"}]}")
                .intents().get(0).intent() == QueryIntent.OUT_OF_SCOPE);
        assertTrue(parser.parse("[1,2,3]").isEmpty());
        assertTrue(parser.parse("\"just a string\"").isEmpty());
    }
}
