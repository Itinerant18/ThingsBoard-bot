package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** ACCESS_CONTROL_USER_COUNT, ACCESS_CONTROL_DEVICE_INFO. */
@Component
public class AccessControlHandler implements AnswerHandler {

    private final AnswerSupport support;

    public AccessControlHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.ACCESS_CONTROL_USER_COUNT
                || intent == QueryIntent.ACCESS_CONTROL_DEVICE_INFO;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        return switch (query.getIntent()) {
            case ACCESS_CONTROL_USER_COUNT -> answerAccessControlUserCount(branch);
            case ACCESS_CONTROL_DEVICE_INFO -> answerAccessControlDeviceInfo(branch);
            default -> null;
        };
    }

    private String answerAccessControlUserCount(BranchSnapshot branch) {
        Integer userCount = support.firstInteger(branch.getRawData(),
                "accessControlTotalUsers", "totalUsers", "registeredUsers", "accessControlUserCount", "userCount");
        if (userCount != null) {
            return "**For Branch " + support.branchName(branch)
                    + ", Access Control User Count is " + userCount + ".**";
        }

        return "**For Branch " + support.branchName(branch)
                + ", access control user count is not available in current branch data. Current status is "
                + support.formatState(branch.getSubsystems().getAccessControl().getState()) + ".**";
    }

    private String answerAccessControlDeviceInfo(BranchSnapshot branch) {
        String model = support.firstNonBlank(branch.getRawData(), "accessControlModel", "biometricModel", "acsModel");
        String firmware = support.firstNonBlank(branch.getRawData(), "accessControlFirmware", "biometricFirmware",
                "accessControlFirmwareVersion");
        String ip = support.firstNonBlank(branch.getRawData(), "accessControlIp", "biometricIp", "accessControlIP");
        String door = support.firstNonBlank(branch.getRawData(), "accessControlDoor");
        String status = support.formatState(branch.getSubsystems().getAccessControl().getState());

        if (model == null && firmware == null && ip == null) {
            String suffix = door != null ? " Door: " + door.toUpperCase() + "." : "";
            return "**For Branch " + support.branchName(branch)
                    + ", access control device information is not available in current branch data. Status is "
                    + status + "." + suffix + "**";
        }

        List<String> parts = new ArrayList<>();
        parts.add("Status: " + status);
        if (model != null) {
            parts.add("Model: " + model);
        }
        if (firmware != null) {
            parts.add("Firmware: " + firmware);
        }
        if (ip != null) {
            parts.add("IP: " + ip);
        }
        if (door != null) {
            parts.add("Door: " + door.toUpperCase());
        }
        return "**For Branch " + support.branchName(branch)
                + ", Access Control Device Info is: " + String.join(", ", parts) + ".**";
    }
}
