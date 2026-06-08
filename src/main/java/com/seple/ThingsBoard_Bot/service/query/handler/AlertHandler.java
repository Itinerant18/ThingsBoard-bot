package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** ALARM_STATUS and ERROR_STATUS. */
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
        if (branch == null) {
            return null;
        }
        return switch (query.getIntent()) {
            case ALARM_STATUS -> answerTemplateService.renderAlertStatus(branch, "Alarm Count",
                    branch.getAlerts().getAlarmCount());
            case ERROR_STATUS -> answerTemplateService.renderAlertStatus(branch, "Error Count",
                    branch.getAlerts().getErrorCount());
            default -> null;
        };
    }
}
