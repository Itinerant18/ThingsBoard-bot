package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** BATTERY_VOLTAGE, AC_VOLTAGE, SYSTEM_CURRENT, BATTERY_LOW_STATUS. */
@Component
public class PowerHandler implements AnswerHandler {

    private final AnswerTemplateService answerTemplateService;
    private final AnswerSupport support;

    public PowerHandler(AnswerTemplateService answerTemplateService, AnswerSupport support) {
        this.answerTemplateService = answerTemplateService;
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.BATTERY_VOLTAGE || intent == QueryIntent.AC_VOLTAGE
                || intent == QueryIntent.SYSTEM_CURRENT || intent == QueryIntent.BATTERY_LOW_STATUS;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        return switch (query.getIntent()) {
            case BATTERY_VOLTAGE -> answerTemplateService.renderMetric(branch, "Battery Voltage Reading",
                    branch.getPower().getBatteryVoltage(), "V DC");
            case AC_VOLTAGE -> answerTemplateService.renderMetric(branch, "AC Input Voltage",
                    branch.getPower().getAcVoltage(), "V AC");
            case SYSTEM_CURRENT -> answerTemplateService.renderMetric(branch, "System Current",
                    branch.getPower().getSystemCurrent(), " Amp");
            case BATTERY_LOW_STATUS -> answerBatteryLowStatus(branch);
            default -> null;
        };
    }

    private String answerBatteryLowStatus(BranchSnapshot branch) {
        Boolean batteryLow = support.resolveBoolean(branch.getRawData(), "BATTERY LOW", "gatewayStatus_BATTERY LOW",
                "system_status_statusbox_battery_low", "ticketStatus_BATTERY_LOW");
        if (Boolean.TRUE.equals(batteryLow)) {
            return "**For Branch " + support.branchName(branch)
                    + ", Battery Low Status is WARNING ACTIVE.**";
        }
        if (Boolean.FALSE.equals(batteryLow)) {
            return "**For Branch " + support.branchName(branch)
                    + ", Battery Low Status is NORMAL. No low battery warning is active.**";
        }
        return "**For Branch " + support.branchName(branch) + ", Battery Low Status is N/A.**";
    }
}
