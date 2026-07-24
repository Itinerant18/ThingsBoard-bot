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
}
