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
            // Fleet-level network query
            return answerFleetNetwork(snapshots);
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

    // ── Fleet-level methods ─────────────────────────────────────────────────

    private String answerFleetNetwork(List<BranchSnapshot> snapshots) {
        return buildNetworkSummary("Fleet", snapshots);
    }

    /**
     * Package-visible: builds a formatted network summary for any snapshot subset.
     * Called by both this handler (fleet mode) and {@link ZoneOverviewHandler} (zone mode).
     *
     * @param scopeLabel human-readable label for the scope (e.g., "Fleet" or "ZO HOWRAH")
     * @param snapshots  the branches to summarise
     */
    String buildNetworkSummary(String scopeLabel, List<BranchSnapshot> snapshots) {
        java.util.Map<String, java.util.List<String>> byOperator = new java.util.LinkedHashMap<>();
        java.util.List<String> unmapped = new java.util.ArrayList<>();
        java.util.List<String> offline = new java.util.ArrayList<>();

        for (BranchSnapshot snap : snapshots) {
            String operator = resolveOperator(snap.getRawData());
            Boolean networkUp = support.resolveBoolean(snap.getRawData(), "NETWORK", "gatewayStatus_NETWORK");

            if (Boolean.FALSE.equals(networkUp)) {
                offline.add(support.branchName(snap));
            }
            if (operator == null || operator.isBlank()) {
                unmapped.add(support.branchName(snap));
            } else {
                byOperator.computeIfAbsent(operator, k -> new java.util.ArrayList<>())
                        .add(support.branchName(snap));
            }
        }

        int total = snapshots.size();
        StringBuilder sb = new StringBuilder();
        sb.append("### 🌐 Network Status — ").append(scopeLabel).append("\n\n");
        sb.append("| Metric | Count |\n|--------|-------|\n");
        sb.append("| Total Devices | ").append(total).append(" |\n");
        sb.append("| 🔴 Network Offline | ").append(offline.size()).append(" |\n");
        sb.append("| ⚠️ Operator Unmapped | ").append(unmapped.size()).append(" |\n\n");

        if (!byOperator.isEmpty()) {
            sb.append("**Network Operator Distribution:**\n\n");
            sb.append("| Operator | Branches |\n|----------|----------|\n");
            byOperator.forEach((op, branches) ->
                    sb.append("| ").append(op).append(" | ").append(branches.size()).append(" |\n"));
            sb.append("\n");
        }

        if (!offline.isEmpty()) {
            sb.append("**Branches with Network Offline (").append(offline.size()).append("):**\n");
            for (String name : offline) {
                sb.append("- ").append(name).append("\n");
            }
        }
        if (!unmapped.isEmpty() && unmapped.size() <= 30) {
            sb.append("\n**Branches with Unmapped Operator (").append(unmapped.size()).append("):**\n");
            for (String name : unmapped) {
                sb.append("- ").append(name).append("\n");
            }
        }
        return sb.toString();
    }
}

