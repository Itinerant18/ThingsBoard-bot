package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;


import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** DOOR_STATUS. */
@Component
public class DoorStatusHandler implements AnswerHandler {

    private final AnswerSupport support;

    public DoorStatusHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.DOOR_STATUS;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        String targetSystem = query.getTargetSystem();
        if ("timeLock".equals(targetSystem)) {
            String door = support.firstNonBlank(branch.getRawData(), "timeLockDoor");
            if (door != null) {
                return "**For Branch " + support.branchName(branch)
                        + ", Time Lock Door Status is " + door.toUpperCase() + ".**";
            }
            return "**For Branch " + support.branchName(branch)
                    + ", Time Lock Door Status is not available.**";
        }
        if ("accessControl".equals(targetSystem)) {
            String door = support.firstNonBlank(branch.getRawData(), "accessControlDoor");
            if (door != null) {
                return "**For Branch " + support.branchName(branch)
                        + ", Access Control Door Status is " + door.toUpperCase() + ".**";
            }
            return "**For Branch " + support.branchName(branch)
                    + ", Access Control Door Status is not available.**";
        }
        
        // Check both door types if neither is explicitly targeted
        String timeLockDoor = support.firstNonBlank(branch.getRawData(), "timeLockDoor");
        String acDoor = support.firstNonBlank(branch.getRawData(), "accessControlDoor");
        if (timeLockDoor != null || acDoor != null) {
            List<String> statuses = new ArrayList<>();
            if (timeLockDoor != null) {
                statuses.add("Time Lock Door is " + timeLockDoor.toUpperCase());
            }
            if (acDoor != null) {
                statuses.add("Access Control Door is " + acDoor.toUpperCase());
            }
            return "**For Branch " + support.branchName(branch) + ", " + String.join(", and ", statuses) + ".**";
        }
        
        return "**For Branch " + support.branchName(branch) + ", Door status is not available.**";
    }
}
