package com.seple.ThingsBoard_Bot.service.query.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;

class BranchDictionaryServiceTest {

    private HierarchyNodeRepository repository;
    private BranchDictionaryService service;
    private final BranchAliasIndex aliasIndex = new BranchAliasIndex();

    @BeforeEach
    void setUp() {
        repository = mock(HierarchyNodeRepository.class);
        service = new BranchDictionaryService(repository, aliasIndex, new ManualAliasTable(aliasIndex));
    }

    private HierarchyNode leaf(String nodeId, String customerId, String displayName) {
        return HierarchyNode.builder()
                .nodeId(nodeId)
                .customerId(customerId)
                .nodeType("BRANCH")
                .nodeLevel(4)
                .displayName(displayName)
                .isLeaf(true)
                .build();
    }

    @Test
    void loadsAndCachesPerCustomer() {
        when(repository.findByCustomerIdAndIsLeaf("boi", true))
                .thenReturn(List.of(leaf("BOI-MALDATOWN", "boi", "BRANCH MALDA TOWN")));

        BranchDictionary first = service.getDictionary("boi");
        BranchDictionary second = service.getDictionary("boi");

        assertEquals(1, first.entries().size());
        assertTrue(first == second, "second call must come from cache");
        verify(repository, times(1)).findByCustomerIdAndIsLeaf("boi", true);
    }

    @Test
    void customerScopingNeverMixesBranches() {
        when(repository.findByCustomerIdAndIsLeaf("boi", true))
                .thenReturn(List.of(leaf("BOI-MALDATOWN", "boi", "BRANCH MALDA TOWN")));
        when(repository.findByCustomerIdAndIsLeaf("canara", true))
                .thenReturn(List.of(leaf("CANARA-BURNPUR", "canara", "BRANCH BURNPUR")));

        BranchDictionary boi = service.getDictionary("boi");
        BranchDictionary canara = service.getDictionary("canara");

        assertNotNull(boi.findExact(aliasIndex.normalize("MALDA TOWN")));
        assertNull(boi.findExact(aliasIndex.normalize("BURNPUR")));
        assertNotNull(canara.findExact(aliasIndex.normalize("CANARA-BURNPUR")));
        assertNull(canara.findExact(aliasIndex.normalize("MALDA TOWN")));
    }

    @Test
    void allScopeSpansEveryCustomer() {
        when(repository.findAll()).thenReturn(List.of(
                leaf("BOI-MALDATOWN", "boi", "BRANCH MALDA TOWN"),
                leaf("CANARA-BURNPUR", "canara", "BRANCH BURNPUR")));

        BranchDictionary all = service.getDictionary("ALL");

        assertEquals(2, all.entries().size());
        assertNotNull(all.findExact(aliasIndex.normalize("MALDA TOWN")));
        assertNotNull(all.findExact(aliasIndex.normalize("BURNPUR")));
    }

    @Test
    void blankCustomerReturnsEmptyDictionary() {
        assertTrue(service.getDictionary(null).isEmpty());
        assertTrue(service.getDictionary("").isEmpty());
    }

    @Test
    void refreshReloadsKnownCustomers() {
        when(repository.findByCustomerIdAndIsLeaf(anyString(), anyBoolean()))
                .thenReturn(List.of(leaf("BOI-MALDATOWN", "boi", "BRANCH MALDA TOWN")));

        service.getDictionary("boi");
        service.refresh();

        verify(repository, times(2)).findByCustomerIdAndIsLeaf("boi", true);
    }
}
