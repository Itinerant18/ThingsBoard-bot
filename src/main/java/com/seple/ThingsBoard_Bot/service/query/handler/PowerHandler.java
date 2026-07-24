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
    private final GlobalOverviewHandler globalOverviewHandler;

    @org.springframework.beans.factory.annotation.Autowired
    public PowerHandler(AnswerTemplateService answerTemplateService, AnswerSupport support, GlobalOverviewHandler globalOverviewHandler) {
        this.answerTemplateService = answerTemplateService;
        this.support = support;
        this.globalOverviewHandler = globalOverviewHandler;
    }

    public PowerHandler(AnswerTemplateService answerTemplateService, AnswerSupport support) {
        this(answerTemplateService, support, null);
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
            return answerGlobalPowerOverview(query, snapshots, customerId);
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

    private String answerGlobalPowerOverview(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "**No branch data available.**";
        }
        java.util.Map<String, String> groupHeaders = globalOverviewHandler != null 
                ? globalOverviewHandler.resolveGroupHeaders(snapshots, customerId) 
                : java.util.Map.of();

        java.util.Map<String, java.util.List<BranchSnapshot>> grouped = new java.util.TreeMap<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null || snapshot.getIdentity().getBranchName() == null) continue;
            String header = groupHeaders.getOrDefault(snapshot.getIdentity().getBranchName(), "Other");
            grouped.computeIfAbsent(header, k -> new java.util.ArrayList<>()).add(snapshot);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Battery & Power Status Across All Branches (").append(snapshots.size()).append(" branches):**\n\n");

        for (java.util.Map.Entry<String, java.util.List<BranchSnapshot>> entry : grouped.entrySet()) {
            sb.append("- **").append(entry.getKey()).append("**\n");
            for (BranchSnapshot branch : entry.getValue()) {
                String name = support.branchName(branch);
                Double battery = branch.getPower() != null ? branch.getPower().getBatteryVoltage() : null;
                Double ac = branch.getPower() != null ? branch.getPower().getAcVoltage() : null;
                Boolean mains = branch.getPower() != null ? branch.getPower().getMainsOn() : null;

                sb.append("  - **").append(name).append("**:");
                if (battery != null) {
                    sb.append(" Battery: **").append(trimDouble(battery)).append("V DC**");
                } else {
                    sb.append(" Battery: N/A");
                }
                if (ac != null) {
                    sb.append(" | AC: ").append(trimDouble(ac)).append("V AC");
                } else if (Boolean.TRUE.equals(mains)) {
                    sb.append(" | AC Mains: ON");
                } else if (Boolean.FALSE.equals(mains)) {
                    sb.append(" | AC Mains: OFF");
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String trimDouble(Double value) {
        return value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
