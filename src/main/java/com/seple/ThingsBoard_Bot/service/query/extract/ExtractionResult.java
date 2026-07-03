package com.seple.ThingsBoard_Bot.service.query.extract;

import java.util.List;

/**
 * Validated output of the LLM intent extractor. Empty means "extraction failed or produced
 * nothing usable" - the caller falls back to the existing LLM answer path, so a broken
 * extractor can never take the bot down.
 */
public record ExtractionResult(List<ExtractedIntent> intents) {

    public static ExtractionResult empty() {
        return new ExtractionResult(List.of());
    }

    public boolean isEmpty() {
        return intents == null || intents.isEmpty();
    }
}
