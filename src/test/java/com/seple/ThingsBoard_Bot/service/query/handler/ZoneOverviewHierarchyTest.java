package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.GatewayStatus;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * A branch's zone membership must be resolved from the hierarchy ancestor path, not the sparse
 * zo/nbg telemetry keys (present on &lt;10% of devices). A branch with NO zo telemetry but a
 * ZO HOWRAH ancestor must still be found under "ZO HOWRAH".
 */
class ZoneOverviewHierarchyTest {

    private BranchSnapshot branchNoZoneTelemetry(String name, NormalizedState gw) {
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName(name).build())
                .gateway(GatewayStatus.builder().state(gw).build())
                .build(); // rawData defaults to empty -> zoneOf/nbgOf return null
    }

    @Test
    void resolvesBranchesUnderZoneViaHierarchyWhenTelemetryLacksZone() {
        ZoneOverviewHandler handler = new ZoneOverviewHandler(new AnswerSupport());

        HierarchyNodeRepository nodeRepo = mock(HierarchyNodeRepository.class);
        BranchAncestorPathRepository pathRepo = mock(BranchAncestorPathRepository.class);

        HierarchyNode leaf = HierarchyNode.builder()
                .nodeId("ARAMBAGH").displayName("BRANCH ARAMBAGH").isLeaf(true).build();
        HierarchyNode zone = HierarchyNode.builder()
                .nodeId("ZO_HOWRAH").displayName("ZO HOWRAH").nodeType("ZO").isLeaf(false).build();
        BranchAncestorPath path = BranchAncestorPath.builder()
                .branchNodeId("ARAMBAGH").ancestorPath(new String[]{"ZO_HOWRAH"}).build();

        when(nodeRepo.findByCustomerId("BOI")).thenReturn(List.of(leaf, zone));
        when(pathRepo.findByCustomerId("BOI")).thenReturn(List.of(path));
        ReflectionTestUtils.setField(handler, "hierarchyNodeRepository", nodeRepo);
        ReflectionTestUtils.setField(handler, "branchAncestorPathRepository", pathRepo);

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.ZONE_OVERVIEW)
                .zoneFilter("ZO HOWRAH")
                .originalQuestion("Current gateway status of all branches for ZO Howrah")
                .build();

        List<BranchSnapshot> snapshots = List.of(
                branchNoZoneTelemetry("BRANCH ARAMBAGH", NormalizedState.ONLINE),
                branchNoZoneTelemetry("BRANCH FARAWAY", NormalizedState.ONLINE));

        String answer = handler.handle(query, snapshots, "BOI");

        // AnswerSupport.branchName strips the "BRANCH " prefix, so the rendered name is "ARAMBAGH".
        assertFalse(answer.contains("No branches found"), "hierarchy match must find the branch: " + answer);
        assertTrue(answer.contains("ARAMBAGH"), answer);
        assertFalse(answer.contains("FARAWAY"), "only ZO HOWRAH branches: " + answer);
    }

    @Test
    void multiZoneSlashListMatchesBranchesInAnyListedZone() {
        ZoneOverviewHandler handler = new ZoneOverviewHandler(new AnswerSupport());

        HierarchyNodeRepository nodeRepo = mock(HierarchyNodeRepository.class);
        BranchAncestorPathRepository pathRepo = mock(BranchAncestorPathRepository.class);

        HierarchyNode howrahLeaf = HierarchyNode.builder()
                .nodeId("ARAMBAGH").displayName("BRANCH ARAMBAGH").isLeaf(true).build();
        HierarchyNode barasatLeaf = HierarchyNode.builder()
                .nodeId("BAGULA").displayName("BRANCH BAGULA").isLeaf(true).build();
        HierarchyNode nasikLeaf = HierarchyNode.builder()
                .nodeId("SAVEDI").displayName("BRANCH SAVEDI").isLeaf(true).build();
        HierarchyNode howrah = HierarchyNode.builder()
                .nodeId("ZO_HOWRAH").displayName("ZO HOWRAH").nodeType("ZO").isLeaf(false).build();
        HierarchyNode barasat = HierarchyNode.builder()
                .nodeId("ZO_BARASAT").displayName("ZO BARASAT").nodeType("ZO").isLeaf(false).build();
        HierarchyNode nasik = HierarchyNode.builder()
                .nodeId("ZO_NASIK").displayName("ZO NASIK").nodeType("ZO").isLeaf(false).build();

        when(nodeRepo.findByCustomerId("BOI"))
                .thenReturn(List.of(howrahLeaf, barasatLeaf, nasikLeaf, howrah, barasat, nasik));
        when(pathRepo.findByCustomerId("BOI")).thenReturn(List.of(
                BranchAncestorPath.builder().branchNodeId("ARAMBAGH").ancestorPath(new String[]{"ZO_HOWRAH"}).build(),
                BranchAncestorPath.builder().branchNodeId("BAGULA").ancestorPath(new String[]{"ZO_BARASAT"}).build(),
                BranchAncestorPath.builder().branchNodeId("SAVEDI").ancestorPath(new String[]{"ZO_NASIK"}).build()));
        ReflectionTestUtils.setField(handler, "hierarchyNodeRepository", nodeRepo);
        ReflectionTestUtils.setField(handler, "branchAncestorPathRepository", pathRepo);

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.ZONE_OVERVIEW)
                // Prefix written once, zones slash-separated.
                .zoneFilter("ZO HOWRAH/BARASAT/NASIK")
                .originalQuestion("Current gateway status of all branches for ZO Howrah/Barasat/Nasik")
                .build();

        List<BranchSnapshot> snapshots = List.of(
                branchNoZoneTelemetry("BRANCH ARAMBAGH", NormalizedState.ONLINE),   // HOWRAH
                branchNoZoneTelemetry("BRANCH BAGULA", NormalizedState.OFFLINE),    // BARASAT
                branchNoZoneTelemetry("BRANCH SAVEDI", NormalizedState.ONLINE),     // NASIK
                branchNoZoneTelemetry("BRANCH FARAWAY", NormalizedState.ONLINE));   // no zone

        String answer = handler.handle(query, snapshots, "BOI");

        assertFalse(answer.contains("No branches found"), answer);
        assertTrue(answer.contains("ARAMBAGH"), answer);
        assertTrue(answer.contains("BAGULA"), answer);
        assertTrue(answer.contains("SAVEDI"), answer);
        assertFalse(answer.contains("FARAWAY"), "branch in no listed zone must be excluded: " + answer);
    }
}
