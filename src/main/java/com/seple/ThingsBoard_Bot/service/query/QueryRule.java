package com.seple.ThingsBoard_Bot.service.query;

import java.util.function.Predicate;

import com.seple.ThingsBoard_Bot.service.query.QueryRouterService.QueryComplexity;

/**
 * A single declarative classification rule: if {@link #matches} accepts the cleaned (lowercased,
 * trimmed) question, the query is classified as {@link #complexity}. Rules are evaluated in
 * ascending {@link #priority} order; the first match wins.
 */
public record QueryRule(String name, int priority, Predicate<String> matches, QueryComplexity complexity) {

    /** @param cleanQuestion the lowercased, trimmed question */
    public boolean appliesTo(String cleanQuestion) {
        return matches.test(cleanQuestion);
    }
}
