package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.BranchSubsystems;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * Per-subsystem fleet overview: when the query carries a targetSystem, GLOBAL_OVERVIEW buckets by
 * that subsystem's state (not gateway) and keeps a distinct Not Installed bucket so absent systems
 * aren't miscounted as Offline. Hierarchy repos are null here, exercising the "Other"-group path.
 */
class SubsystemFleetOverviewTest {

    private final GlobalOverviewHandler handler = new GlobalOverviewHandler(null);

    private BranchSnapshot branch(String name, NormalizedState cctvState) {
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName(name).build())
                .subsystems(BranchSubsystems.builder()
                        .cctv(SubsystemStatus.builder().state(cctvState).build())
                        .build())
                .build();
    }

    private ResolvedQuery cctvOverviewQuery() {
        return ResolvedQuery.builder()
                .intent(QueryIntent.GLOBAL_OVERVIEW)
                .targetSystem("cctv")
                .build();
    }

    @Test
    void bucketsByCctvStateWithNotInstalledSeparate() {
        List<BranchSnapshot> snapshots = List.of(
                branch("BRANCH A", NormalizedState.ONLINE),
                branch("BRANCH B", NormalizedState.OFFLINE),
                branch("BRANCH C", NormalizedState.NOT_INSTALLED),
                branch("BRANCH D", NormalizedState.UNKNOWN));

        String answer = handler.handle(cctvOverviewQuery(), snapshots, "BOI");

        assertTrue(answer.contains("CCTV"), answer);
        // Prose summary: one branch in each bucket.
        assertTrue(answer.contains("1 Branches** have active/working **Online**"), answer);
        assertTrue(answer.contains("Offline / Faulty"), answer);
        assertTrue(answer.contains("Not Installed"), answer);
        assertTrue(answer.contains("Unknown / Unreachable"), answer);
        assertTrue(answer.contains("Not Installed:"), "Not Installed must be its own bucket: " + answer);
        assertTrue(answer.contains("BRANCH C"), answer);
    }

    @Test
    void offlineAndNotInstalledAreNotConflated() {
        // The core reason for the Not Installed bucket: an absent subsystem is not a fault.
        List<BranchSnapshot> snapshots = List.of(
                branch("BRANCH A", NormalizedState.NOT_INSTALLED),
                branch("BRANCH B", NormalizedState.NOT_INSTALLED));

        String answer = handler.handle(cctvOverviewQuery(), snapshots, "BOI");

        assertTrue(answer.contains("2 Branches** have CCTV **Not Installed**"), answer);
        assertFalse(answer.contains("Offline:"), "no Offline bucket section when nothing is offline: " + answer);
    }
}
