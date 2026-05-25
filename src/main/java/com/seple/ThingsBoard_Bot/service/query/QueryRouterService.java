package com.seple.ThingsBoard_Bot.service.query;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRouterService {

    private final RedisQueryService redisQueryService;
    private final NodeNameResolver nodeNameResolver;

    public enum QueryComplexity {
        SIMPLE_REDIS,
        COMPLEX_LLM
    }

    /**
     * Classifies a user query as SIMPLE_REDIS or COMPLEX_LLM.
     */
    public QueryComplexity classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryComplexity.COMPLEX_LLM;
        }
        String clean = question.toLowerCase().trim();

        // 1. Check for global/general count query
        boolean hasHowMany = clean.contains("how many") || clean.contains("count");
        boolean hasListOrShowAll = (clean.contains("list") || clean.contains("show")) && (clean.contains("all") || clean.contains("branch"));
        boolean hasStatusWord = clean.contains("online") || clean.contains("offline") || clean.contains("active") 
                || clean.contains("inactive") || clean.contains("connected") || clean.contains("disconnected");

        if ((hasHowMany || hasListOrShowAll) && hasStatusWord) {
            return QueryComplexity.SIMPLE_REDIS;
        }

        // 2. Patterns for intermediate node/region/zone queries
        if (clean.matches(".*\\bin (zone|region|fgmo|zo|ro|lho|rbo|co|hq|head office|office)\\b.*")) {
            return QueryComplexity.SIMPLE_REDIS;
        }

        // 3. Patterns for simple branch status (must start with "status of" or "show status of")
        if (clean.startsWith("status of") || clean.startsWith("show status of")) {
            return QueryComplexity.SIMPLE_REDIS;
        }

        return QueryComplexity.COMPLEX_LLM;
    }

    /**
     * Routes the query to direct Redis lookups and renders a template response.
     * Returns null if it cannot be answered deterministically from Redis.
     */
    public String routeAndAnswerSimple(String customerId, String question) {
        String clean = question.toLowerCase().trim();
        log.info("[ROUTER] Routing simple query for customer: '{}', question: '{}'", customerId, question);

        // 1. Check if the question references an intermediate node (Zone/Region/FGMO/etc.)
        Optional<HierarchyNode> nodeOpt = nodeNameResolver.resolveNodeInQuestion(customerId, question);
        if (nodeOpt.isPresent()) {
            HierarchyNode node = nodeOpt.get();
            log.info("[ROUTER] Resolved intermediate node: {} ({})", node.getDisplayName(), node.getNodeType());
            
            if (clean.contains("offline") || clean.contains("inactive") || clean.contains("disconnected")) {
                long count = redisQueryService.getNodeCounter(customerId, node.getNodeId(), "total_offline");
                return String.format("**%d branches are currently OFFLINE in %s (%s).**", count, node.getDisplayName(), node.getNodeType());
            } else if (clean.contains("online") || clean.contains("active") || clean.contains("connected")) {
                long count = redisQueryService.getNodeCounter(customerId, node.getNodeId(), "total_online");
                return String.format("**%d branches are currently ONLINE in %s (%s).**", count, node.getDisplayName(), node.getNodeType());
            } else {
                long total = redisQueryService.getNodeCounter(customerId, node.getNodeId(), "total_branches");
                long online = redisQueryService.getNodeCounter(customerId, node.getNodeId(), "total_online");
                long offline = redisQueryService.getNodeCounter(customerId, node.getNodeId(), "total_offline");
                return String.format("**Hierarchy Node %s (%s):**\n- Total Branches: %d\n- Online: %d\n- Offline: %d", 
                        node.getDisplayName(), node.getNodeType(), total, online, offline);
            }
        }

        // 2. Global counts
        if (clean.contains("how many") || clean.contains("list") || clean.contains("show")) {
            if (clean.contains("offline") || clean.contains("inactive") || clean.contains("disconnected")) {
                long count = redisQueryService.getGlobalCounter(customerId, "total_offline");
                return String.format("**%d branches are currently OFFLINE across all branches.**", count);
            } else if (clean.contains("online") || clean.contains("active") || clean.contains("connected")) {
                long count = redisQueryService.getGlobalCounter(customerId, "total_online");
                return String.format("**%d branches are currently ONLINE across all branches.**", count);
            }
        }

        log.warn("[ROUTER] Query fell through simple classification: '{}'", question);
        return null;
    }
}
