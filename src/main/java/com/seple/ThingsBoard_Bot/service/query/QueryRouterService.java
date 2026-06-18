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
    private final QueryRuleRegistry ruleRegistry;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public enum QueryComplexity {
        SIMPLE_REDIS,
        COMPLEX_LLM
    }

    /**
     * Classifies a user query as SIMPLE_REDIS or COMPLEX_LLM by evaluating the declarative
     * {@link QueryRuleRegistry} rules in priority order (first match wins). Records the outcome
     * under metric {@code query.router{complexity}}.
     */
    public QueryComplexity classify(String question) {
        QueryComplexity result = classifyInternal(question);
        meterRegistry.counter("query.router", "complexity", result.name().toLowerCase()).increment();
        return result;
    }

    private QueryComplexity classifyInternal(String question) {
        if (question == null || question.isBlank()) {
            return QueryComplexity.COMPLEX_LLM;
        }
        String clean = question.toLowerCase().trim();
        for (QueryRule rule : ruleRegistry.rules()) {
            if (rule.appliesTo(clean)) {
                return rule.complexity();
            }
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

        // 2. Global (non-node) counts are intentionally NOT answered here. The global total_online /
        // total_offline Redis counters are never populated by replay, so reading them returned 0 and
        // contradicted the global overview. Returning null lets the query fall through to the
        // snapshot-based GLOBAL_OVERVIEW handler, which counts live branch snapshots — keeping
        // "how many offline" consistent with "list all branches".
        log.warn("[ROUTER] Query fell through simple classification: '{}'", question);
        return null;
    }
}
