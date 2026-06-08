package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * Handles one or more {@link QueryIntent}s, producing the deterministic answer string for a
 * resolved query. Implementations are discovered as Spring beans and dispatched by
 * {@code DeterministicAnswerService}. Returning {@code null} means "cannot answer
 * deterministically" (the caller then falls back to the LLM path), preserving the original
 * switch behavior where unmatched/branch-less intents yielded {@code null}.
 */
public interface AnswerHandler {

    /** Whether this handler can produce an answer for the given intent. */
    boolean supports(QueryIntent intent);

    /**
     * Produces the answer, or {@code null} if it cannot be answered deterministically.
     *
     * @param query      the resolved query (intent, target branch, target system, etc.)
     * @param snapshots  all branch snapshots for the customer (used by global intents)
     * @param customerId the customer id
     */
    String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId);
}
