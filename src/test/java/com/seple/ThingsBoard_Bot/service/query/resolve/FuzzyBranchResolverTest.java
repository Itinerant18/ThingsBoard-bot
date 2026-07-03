package com.seple.ThingsBoard_Bot.service.query.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchResolution.Status;

class FuzzyBranchResolverTest {

    private final BranchAliasIndex aliasIndex = new BranchAliasIndex();
    private final ManualAliasTable aliasTable = new ManualAliasTable(aliasIndex);
    private BranchDictionary dictionary;
    private FuzzyBranchResolver resolver;

    private static HierarchyNode leaf(String nodeId, String displayName) {
        return HierarchyNode.builder()
                .nodeId(nodeId).customerId("boi").nodeType("BRANCH").nodeLevel(4)
                .displayName(displayName).isLeaf(true).build();
    }

    @BeforeEach
    void setUp() {
        dictionary = BranchDictionary.fromHierarchyNodes(List.of(
                leaf("BOI-MALDATOWN", "MALDA TOWN"),
                leaf("BOI-TARAKESHWAR", "TARAKESHWAR"),
                leaf("BOI-BHUBANESHWAR", "BHUBANESHWAR"),
                leaf("BOI-CHANDANNAGAR", "CHANDANNAGAR")), aliasIndex)
                .withManualAliases(aliasTable.mappings());
        resolver = new FuzzyBranchResolver(aliasIndex, aliasTable, 0.90, 0.75, 0.55);
    }

    @Test
    void exactNameResolvesSilently() {
        BranchResolution result = resolver.resolve("MALDA TOWN", dictionary);
        assertEquals(Status.RESOLVED, result.status());
        assertEquals("BOI-MALDATOWN", result.match().technicalId());
        assertEquals(1.0, result.candidates().get(0).score());
    }

    @Test
    void compactSpellingResolvesSilently() {
        BranchResolution result = resolver.resolve("MALDATOWN", dictionary);
        assertEquals(Status.RESOLVED, result.status());
        assertEquals("BOI-MALDATOWN", result.match().technicalId());
    }

    @Test
    void manualShorthandResolvesSilently() {
        BranchResolution result = resolver.resolve("MT", dictionary);
        assertEquals(Status.RESOLVED, result.status());
        assertEquals("BOI-MALDATOWN", result.match().technicalId());

        BranchResolution bbsr = resolver.resolve("bbsr", dictionary);
        assertEquals(Status.RESOLVED, bbsr.status());
        assertEquals("BOI-BHUBANESHWAR", bbsr.match().technicalId());
    }

    @Test
    void closeTypoResolvesSilently() {
        // Dropped letter: TARAKESWAR vs TARAKESHWAR - Jaro-Winkler > 0.90.
        BranchResolution result = resolver.resolve("TARAKESWAR", dictionary);
        assertEquals(Status.RESOLVED, result.status());
        assertEquals("BOI-TARAKESHWAR", result.match().technicalId());
    }

    @Test
    void midBandAsksForConfirmation() {
        // Force the band by raising the silent threshold above any fuzzy (non-exact) score.
        FuzzyBranchResolver strict = new FuzzyBranchResolver(aliasIndex, aliasTable, 0.999, 0.75, 0.55);
        BranchResolution result = strict.resolve("TARAKESWAR", dictionary);
        assertEquals(Status.NEEDS_CONFIRMATION, result.status());
        assertEquals("BOI-TARAKESHWAR", result.match().technicalId());
        assertTrue(result.candidates().size() >= 1);
    }

    @Test
    void lowBandReturnsTopThreeSuggestions() {
        // Force the band by raising both thresholds above any fuzzy score.
        FuzzyBranchResolver strict = new FuzzyBranchResolver(aliasIndex, aliasTable, 0.999, 0.999, 0.55);
        BranchResolution result = strict.resolve("TARAKESWAR", dictionary);
        assertEquals(Status.SUGGESTIONS, result.status());
        assertNull(result.match());
        assertTrue(result.candidates().size() <= 3);
        assertEquals("BOI-TARAKESHWAR", result.candidates().get(0).entry().technicalId());
    }

    @Test
    void garbageReturnsNoMatch() {
        BranchResolution result = resolver.resolve("QXZPLW999", dictionary);
        assertEquals(Status.NO_MATCH, result.status());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void blankInputAndEmptyDictionaryReturnNoMatch() {
        assertEquals(Status.NO_MATCH, resolver.resolve("  ", dictionary).status());
        assertEquals(Status.NO_MATCH, resolver.resolve(null, dictionary).status());
        assertEquals(Status.NO_MATCH, resolver.resolve("MALDA TOWN", BranchDictionary.empty()).status());
        assertEquals(Status.NO_MATCH, resolver.resolve("MALDA TOWN", null).status());
    }

    @Test
    void candidatesAreRankedBestFirst() {
        FuzzyBranchResolver strict = new FuzzyBranchResolver(aliasIndex, aliasTable, 0.999, 0.999, 0.10);
        BranchResolution result = strict.resolve("CHANDANAGAR", dictionary);
        assertNotNull(result.candidates());
        for (int i = 1; i < result.candidates().size(); i++) {
            assertTrue(result.candidates().get(i - 1).score() >= result.candidates().get(i).score());
        }
        assertEquals("BOI-CHANDANNAGAR", result.candidates().get(0).entry().technicalId());
    }
}
