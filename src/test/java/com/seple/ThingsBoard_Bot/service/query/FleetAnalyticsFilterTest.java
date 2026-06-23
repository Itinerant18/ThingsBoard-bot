package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;
import com.seple.ThingsBoard_Bot.service.query.handler.AnswerSupport;
import com.seple.ThingsBoard_Bot.service.query.handler.FleetAnalyticsHandler;

/**
 * Multi-condition fleet filter over constructed snapshots with known subsystem states (gateway_sts /
 * &lt;x&gt;_sts drive the normalized states).
 */
class FleetAnalyticsFilterTest {

    private final BranchSnapshotMapper mapper =
            new BranchSnapshotMapper(new FieldPrecedenceResolver(new ValueNormalizer()), new ValueNormalizer());
    private final FleetAnalyticsHandler handler = new FleetAnalyticsHandler(new AnswerSupport());

    private BranchSnapshot branch(String name, String gateway, String ias, String cctv, String acs) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("branchName", name);
        raw.put("gateway_sts", gateway);
        raw.put("ias_sts", ias);
        raw.put("cctv_sts", cctv);
        raw.put("accessControl_sts", acs);
        return mapper.map(raw);
    }

    private String filter(String question, List<BranchSnapshot> snaps) {
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.BRANCH_FILTER)
                .originalQuestion(question)
                .deterministic(true)
                .confidence(1.0)
                .build();
        return handler.handle(query, snaps, "BOI");
    }

    @Test
    void filtersGatewayOfflineButIasOnline() {
        List<BranchSnapshot> snaps = List.of(
                branch("ALPHA", "Offline", "Online", "Online", "N/A"),   // matches
                branch("BETA", "Online", "Online", "Online", "N/A"),     // gateway not offline
                branch("GAMMA", "Offline", "Offline", "Online", "N/A")); // ias not online
        String answer = filter("Show branches where Gateway is offline but IAS is online", snaps);
        assertTrue(answer.contains("ALPHA"), answer);
        assertTrue(!answer.contains("BETA"), answer);
        assertTrue(!answer.contains("GAMMA"), answer);
    }

    @Test
    void filtersBranchesMissingCctvAndAcs() {
        List<BranchSnapshot> snaps = List.of(
                branch("ALPHA", "Online", "Online", "N/A", "N/A"),    // both missing -> matches
                branch("BETA", "Online", "Online", "Online", "N/A")); // cctv present -> no
        String answer = filter("Show branches missing CCTV and ACS", snaps);
        assertTrue(answer.contains("ALPHA"), answer);
        assertTrue(!answer.contains("BETA"), answer);
    }

    @Test
    void filtersBranchesWithAtLeastThreeActiveSystems() {
        List<BranchSnapshot> snaps = List.of(
                branch("ALPHA", "Online", "Online", "Online", "N/A"),  // 3 online -> matches
                branch("BETA", "Online", "Online", "N/A", "N/A"));     // 2 online -> no
        String answer = filter("Show branches with at least 3 active systems", snaps);
        assertTrue(answer.contains("ALPHA"), answer);
        assertTrue(!answer.contains("BETA"), answer);
    }

    @Test
    void reportsNoMatchesHonestly() {
        List<BranchSnapshot> snaps = List.of(branch("ALPHA", "Online", "Online", "Online", "Online"));
        String answer = filter("Show branches where Gateway is offline but IAS is online", snaps);
        assertTrue(answer.contains("No branches match"), answer);
    }
}
