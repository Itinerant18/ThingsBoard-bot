package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * ALARM_STATUS and ERROR_STATUS.
 *
 * <p>Supports both single-branch (detailed) and fleet-wide (total count) modes.
 * Bug 4a fix: previously returned null for fleet mode, falling through to LLM.
 */
@Component
public class AlertHandler implements AnswerHandler {

    private final AnswerTemplateService answerTemplateService;

    public AlertHandler(AnswerTemplateService answerTemplateService) {
        this.answerTemplateService = answerTemplateService;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.ALARM_STATUS || intent == QueryIntent.ERROR_STATUS;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();

        // Fleet mode: no branch targeted → sum across all snapshots
        if (branch == null) {
            return switch (query.getIntent()) {
                case ALARM_STATUS -> renderFleetAlarms(snapshots);
                case ERROR_STATUS -> renderFleetErrors(snapshots);
                default -> null;
            };
        }

        // Single-branch mode
        return switch (query.getIntent()) {
            case ALARM_STATUS -> answerTemplateService.renderAlertStatus(branch, "Alarm Count",
                    branch.getAlerts().getAlarmCount());
            case ERROR_STATUS -> answerTemplateService.renderAlertStatus(branch, "Error Count",
                    branch.getAlerts().getErrorCount());
            default -> null;
        };
    }

    private String renderFleetAlarms(List<BranchSnapshot> snapshots) {
        int total = 0;
        int branchesWithAlarms = 0;
        for (BranchSnapshot snap : snapshots) {
            if (snap.getAlerts() != null && snap.getAlerts().getAlarmCount() > 0) {
                total += snap.getAlerts().getAlarmCount();
                branchesWithAlarms++;
            }
        }
        if (total == 0) {
            return "✅ **No open alarms** detected across all " + snapshots.size() + " branches.";
        }
        return "### 🚨 Fleet Alarm Summary\n\n"
                + "**Total open alarms:** " + total + "\n"
                + "**Branches with alarms:** " + branchesWithAlarms + " / " + snapshots.size() + "\n\n"
                + "_For severity breakdown, ask: \"Show alarm severity breakdown\"_";
    }

    private String renderFleetErrors(List<BranchSnapshot> snapshots) {
        int total = 0;
        int branchesWithErrors = 0;
        for (BranchSnapshot snap : snapshots) {
            if (snap.getAlerts() != null && snap.getAlerts().getErrorCount() > 0) {
                total += snap.getAlerts().getErrorCount();
                branchesWithErrors++;
            }
        }
        if (total == 0) {
            return "✅ **No open errors** detected across all " + snapshots.size() + " branches.";
        }
        return "### ⚠️ Fleet Error Summary\n\n"
                + "**Total open errors:** " + total + "\n"
                + "**Branches with errors:** " + branchesWithErrors + " / " + snapshots.size();
    }
}

