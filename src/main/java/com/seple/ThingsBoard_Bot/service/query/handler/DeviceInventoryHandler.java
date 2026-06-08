package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** ACTIVE_DEVICES, FAULT_DEVICES, OFFLINE_DEVICES, CONNECTED_DEVICES. */
@Component
public class DeviceInventoryHandler implements AnswerHandler {

    private final AnswerTemplateService answerTemplateService;
    private final AnswerSupport support;

    public DeviceInventoryHandler(AnswerTemplateService answerTemplateService, AnswerSupport support) {
        this.answerTemplateService = answerTemplateService;
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.ACTIVE_DEVICES || intent == QueryIntent.FAULT_DEVICES
                || intent == QueryIntent.OFFLINE_DEVICES || intent == QueryIntent.CONNECTED_DEVICES;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        return switch (query.getIntent()) {
            case ACTIVE_DEVICES -> answerTemplateService.renderActiveDevices(branch, support.activeSystems(branch));
            case FAULT_DEVICES -> answerFaultDevices(branch);
            case OFFLINE_DEVICES -> answerOfflineDevices(branch);
            case CONNECTED_DEVICES -> answerConnectedDevices(branch);
            default -> null;
        };
    }

    private String answerFaultDevices(BranchSnapshot branch) {
        List<String> faultDevices = support.systemsByState(branch, NormalizedState.FAULT);
        if (!faultDevices.isEmpty()) {
            return "**For Branch " + support.branchName(branch) + ", Fault Devices ("
                    + faultDevices.size() + "): " + String.join(", ", faultDevices) + ".**";
        }

        List<String> branchLevelIndicators = support.branchLevelFaultIndicators(branch);
        if (!branchLevelIndicators.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", no specific fault device is deterministically identified"
                    + ". Branch-level fault indicators are present: " + String.join(", ", branchLevelIndicators) + ".**";
        }

        return "**For Branch " + support.branchName(branch)
                + ", no fault devices are currently identified.**";
    }

    private String answerOfflineDevices(BranchSnapshot branch) {
        List<String> offlineDevices = support.systemsByState(branch, NormalizedState.OFFLINE);
        if (offlineDevices.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", no offline devices are currently identified.**";
        }
        return "**For Branch " + support.branchName(branch) + ", Offline Devices ("
                + offlineDevices.size() + "): " + String.join(", ", offlineDevices) + ".**";
    }

    private String answerConnectedDevices(BranchSnapshot branch) {
        List<String> connectedDevices = support.installedSystems(branch);
        if (connectedDevices.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", no connected devices are currently identified.**";
        }
        return "**For Branch " + support.branchName(branch) + ", Connected Devices ("
                + connectedDevices.size() + "): " + String.join(", ", connectedDevices) + ".**";
    }
}
