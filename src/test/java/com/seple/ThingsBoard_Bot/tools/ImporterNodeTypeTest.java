package com.seple.ThingsBoard_Bot.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The importer must label each middle hierarchy node by the level code that prefixes its name, so
 * every bank's levels (NBG/FGMO/LHO/ZO/RO/RBO/CO) read correctly — not just BOI's NBG/ZO.
 */
class ImporterNodeTypeTest {

    @Test
    void classifiesEachBankLevelCodeFromSegmentName() {
        assertEquals("NBG", ThingsBoardTimescaleImporter.classifyMiddleNodeType("NBG JH"));
        assertEquals("FGMO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("FGMO East"));
        assertEquals("LHO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("LHO Mumbai"));
        assertEquals("ZO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("ZO Pune"));
        assertEquals("RO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("RO KOLKATA - I"));
        assertEquals("RBO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("RBO 3"));
        assertEquals("CO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("CO South"));
    }

    @Test
    void handlesPunctuationAndSynonyms() {
        assertEquals("ZO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("ZO(Kolkata)"));
        assertEquals("NBG", ThingsBoardTimescaleImporter.classifyMiddleNodeType("NBG-3"));
        assertEquals("HO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("HO (Canara Bank)"));
        assertEquals("ZO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("ZONE Kolkata"));
        assertEquals("RO", ThingsBoardTimescaleImporter.classifyMiddleNodeType("REGION North"));
    }

    @Test
    void returnsNullForUnrecognizedSegments() {
        // Segment does not self-declare a level code -> caller falls back to the legacy heuristic.
        assertNull(ThingsBoardTimescaleImporter.classifyMiddleNodeType("STATE BANK OF INDIA"));
        assertNull(ThingsBoardTimescaleImporter.classifyMiddleNodeType("Kolkata"));
        assertNull(ThingsBoardTimescaleImporter.classifyMiddleNodeType(""));
        assertNull(ThingsBoardTimescaleImporter.classifyMiddleNodeType(null));
    }
}
