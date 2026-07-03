package com.seple.ThingsBoard_Bot.service.query.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.support.MockSnapshotStore;

class BranchDictionaryTest {

    private final BranchAliasIndex aliasIndex = new BranchAliasIndex();

    @Test
    void buildsFromFixtureSnapshots() throws Exception {
        List<BranchSnapshot> snapshots = MockSnapshotStore.loadDefault();
        BranchDictionary dictionary = BranchDictionary.fromSnapshots(snapshots, "customer-1", aliasIndex);

        assertFalse(dictionary.isEmpty());
        assertEquals(snapshots.size(), dictionary.entries().size());

        BranchEntry tarakeshwar = dictionary.findExact(aliasIndex.normalize("BOI-TARAKESHWAR"));
        assertNotNull(tarakeshwar);
        assertEquals("BOI-TARAKESHWAR", tarakeshwar.technicalId());
        assertEquals("customer-1", tarakeshwar.customerId());
    }

    @Test
    void buildsFromHierarchyNodes() {
        HierarchyNode branch = HierarchyNode.builder()
                .nodeId("BOI-MALDATOWN")
                .customerId("boi")
                .nodeType("BRANCH")
                .nodeLevel(4)
                .displayName("BRANCH MALDA TOWN")
                .isLeaf(true)
                .build();
        BranchDictionary dictionary = BranchDictionary.fromHierarchyNodes(List.of(branch), aliasIndex);

        assertEquals(1, dictionary.entries().size());
        assertNotNull(dictionary.findExact(aliasIndex.normalize("BOI-MALDATOWN")));
        assertNotNull(dictionary.findExact(aliasIndex.normalize("BRANCH MALDA TOWN")));
        assertNotNull(dictionary.findExact(aliasIndex.compact("MALDA TOWN")));
    }

    @Test
    void unknownVariantReturnsNull() {
        BranchDictionary dictionary = BranchDictionary.fromHierarchyNodes(List.of(), aliasIndex);
        assertTrue(dictionary.isEmpty());
        assertNull(dictionary.findExact("NOWHERE"));
        assertNull(dictionary.findExact(null));
    }
}
