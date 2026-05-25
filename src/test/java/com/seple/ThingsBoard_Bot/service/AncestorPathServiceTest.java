package com.seple.ThingsBoard_Bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;

class AncestorPathServiceTest {

    private HierarchyNodeRepository hierarchyNodeRepository;
    private BranchAncestorPathRepository branchAncestorPathRepository;
    private AncestorPathCache ancestorPathCache;
    private AncestorPathService ancestorPathService;

    @BeforeEach
    void setUp() {
        hierarchyNodeRepository = mock(HierarchyNodeRepository.class);
        branchAncestorPathRepository = mock(BranchAncestorPathRepository.class);
        ancestorPathCache = mock(AncestorPathCache.class);
        ancestorPathService = new AncestorPathService(
                hierarchyNodeRepository,
                branchAncestorPathRepository,
                ancestorPathCache
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldComputeAncestorPathsCorrectly() {
        String customerId = "BOI";

        HierarchyNode ho = new HierarchyNode();
        ho.setNodeId("HO_BOI");
        ho.setCustomerId(customerId);
        ho.setParentId(null);
        ho.setIsLeaf(false);

        HierarchyNode zo = new HierarchyNode();
        zo.setNodeId("ZO_KOLKATA");
        zo.setCustomerId(customerId);
        zo.setParentId("HO_BOI");
        zo.setIsLeaf(false);

        HierarchyNode branch = new HierarchyNode();
        branch.setNodeId("BOI_BR042");
        branch.setCustomerId(customerId);
        branch.setParentId("ZO_KOLKATA");
        branch.setIsLeaf(true);

        when(hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true)).thenReturn(List.of(branch));
        when(hierarchyNodeRepository.findById("ZO_KOLKATA")).thenReturn(Optional.of(zo));
        when(hierarchyNodeRepository.findById("HO_BOI")).thenReturn(Optional.of(ho));

        ancestorPathService.computeForCustomer(customerId);

        verify(branchAncestorPathRepository).deleteByCustomerId(customerId);
        ArgumentCaptor<List<BranchAncestorPath>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(branchAncestorPathRepository).saveAll(listCaptor.capture());

        List<BranchAncestorPath> saved = listCaptor.getValue();
        assertEquals(1, saved.size());
        BranchAncestorPath path = saved.get(0);
        assertEquals("BOI_BR042", path.getBranchNodeId());
        assertEquals(2, path.getPathDepth());
        assertEquals("HO_BOI", path.getAncestorPath()[0]);
        assertEquals("ZO_KOLKATA", path.getAncestorPath()[1]);
    }

    @Test
    void shouldReloadPathsIntoCache() {
        String customerId = "BOI";
        BranchAncestorPath path = BranchAncestorPath.builder()
                .branchNodeId("BOI_BR042")
                .customerId(customerId)
                .ancestorPath(new String[]{"HO_BOI", "ZO_KOLKATA"})
                .pathDepth(2)
                .build();

        when(branchAncestorPathRepository.findByCustomerId(customerId)).thenReturn(List.of(path));

        ancestorPathService.reloadForCustomer(customerId);

        verify(ancestorPathCache).invalidateAll(customerId);
        verify(ancestorPathCache).cacheAncestors(customerId, "BOI_BR042", List.of("HO_BOI", "ZO_KOLKATA"));
    }
}
