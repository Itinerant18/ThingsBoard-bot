package com.seple.ThingsBoard_Bot.service.query.extract;

import java.util.List;

import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResponseFormat;

/**
 * One intent extracted from a user question by the LLM extractor (Phase 2, Task 2.1).
 *
 * @param intent     validated {@link QueryIntent}; invalid extractor output is coerced to
 *                   {@code OUT_OF_SCOPE} at confidence 0 by the parser (fail-closed)
 * @param entities   raw entity strings copied verbatim from the question - spelling
 *                   correction is owned by the fuzzy resolver, not the extractor
 * @param format     requested rendering, or null to use the handler default
 * @param confidence extractor's self-reported confidence; treated as an ordinal signal
 *                   for gating, NOT a calibrated probability
 */
public record ExtractedIntent(QueryIntent intent, List<String> entities, ResponseFormat format, double confidence) {
}
