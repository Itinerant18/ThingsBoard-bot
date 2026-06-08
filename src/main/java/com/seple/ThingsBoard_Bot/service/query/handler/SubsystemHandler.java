package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** SUBSYSTEM_STATUS, SUBSYSTEM_FAULT_STATUS, SUBSYSTEM_ALARM_STATUS. */
@Component
public class SubsystemHandler implements AnswerHandler {

    private final AnswerTemplateService answerTemplateService;
    private final AnswerSupport support;

    public SubsystemHandler(AnswerTemplateService answerTemplateService, AnswerSupport support) {
        this.answerTemplateService = answerTemplateService;
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.SUBSYSTEM_STATUS || intent == QueryIntent.SUBSYSTEM_FAULT_STATUS
                || intent == QueryIntent.SUBSYSTEM_ALARM_STATUS;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        String targetSystem = query.getTargetSystem();
        return switch (query.getIntent()) {
            case SUBSYSTEM_STATUS -> answerSubsystemStatus(branch, targetSystem);
            case SUBSYSTEM_FAULT_STATUS -> answerSubsystemFaultStatus(branch, targetSystem);
            case SUBSYSTEM_ALARM_STATUS -> answerSubsystemAlarmStatus(branch, targetSystem);
            default -> null;
        };
    }

    private String answerSubsystemStatus(BranchSnapshot branch, String targetSystem) {
        SubsystemStatus subsystem = support.subsystemByTarget(branch, targetSystem);
        if (subsystem == null) {
            return null;
        }
        return answerTemplateService.renderSubsystemStatus(branch, subsystem.getSystemName(),
                support.formatState(subsystem.getState()));
    }

    private String answerSubsystemFaultStatus(BranchSnapshot branch, String targetSystem) {
        SubsystemStatus subsystem = support.subsystemByTarget(branch, targetSystem);
        if (subsystem == null) {
            return "**For Branch " + support.branchName(branch) + ", subsystem fault status is not available.**";
        }
        if (subsystem.getState() == NormalizedState.NOT_INSTALLED || !subsystem.isInstalled()) {
            return answerTemplateService.renderSubsystemFaultStatus(branch, subsystem.getSystemName(), "NOT INSTALLED");
        }

        Boolean fault = support.resolveSubsystemFault(branch, targetSystem);
        if (Boolean.TRUE.equals(fault)) {
            return answerTemplateService.renderSubsystemFaultStatus(branch, subsystem.getSystemName(), "ACTIVE");
        }
        if (Boolean.FALSE.equals(fault)) {
            return answerTemplateService.renderSubsystemFaultStatus(branch, subsystem.getSystemName(), "NORMAL");
        }
        return answerTemplateService.renderSubsystemFaultStatus(branch, subsystem.getSystemName(), "N/A");
    }

    private String answerSubsystemAlarmStatus(BranchSnapshot branch, String targetSystem) {
        SubsystemStatus subsystem = support.subsystemByTarget(branch, targetSystem);
        if (subsystem == null) {
            return "**For Branch " + support.branchName(branch) + ", subsystem alarm status is not available.**";
        }
        if (subsystem.getState() == NormalizedState.NOT_INSTALLED || !subsystem.isInstalled()) {
            return answerTemplateService.renderSubsystemAlarmStatus(branch, subsystem.getSystemName(), "NOT INSTALLED");
        }

        Boolean alarm = support.resolveSubsystemAlarm(branch, targetSystem);
        if (Boolean.TRUE.equals(alarm)) {
            return answerTemplateService.renderSubsystemAlarmStatus(branch, subsystem.getSystemName(), "ACTIVE");
        }
        if (Boolean.FALSE.equals(alarm)) {
            return answerTemplateService.renderSubsystemAlarmStatus(branch, subsystem.getSystemName(), "NORMAL");
        }
        return answerTemplateService.renderSubsystemAlarmStatus(branch, subsystem.getSystemName(), "N/A");
    }
}
