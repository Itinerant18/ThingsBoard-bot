package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.GatewayStatus;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;

/**
 * GLOBAL_OVERVIEW must report branches whose gateway_sts attribute is absent (UNKNOWN) in their own
 * bucket instead of silently counting them as Offline. Exercises the flat handler path (the
 * single-arg DeterministicAnswerService wires GlobalOverviewHandler without hierarchy repos).
 */
class GlobalOverviewUnknownBucketTest {

    private final DeterministicAnswerService answerService =
            new DeterministicAnswerService(new AnswerTemplateService());

    private BranchSnapshot snapshot(String name, NormalizedState state) {
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName(name).technicalId(name).build())
                .gateway(GatewayStatus.builder().state(state).build())
                .build();
    }

    private String overview(List<BranchSnapshot> snapshots) {
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.GLOBAL_OVERVIEW)
                .originalQuestion("List all branches")
                .global(true)
                .deterministic(true)
                .confidence(1.0)
                .build();
        return answerService.answer(query, snapshots);
    }

    @Test
    void unknownGatewayIsReportedAsUnknownNotOffline() {
        String answer = overview(List.of(
                snapshot("BRANCH ONLINE", NormalizedState.ONLINE),
                snapshot("BRANCH OFFLINE", NormalizedState.OFFLINE),
                snapshot("BRANCH NOSTS", NormalizedState.UNKNOWN)));

        assertTrue(answer.contains("1 Online | 1 Offline | 1 Unknown"),
                "total must split out the Unknown bucket: " + answer);
        assertTrue(answer.contains("Unknown:"), "an Unknown section must be rendered: " + answer);
        assertTrue(answer.contains("BRANCH NOSTS"), answer);
    }

    @Test
    void cleanDataKeepsTwoPartTotalWithNoUnknownSection() {
        String answer = overview(List.of(
                snapshot("BRANCH ONLINE", NormalizedState.ONLINE),
                snapshot("BRANCH OFFLINE", NormalizedState.OFFLINE)));

        assertTrue(answer.contains("1 Online | 1 Offline"), answer);
        assertFalse(answer.contains("Unknown"), "no Unknown wording when there are none: " + answer);
    }
}
