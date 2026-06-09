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
                || intent == QueryIntent.SYSTEM_CURRENT || intent == QueryIntent.BATTERY_LOW_STATUS
                || intent == QueryIntent.BATTERY_HEALTH || intent == QueryIntent.POWER_STATUS;
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
            case BATTERY_HEALTH -> answerBatteryHealth(branch);
            case POWER_STATUS -> answerPowerStatus(branch);
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

    private String answerBatteryHealth(BranchSnapshot branch) {
        Double voltage = branch.getPower().getBatteryVoltage();
        if (voltage != null) {
            String vStr = trimDouble(voltage);
            return "**For Branch " + support.branchName(branch) + ", the Battery Status is HEALTHY. Voltage: " + vStr + "V DC.**";
        }
        return "**For Branch " + support.branchName(branch) + ", the Battery Status is not available.**";
    }

    private String answerPowerStatus(BranchSnapshot branch) {
        com.seple.ThingsBoard_Bot.model.domain.PowerStatus power = branch.getPower();
        Double batteryVoltage = power.getBatteryVoltage();
        Double acVoltage = power.getAcVoltage();
        Boolean mainsOn = power.getMainsOn();

        boolean isOn;
        if (mainsOn != null) {
            isOn = mainsOn;
        } else {
            isOn = (acVoltage != null && acVoltage > 0);
        }

        String acStr;
        if (isOn) {
            acStr = acVoltage != null ? trimDouble(acVoltage) + "V AC" : "N/A";
        } else {
            acStr = "Offline";
        }

        String batteryStr = batteryVoltage != null ? trimDouble(batteryVoltage) + "V DC" : "N/A";

        return "**For Branch " + support.branchName(branch)
                + ", the Power Status is " + (isOn ? "ON" : "OFF")
                + ". AC Mains: " + acStr
                + ", Battery Backup: " + batteryStr + ".**";
    }

    private String trimDouble(Double value) {
        return value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
