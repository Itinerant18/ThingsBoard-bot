package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;

class NodeNameResolverTest {

    private HierarchyNodeRepository hierarchyNodeRepository;
    private NodeNameResolver resolver;

    @BeforeEach
    void setUp() {
        hierarchyNodeRepository = mock(HierarchyNodeRepository.class);
        resolver = new NodeNameResolver(hierarchyNodeRepository);
    }

    @Test
    void shouldReturnEmptyForNullOrBlankQuestion() {
        assertTrue(resolver.resolveNodeInQuestion("BOI", null).isEmpty());
        assertTrue(resolver.resolveNodeInQuestion("BOI", "   ").isEmpty());
    }

    @Test
    void shouldResolveNodeByDisplayName() {
        HierarchyNode zone1 = new HierarchyNode();
        zone1.setNodeId("zone-kolkata");
        zone1.setDisplayName("Kolkata Zone");
        zone1.setIsLeaf(false);

        when(hierarchyNodeRepository.findByCustomerId("BOI")).thenReturn(List.of(zone1));

        Optional<HierarchyNode> result = resolver.resolveNodeInQuestion("BOI", "How many branches offline in Kolkata Zone?");
        assertTrue(result.isPresent());
        assertEquals("zone-kolkata", result.get().getNodeId());
    }

    @Test
    void shouldResolveNodeByNodeId() {
        HierarchyNode zone1 = new HierarchyNode();
        zone1.setNodeId("ZO_KOLKATA");
        zone1.setDisplayName("Kolkata Zone");
        zone1.setIsLeaf(false);

        when(hierarchyNodeRepository.findByCustomerId("BOI")).thenReturn(List.of(zone1));

        Optional<HierarchyNode> result = resolver.resolveNodeInQuestion("BOI", "How many online in ZO_KOLKATA?");
        assertTrue(result.isPresent());
        assertEquals("ZO_KOLKATA", result.get().getNodeId());
    }

    @Test
    void shouldIgnoreLeafNodes() {
        HierarchyNode leaf = new HierarchyNode();
        leaf.setNodeId("leaf-branch");
        leaf.setDisplayName("Branch Kolkata");
        leaf.setIsLeaf(true);

        when(hierarchyNodeRepository.findByCustomerId("BOI")).thenReturn(List.of(leaf));

        Optional<HierarchyNode> result = resolver.resolveNodeInQuestion("BOI", "Status of Branch Kolkata");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSortByDisplayNameLengthDescending() {
        HierarchyNode shorterNode = new HierarchyNode();
        shorterNode.setNodeId("zone-kolkata");
        shorterNode.setDisplayName("Kolkata");
        shorterNode.setIsLeaf(false);

        HierarchyNode longerNode = new HierarchyNode();
        longerNode.setNodeId("zone-kolkata-east");
        longerNode.setDisplayName("Kolkata East");
        longerNode.setIsLeaf(false);

        when(hierarchyNodeRepository.findByCustomerId("BOI")).thenReturn(List.of(shorterNode, longerNode));

        Optional<HierarchyNode> result = resolver.resolveNodeInQuestion("BOI", "Count offline in Kolkata East Zone");
        assertTrue(result.isPresent());
        assertEquals("zone-kolkata-east", result.get().getNodeId());
    }
}
