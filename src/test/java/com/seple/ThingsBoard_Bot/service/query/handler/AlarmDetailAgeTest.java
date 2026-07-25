package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.ActiveAlarm;
import com.seple.ThingsBoard_Bot.model.domain.AlertSummary;
import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.AlarmFetchService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * Active-alarm age/recency is computed from the alarm's createdTime. Uses a no-op AlarmFetchService
 * so the preset topAlarms are read directly (no ThingsBoard API call).
 */
class AlarmDetailAgeTest {

    /** No-op fetcher: leaves the snapshot's preset alarm detail untouched. */
    private final AlarmFetchService noopFetch = new AlarmFetchService(null) {
        @Override
        public void enrichWithAlarmDetail(List<BranchSnapshot> snapshots) { /* preset */ }
    };
    private final AlarmDetailHandler handler = new AlarmDetailHandler(noopFetch, new AnswerSupport());

    private BranchSnapshot branchWithAlarm(String name, long createdTime) {
        ActiveAlarm alarm = new ActiveAlarm("id1", "BOI- CAMERA DISCONNECT", "MAJOR",
                "ACTIVE_UNACK", createdTime, name, "dev1");
        AlertSummary alerts = AlertSummary.builder()
                .alarmCount(1).topAlarms(List.of(alarm)).alarmDetailLoaded(true).build();
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName(name).build())
                .alerts(alerts).build();
    }

    @Test
    void oldestQuestionReportsAge() {
        long threeDaysAgo = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000;
        BranchSnapshot b = branchWithAlarm("BRANCH A", threeDaysAgo);
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.ALARM_DETAIL)
                .originalQuestion("what is the oldest currently active alarm").build();

        String answer = handler.handle(q, List.of(b), "BOI");

        assertTrue(answer.contains("Oldest"), answer);
        assertTrue(answer.contains("3d"), answer);            // age rendered
        assertTrue(answer.contains("CAMERA DISCONNECT"), answer);
    }

    @Test
    void lastHourFiltersByCreatedTime() {
        long tenMinAgo = System.currentTimeMillis() - 10L * 60 * 1000;
        long twoDaysAgo = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000;
        BranchSnapshot recent = branchWithAlarm("BRANCH RECENT", tenMinAgo);
        BranchSnapshot old = branchWithAlarm("BRANCH OLD", twoDaysAgo);
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.ALARM_DETAIL)
                .originalQuestion("what alarms were triggered in the last hour").build();

        String answer = handler.handle(q, List.of(recent, old), "BOI");

        assertTrue(answer.contains("last hour"), answer);
        assertTrue(answer.contains("RECENT"), answer);
        assertTrue(!answer.contains("BRANCH OLD"), "alarm outside window excluded: " + answer);
    }
}
