package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** FAULT_REASON. */
@Component
public class FaultReasonHandler implements AnswerHandler {

    private final AnswerSupport support;

    public FaultReasonHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.FAULT_REASON;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return answerFleetIssues(snapshots);
        }
        Map<String, Object> raw = branch.getRawData();
        List<String> reasons = new ArrayList<>();

        // Device_Issue is the device's own current-issue string (e.g. "battery_low", "hdd_error",
        // "camera_tampered"), populated on most devices — the most direct fault signal we have.
        String issue = support.firstNonBlank(raw, "Device_Issue");
        if (issue != null) {
            reasons.add(readable(issue));
        }

        if (support.isTrue(raw.get("ticketStatus_FAS_FAULT")) || support.isTrue(raw.get("fireAlarmSystem_fault"))
                || support.isTrue(raw.get("fire_alarm_system_fault"))) {
            reasons.add("fire alarm fault indicator is active");
        }
        if (support.isTrue(raw.get("ticketStatus_IAS_FAULT")) || support.isTrue(raw.get("intrusion_alarm_system_fault"))) {
            reasons.add("intrusion alarm fault indicator is active");
        }
        if (support.isTrue(raw.get("ticketStatus_NVR_OFF")) || support.isTrue(raw.get("DVR/NVR OFF"))) {
            reasons.add("NVR/DVR off indicator is active");
        }
        if (support.isTrue(raw.get("HDD ERROR")) || support.isTrue(raw.get("ticketStatus_HDD_ERROR"))) {
            reasons.add("HDD error indicator is active");
        }
        if (support.isTrue(raw.get("CAMERA DISCONNECT"))) {
            reasons.add("camera disconnect indicator is active");
        }

        if (reasons.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", no modeled fault reason is currently available.**";
        }

        return "**For Branch " + support.branchName(branch) + ", there is a fault indication because "
                + String.join(", ", reasons) + ".**";
    }

    /** Fleet: which branches are reporting a device-level issue, and what. */
    private String answerFleetIssues(List<BranchSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "No branches are in scope.";
        }
        List<String> lines = new ArrayList<>();
        for (BranchSnapshot s : snapshots) {
            String issue = support.firstNonBlank(s.getRawData(), "Device_Issue");
            if (issue != null) {
                lines.add("- **" + support.branchName(s) + "** — " + readable(issue));
            }
        }
        if (lines.isEmpty()) {
            return "✅ No branches are currently reporting a device issue.";
        }
        StringBuilder b = new StringBuilder("### ⚠️ Branches Reporting an Issue (")
                .append(lines.size()).append(")\n\n");
        lines.forEach(l -> b.append(l).append("\n"));
        return b.toString();
    }

    /** "battery_low" -> "battery low"; the raw Device_Issue token is snake_case. */
    private static String readable(String issue) {
        return issue.trim().replace('_', ' ');
    }
}
