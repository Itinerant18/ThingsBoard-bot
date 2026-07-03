package com.seple.ThingsBoard_Bot.service.query.extract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResponseFormat;

/**
 * Fail-closed parser for the extractor's JSON output. Guarantees, no matter what the model
 * returns:
 *
 * <ul>
 *   <li>malformed JSON / missing {@code intents} array - empty result (caller falls back)</li>
 *   <li>unknown intent or format enum value - that entry becomes {@code OUT_OF_SCOPE} at
 *       confidence 0 (missing/null format is fine: it means "handler default")</li>
 *   <li>entities: non-string and blank values dropped, missing array treated as empty</li>
 *   <li>confidence: non-numeric treated as 0, clamped to [0, 1]</li>
 *   <li>more than {@value #MAX_INTENTS} intents - top {@value #MAX_INTENTS} by confidence,
 *       truncation logged</li>
 * </ul>
 *
 * This class never throws.
 */
@Component
public class ExtractionResultParser {

    public static final int MAX_INTENTS = 3;

    private static final Logger log = LoggerFactory.getLogger(ExtractionResultParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExtractionResult parse(String json) {
        if (json == null || json.isBlank()) {
            return ExtractionResult.empty();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Extractor returned malformed JSON - falling back. {}", e.getMessage());
            return ExtractionResult.empty();
        }

        JsonNode intentsNode = root.path("intents");
        if (!intentsNode.isArray() || intentsNode.isEmpty()) {
            return ExtractionResult.empty();
        }

        List<ExtractedIntent> intents = new ArrayList<>();
        for (JsonNode entry : intentsNode) {
            intents.add(parseEntry(entry));
        }

        if (intents.size() > MAX_INTENTS) {
            log.warn("Extractor produced {} intents - keeping top {} by confidence", intents.size(), MAX_INTENTS);
            intents.sort(Comparator.comparingDouble(ExtractedIntent::confidence).reversed());
            intents = new ArrayList<>(intents.subList(0, MAX_INTENTS));
        }
        return new ExtractionResult(List.copyOf(intents));
    }

    private ExtractedIntent parseEntry(JsonNode entry) {
        List<String> entities = parseEntities(entry.path("entities"));
        double confidence = clamp(entry.path("confidence").asDouble(0.0));

        QueryIntent intent = parseEnum(QueryIntent.class, entry.path("intent"));
        if (intent == null) {
            log.warn("Extractor emitted unknown intent '{}' - coercing to OUT_OF_SCOPE", entry.path("intent").asText());
            return new ExtractedIntent(QueryIntent.OUT_OF_SCOPE, entities, null, 0.0);
        }

        JsonNode formatNode = entry.path("format");
        ResponseFormat format = null;
        if (!formatNode.isMissingNode() && !formatNode.isNull()) {
            format = parseEnum(ResponseFormat.class, formatNode);
            if (format == null) {
                log.warn("Extractor emitted unknown format '{}' - coercing entry to OUT_OF_SCOPE",
                        formatNode.asText());
                return new ExtractedIntent(QueryIntent.OUT_OF_SCOPE, entities, null, 0.0);
            }
        }

        return new ExtractedIntent(intent, entities, format, confidence);
    }

    private List<String> parseEntities(JsonNode node) {
        List<String> entities = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode e : node) {
                if (e.isTextual() && !e.asText().isBlank()) {
                    entities.add(e.asText().trim());
                }
            }
        }
        return List.copyOf(entities);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        try {
            return Enum.valueOf(type, node.asText().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
