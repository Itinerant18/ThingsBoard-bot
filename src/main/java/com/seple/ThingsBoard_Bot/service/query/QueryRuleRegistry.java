package com.seple.ThingsBoard_Bot.service.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.service.query.QueryRouterService.QueryComplexity;

/**
 * Holds the ordered set of {@link QueryRule}s used by {@link QueryRouterService} to classify a
 * query as SIMPLE_REDIS or COMPLEX_LLM. Rules live here in code (rather than an if/else chain)
 * so they can be inspected, unit-tested individually, and extended without touching the router.
 *
 * <p>Behavior is identical to the previous inline if/else chain; only the structure changed.
 */
@Component
public class QueryRuleRegistry {

    /** Matches an intermediate node reference such as "... in zone X" / "... in region Y". */
    static final String NODE_REGEX =
            ".*\\bin (zone|region|fgmo|zo|ro|lho|rbo|co|hq|head office|office)\\b.*";

    private final List<QueryRule> rules;

    public QueryRuleRegistry() {
        List<QueryRule> r = new ArrayList<>();
        r.add(new QueryRule("global-count-with-status", 10,
                QueryRuleRegistry::isGlobalCountWithStatus, QueryComplexity.SIMPLE_REDIS));
        r.add(new QueryRule("intermediate-node", 20,
                clean -> clean.matches(NODE_REGEX), QueryComplexity.SIMPLE_REDIS));
        r.add(new QueryRule("branch-status-prefix", 30,
                clean -> clean.startsWith("status of") || clean.startsWith("show status of"),
                QueryComplexity.SIMPLE_REDIS));
        r.sort(Comparator.comparingInt(QueryRule::priority));
        this.rules = List.copyOf(r);
    }

    /** Rules in ascending priority order (first match wins). */
    public List<QueryRule> rules() {
        return rules;
    }

    /**
     * A global count/list query combined with a status word, e.g. "how many offline",
     * "list offline branches", "how many connected devices".
     */
    static boolean isGlobalCountWithStatus(String clean) {
        boolean hasHowMany = clean.contains("how many") || clean.contains("count");
        boolean hasListOrShowAll = (clean.contains("list") || clean.contains("show"))
                && (clean.contains("all") || clean.contains("branch"));
        boolean hasStatusWord = clean.contains("online") || clean.contains("offline")
                || clean.contains("active") || clean.contains("inactive")
                || clean.contains("connected") || clean.contains("disconnected");
        return (hasHowMany || hasListOrShowAll) && hasStatusWord;
    }
}
