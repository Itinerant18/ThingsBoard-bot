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
    private final GlobalOverviewHandler globalOverviewHandler;

    public DeviceInventoryHandler(AnswerTemplateService answerTemplateService, AnswerSupport support, GlobalOverviewHandler globalOverviewHandler) {
        this.answerTemplateService = answerTemplateService;
        this.support = support;
        this.globalOverviewHandler = globalOverviewHandler;
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
            if (query.getIntent() == QueryIntent.OFFLINE_DEVICES || query.getIntent() == QueryIntent.ACTIVE_DEVICES) {
                return globalOverviewHandler.answer(snapshots, customerId);
            }
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

    private String answerGlobalOfflineBranches(List<BranchSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "**No branch data available.**";
        }
        List<String> offline = new java.util.ArrayList<>();
        List<String> unknown = new java.util.ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null || snapshot.getIdentity().getBranchName() == null) continue;
            NormalizedState state = snapshot.getGateway().getState();
            String name = snapshot.getIdentity().getBranchName();
            if (state == NormalizedState.OFFLINE || state == NormalizedState.FAULT) {
                offline.add(name);
            } else if (state == NormalizedState.UNKNOWN) {
                unknown.add(name);
            }
        }
        java.util.Collections.sort(offline);
        java.util.Collections.sort(unknown);

        StringBuilder sb = new StringBuilder();
        sb.append("**Inactive / Offline Branches Summary:**\n");
        sb.append("Total Offline: ").append(offline.size());
        if (!unknown.isEmpty()) {
            sb.append(" | Unknown: ").append(unknown.size());
        }
        sb.append("\n\n");

        if (!offline.isEmpty()) {
            sb.append("### 🔴 Offline Branches (").append(offline.size()).append("):\n");
            for (String b : offline) {
                sb.append("- ").append(b).append("\n");
            }
        }
        if (!unknown.isEmpty()) {
            sb.append("\n### ❓ Unknown Status Branches (").append(unknown.size()).append("):\n");
            for (String b : unknown) {
                sb.append("- ").append(b).append("\n");
            }
        }
        if (offline.isEmpty() && unknown.isEmpty()) {
            return "**All branches are currently active and online!**";
        }
        return sb.toString();
    }

    private String answerGlobalActiveBranches(List<BranchSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "**No branch data available.**";
        }
        List<String> online = new java.util.ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null || snapshot.getIdentity().getBranchName() == null) continue;
            if (snapshot.getGateway().getState() == NormalizedState.ONLINE) {
                online.add(snapshot.getIdentity().getBranchName());
            }
        }
        java.util.Collections.sort(online);

        StringBuilder sb = new StringBuilder();
        sb.append("**Active / Online Branches Summary (").append(online.size()).append(" Online):**\n\n");
        for (String b : online) {
            sb.append("- ").append(b).append("\n");
        }
        return sb.toString();
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
