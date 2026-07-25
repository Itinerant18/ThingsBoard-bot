package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** FAULT_REASON surfaces the device's own Device_Issue string, single-branch and fleet. */
class FaultReasonDeviceIssueTest {

    private final FaultReasonHandler handler = new FaultReasonHandler(new AnswerSupport());

    private BranchSnapshot branch(String name, String issue) {
        Map<String, Object> raw = issue == null ? Map.of() : Map.of("Device_Issue", issue);
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName(name).build())
                .rawData(raw).build();
    }

    @Test
    void singleBranchReportsDeviceIssue() {
        BranchSnapshot b = branch("BRANCH A", "battery_low");
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.FAULT_REASON).targetBranch(b).build();

        String answer = handler.handle(q, List.of(b), "BOI");

        assertTrue(answer.contains("battery low"), answer);
    }

    @Test
    void fleetListsBranchesReportingIssues() {
        BranchSnapshot a = branch("LILUAH", "hdd_error");
        BranchSnapshot ok = branch("DOBSON", null);
        ResolvedQuery q = ResolvedQuery.builder().intent(QueryIntent.FAULT_REASON).build();

        String answer = handler.handle(q, List.of(a, ok), "BOI");

        assertTrue(answer.contains("LILUAH"), answer);
        assertTrue(answer.contains("hdd error"), answer);
        assertTrue(!answer.contains("DOBSON"), "issue-free branch not listed: " + answer);
    }
}
