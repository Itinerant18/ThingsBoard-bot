package com.seple.ThingsBoard_Bot.service.query.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;

class ManualAliasTableTest {

    private final BranchAliasIndex aliasIndex = new BranchAliasIndex();
    private final ManualAliasTable table = new ManualAliasTable(aliasIndex);

    @Test
    void loadsMappingsFromProperties() {
        Map<String, String> mappings = table.mappings();
        assertTrue(mappings.size() >= 3);
        assertEquals("BHUBANESHWAR", mappings.get("BBSR"));
        assertEquals("MALDA TOWN", mappings.get("MT"));
        assertEquals("HEAD OFFICE", mappings.get("HO"));
    }

    @Test
    void canonicalizeResolvesShorthandAndPassesThroughUnknown() {
        assertEquals("MALDA TOWN", table.canonicalize("mt", aliasIndex));
        assertEquals("BHUBANESHWAR", table.canonicalize("BBSR", aliasIndex));
        assertEquals("TARAKESHWAR", table.canonicalize("Tarakeshwar", aliasIndex));
    }

    @Test
    void dictionaryEnrichmentAttachesShorthandToCanonicalEntry() {
        HierarchyNode malda = HierarchyNode.builder()
                .nodeId("BOI-MALDATOWN").customerId("boi").nodeType("BRANCH").nodeLevel(4)
                .displayName("MALDA TOWN").isLeaf(true).build();
        HierarchyNode bbsr = HierarchyNode.builder()
                .nodeId("BOI-BHUBANESHWAR").customerId("boi").nodeType("BRANCH").nodeLevel(4)
                .displayName("BHUBANESHWAR").isLeaf(true).build();

        BranchDictionary dictionary = BranchDictionary.fromHierarchyNodes(List.of(malda, bbsr), aliasIndex)
                .withManualAliases(table.mappings());

        BranchEntry viaMt = dictionary.findExact("MT");
        assertNotNull(viaMt);
        assertEquals("BOI-MALDATOWN", viaMt.technicalId());

        BranchEntry viaBbsr = dictionary.findExact("BBSR");
        assertNotNull(viaBbsr);
        assertEquals("BOI-BHUBANESHWAR", viaBbsr.technicalId());

        // Shorthand with no canonical target in this dictionary must be ignored, not fail.
        assertNull(dictionary.findExact("HO"));
    }

    @Test
    void enrichmentWithEmptyMapReturnsSameDictionary() {
        BranchDictionary dictionary = BranchDictionary.fromHierarchyNodes(List.of(), aliasIndex);
        assertTrue(dictionary.withManualAliases(Map.of()) == dictionary);
        assertTrue(dictionary.withManualAliases(null) == dictionary);
    }
}
