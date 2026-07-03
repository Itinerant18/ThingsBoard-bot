package com.seple.ThingsBoard_Bot.service.query.resolve;

import java.util.Set;

/**
 * One canonical branch in the local dictionary, with every name variant it can be
 * matched by (exact name, normalized, compact, shorthand codes, historical names).
 *
 * @param technicalId canonical branch id (e.g. {@code BOI-MALDATOWN})
 * @param displayName human readable name (e.g. {@code BRANCH MALDA TOWN})
 * @param customerId  owning customer - fuzzy matching never crosses customers
 * @param variants    all normalized/compact alias variants used for lookup and scoring
 */
public record BranchEntry(String technicalId, String displayName, String customerId, Set<String> variants) {
}
