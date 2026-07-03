package com.seple.ThingsBoard_Bot.support;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.dto.ChatMessage;
import com.seple.ThingsBoard_Bot.service.query.extract.ExtractionResult;
import com.seple.ThingsBoard_Bot.service.query.extract.ExtractionResultParser;
import com.seple.ThingsBoard_Bot.service.query.extract.IntentExtractor;

/**
 * Fixture-replay {@link IntentExtractor} for the regression bench: answers from
 * {@code fixtures/extractor_recordings.json} (question -> recorded extractor JSON), parsed
 * through the same fail-closed parser as production. Deterministic, offline, zero tokens.
 * Questions with no recording return an empty result, mirroring production fallback.
 */
public final class RecordedIntentExtractor implements IntentExtractor {

    private final Map<String, JsonNode> recordings;
    private final ExtractionResultParser parser = new ExtractionResultParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecordedIntentExtractor() {
        this("fixtures/extractor_recordings.json");
    }

    public RecordedIntentExtractor(String fixturePath) {
        try {
            JsonNode root = objectMapper.readTree(FixtureLoader.load(fixturePath));
            Map<String, JsonNode> map = new java.util.LinkedHashMap<>();
            root.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue()));
            this.recordings = map;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load extractor recordings: " + fixturePath, e);
        }
    }

    @Override
    public ExtractionResult extract(String question, List<ChatMessage> history) {
        JsonNode recorded = recordings.get(question);
        if (recorded == null) {
            return ExtractionResult.empty();
        }
        return parser.parse(recorded.toString());
    }
}
