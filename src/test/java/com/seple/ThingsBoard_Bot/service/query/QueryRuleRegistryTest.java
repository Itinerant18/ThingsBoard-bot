package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.service.query.QueryRouterService.QueryComplexity;

class QueryRuleRegistryTest {

    private final QueryRuleRegistry registry = new QueryRuleRegistry();

    private QueryRule rule(String name) {
        return registry.rules().stream()
                .filter(r -> r.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no rule named " + name));
    }

    @Test
    void rulesAreOrderedByAscendingPriority() {
        List<QueryRule> rules = registry.rules();
        for (int i = 1; i < rules.size(); i++) {
            assertTrue(rules.get(i - 1).priority() <= rules.get(i).priority(),
                    "rules must be sorted by ascending priority");
        }
    }

    @Test
    void globalCountWithStatusRuleMatches() {
        QueryRule r = rule("global-count-with-status");
        assertEquals(QueryComplexity.SIMPLE_REDIS, r.complexity());
        assertTrue(r.appliesTo("how many online"));
        assertTrue(r.appliesTo("list offline branches"));
        assertTrue(r.appliesTo("how many connected devices"));
        assertFalse(r.appliesTo("how many devices"), "no status word -> no match");
        assertFalse(r.appliesTo("why is the voltage low"));
    }

    @Test
    void intermediateNodeRuleMatches() {
        QueryRule r = rule("intermediate-node");
        assertEquals(QueryComplexity.SIMPLE_REDIS, r.complexity());
        assertTrue(r.appliesTo("status in zone kolkata"));
        assertTrue(r.appliesTo("how many online in region north"));
        assertFalse(r.appliesTo("status of branch xyz"));
    }

    @Test
    void branchStatusPrefixRuleMatches() {
        QueryRule r = rule("branch-status-prefix");
        assertEquals(QueryComplexity.SIMPLE_REDIS, r.complexity());
        assertTrue(r.appliesTo("status of branch xyz"));
        assertTrue(r.appliesTo("show status of branch abc"));
        assertFalse(r.appliesTo("what is the status of everything"));
    }

    @Test
    void isGlobalCountWithStatusHelperMatchesExpectedCombos() {
        assertTrue(QueryRuleRegistry.isGlobalCountWithStatus("count of offline"));
        assertTrue(QueryRuleRegistry.isGlobalCountWithStatus("show all active"));
        assertFalse(QueryRuleRegistry.isGlobalCountWithStatus("show me the weather"));
    }
}
