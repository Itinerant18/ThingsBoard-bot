package com.seple.ThingsBoard_Bot.service.query.extract;

import java.util.List;

import com.seple.ThingsBoard_Bot.model.dto.ChatMessage;

/**
 * Extracts structured intents from a natural-language question (Phase 2). Implementations:
 * an LLM-backed extractor for production and a fixture-replay extractor for the regression
 * bench, so bench runs are deterministic and cost nothing.
 */
public interface IntentExtractor {

    /**
     * @param question the user's raw question
     * @param history  recent conversation turns (may be empty) so follow-ups like
     *                 "and Bhubaneswar?" can inherit the prior intent
     * @return validated extraction; {@link ExtractionResult#empty()} on any failure
     */
    ExtractionResult extract(String question, List<ChatMessage> history);
}
