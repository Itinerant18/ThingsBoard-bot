package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * CCTV recording days/compliance is computed from the per-channel days signal
 * (total_recording_days on CP Plus/Dahua, total_duration on Hikvision/rock) against the 90-day
 * retention target. Single-branch summarises that branch; fleet aggregates every branch.
 */
class CctvRecordingComplianceTest {

    private final CctvHandler handler = new CctvHandler(null, new AnswerSupport());

    private BranchSnapshot branch(String name, String recKey, String recJson) {
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName(name).build())
                .rawData(Map.of(recKey, recJson))
                .build();
    }

    @Test
    void singleBranchReportsDaysAndCompliance() {
        // Dahua: 401 days (compliant) + 10 days (non-compliant, <90).
        BranchSnapshot b = branch("BRANCH A", "Dahua_NVR_CameraRecInfo",
                "[{\"channel\":1,\"total_recording_days\":401},{\"channel\":2,\"total_recording_days\":10}]");
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_RECORDING_INFO).targetBranch(b).build();

        String answer = handler.handle(q, List.of(b), "BOI");

        assertTrue(answer.contains("2 camera(s)"), answer);
        assertTrue(answer.contains("1 compliant"), answer);
        assertTrue(answer.contains("1 non-compliant"), answer);
        assertTrue(answer.contains("401"), answer); // max days surfaced
    }

    @Test
    void totalDurationIsTreatedAsRecordedDays() {
        // Hikvision/rock report days under total_duration; 0 must count as a zero-day camera.
        BranchSnapshot b = branch("BRANCH Z", "rock_VIDEOdETAILS",
                "[{\"channel_no\":\"1\",\"total_duration\":120},{\"channel_no\":\"2\",\"total_duration\":0}]");
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_RECORDING_INFO).targetBranch(b).build();

        String answer = handler.handle(q, List.of(b), "BOI");

        assertTrue(answer.contains("1 compliant"), answer);      // 120 >= 90
        assertTrue(answer.contains("0 days"), answer);           // the 0-day camera called out
    }

    @Test
    void fleetComplianceAggregatesAndListsNonCompliantBranches() {
        BranchSnapshot good = branch("BRANCH GOOD", "Dahua_NVR_CameraRecInfo",
                "[{\"channel\":1,\"total_recording_days\":200}]");
        BranchSnapshot bad = branch("BRANCH BAD", "CP_Plus_NVR_CameraRecInfo",
                "[{\"channel\":1,\"total_recording_days\":5},{\"channel\":2,\"total_recording_days\":0}]");
        ResolvedQuery q = ResolvedQuery.builder().intent(QueryIntent.CCTV_RECORDING_COMPLIANCE).build();

        String answer = handler.handle(q, List.of(good, bad), "BOI");

        assertTrue(answer.contains("3 cameras total"), answer);
        assertTrue(answer.contains("1 compliant"), answer);
        assertTrue(answer.contains("2 non-compliant"), answer);
        assertTrue(answer.contains("1 with 0 days"), answer);
        assertTrue(answer.contains("BAD"), answer);
        assertTrue(!answer.contains("GOOD ("), "compliant branch not listed as non-compliant: " + answer);
    }
}
