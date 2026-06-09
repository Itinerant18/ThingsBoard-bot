package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** NETWORK_STATUS. */
@Component
public class NetworkStatusHandler implements AnswerHandler {

    private final AnswerSupport support;

    public NetworkStatusHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.NETWORK_STATUS;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        Boolean network = support.resolveBoolean(branch.getRawData(), "NETWORK", "gatewayStatus_NETWORK");
        String operator = support.firstNonBlank(branch.getRawData(), "system_status_statusbox_network", "networkOperator");

        if (Boolean.TRUE.equals(network)) {
            if (operator != null) {
                return "**For Branch " + support.branchName(branch)
                        + ", the Network Status is ON. Network Operator: " + operator + ".**";
            }
            return "**For Branch " + support.branchName(branch) + ", the Network Status is ON.**";
        }
        if (Boolean.FALSE.equals(network)) {
            return "**For Branch " + support.branchName(branch) + ", the Network Status is OFFLINE.**";
        }
        if (operator != null) {
            return "**For Branch " + support.branchName(branch)
                    + ", the Network Status is ON. Network Operator: " + operator + ".**";
        }
        return "**For Branch " + support.branchName(branch) + ", the Network Status is N/A.**";
    }
}
