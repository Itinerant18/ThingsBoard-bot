package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** NETWORK_STATUS, NETWORK_OPERATOR. */
@Component
public class NetworkStatusHandler implements AnswerHandler {

    private final AnswerSupport support;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NetworkStatusHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.NETWORK_STATUS || intent == QueryIntent.NETWORK_OPERATOR;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        return switch (query.getIntent()) {
            case NETWORK_STATUS -> answerNetworkStatus(branch);
            case NETWORK_OPERATOR -> answerNetworkOperator(branch);
            default -> null;
        };
    }

    private String answerNetworkOperator(BranchSnapshot branch) {
        String operator = resolveOperator(branch.getRawData());
        if (operator == null) {
            return "**For Branch " + support.branchName(branch) + ", the network operator (SIM) is not mapped.**";
        }
        return "**For Branch " + support.branchName(branch) + ", the network operator (SIM) is " + operator + ".**";
    }

    /** Operator from the direct status fields, falling back to the service_provider in log_type_operator_detail. */
    private String resolveOperator(Map<String, Object> raw) {
        String operator = support.firstNonBlank(raw,
                "system_status_statusbox_network", "statusbox_network", "networkOperator");
        if (operator != null) {
            return operator;
        }
        String detail = support.firstNonBlank(raw, "log_type_operator_detail");
        if (detail != null && detail.startsWith("[")) {
            try {
                JsonNode node = objectMapper.readTree(detail);
                if (node.isArray() && !node.isEmpty()) {
                    String provider = node.get(0).path("service_provider").asText(null);
                    if (provider != null && !provider.isBlank()) {
                        return provider;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String answerNetworkStatus(BranchSnapshot branch) {
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
