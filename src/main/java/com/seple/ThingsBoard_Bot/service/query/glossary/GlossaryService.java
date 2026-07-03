package com.seple.ThingsBoard_Bot.service.query.glossary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Phase 3, Task 3.2 - static domain glossary. Loaded once from {@code glossary.json}
 * (operator-editable, no recompile), indexed by normalized term and alias. There is no
 * generative path: a term is either in the file or the caller reports "no definition".
 */
@Slf4j
@Service
public class GlossaryService {

    public record GlossaryEntry(String term, String fullName, String definition, List<String> related) {
    }

    /** Normalized term/alias -> entry. Longest keys matched first in question scanning. */
    private final Map<String, GlossaryEntry> index;
    private final List<String> keysByLengthDesc;

    public GlossaryService(
            @org.springframework.beans.factory.annotation.Value("classpath:glossary.json") Resource resource) {
        Map<String, GlossaryEntry> map = new LinkedHashMap<>();
        try (var in = resource.getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);
            for (JsonNode node : root.path("terms")) {
                String term = node.path("term").asText("");
                String definition = node.path("definition").asText("");
                if (term.isBlank() || definition.isBlank()) {
                    continue;
                }
                String fullName = node.path("fullName").asText(null);
                List<String> related = new ArrayList<>();
                node.path("related").forEach(r -> related.add(r.asText()));
                GlossaryEntry entry = new GlossaryEntry(term, fullName, definition, List.copyOf(related));

                map.put(normalize(term), entry);
                if (fullName != null && !fullName.isBlank()) {
                    map.put(normalize(fullName), entry);
                }
                node.path("aliases").forEach(a -> {
                    String alias = normalize(a.asText());
                    if (!alias.isBlank()) {
                        map.put(alias, entry);
                    }
                });
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load classpath:glossary.json", e);
        }
        this.index = Map.copyOf(map);
        this.keysByLengthDesc = map.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
        log.info("Glossary loaded: {} entries ({} lookup keys)",
                map.values().stream().distinct().count(), map.size());
    }

    /** Exact (normalized) term or alias lookup. */
    public GlossaryEntry lookup(String term) {
        return term == null ? null : index.get(normalize(term));
    }

    /**
     * Scans the question for the longest known term or alias, matched on word boundaries so
     * "stale" doesn't fire inside "installed". Returns null when no glossary term appears.
     */
    public GlossaryEntry findTermInQuestion(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String normalized = normalize(question);
        for (String key : keysByLengthDesc) {
            if (Pattern.compile("(^|\\s)" + Pattern.quote(key) + "($|\\s)").matcher(normalized).find()) {
                return index.get(key);
            }
        }
        return null;
    }

    public boolean containsKnownTerm(String question) {
        return findTermInQuestion(question) != null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
