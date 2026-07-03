package com.seple.ThingsBoard_Bot.service.query.resolve;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;

/**
 * Task 1.4 - Manual overrides map for common abbreviations (e.g. BBSR -> Bhubaneswar).
 * Loaded once from {@code branch-aliases.properties} so operators can extend it without
 * recompiling. Both sides are normalized through {@link BranchAliasIndex} so they line up
 * with dictionary variants.
 */
@Component
public class ManualAliasTable {

    private static final Logger log = LoggerFactory.getLogger(ManualAliasTable.class);
    private static final String RESOURCE = "branch-aliases.properties";

    private final Map<String, String> shorthandToCanonical;

    public ManualAliasTable(BranchAliasIndex aliasIndex) {
        Map<String, String> mappings = new LinkedHashMap<>();
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            } else {
                log.warn("{} not found on classpath - manual alias table is empty", RESOURCE);
            }
        } catch (IOException e) {
            log.warn("Failed to load {} - manual alias table is empty", RESOURCE, e);
        }
        for (String key : properties.stringPropertyNames()) {
            String shorthand = aliasIndex.normalize(key);
            String canonical = aliasIndex.normalize(properties.getProperty(key));
            if (!shorthand.isBlank() && !canonical.isBlank()) {
                mappings.put(shorthand, canonical);
            }
        }
        this.shorthandToCanonical = Map.copyOf(mappings);
        log.info("Manual alias table loaded: {} entries", shorthandToCanonical.size());
    }

    /** Normalized shorthand -> normalized canonical name mappings. */
    public Map<String, String> mappings() {
        return shorthandToCanonical;
    }

    /**
     * Resolves a shorthand to its canonical normalized name, or returns the input
     * (normalized) unchanged when no override exists.
     */
    public String canonicalize(String rawName, BranchAliasIndex aliasIndex) {
        String normalized = aliasIndex.normalize(rawName);
        return shorthandToCanonical.getOrDefault(normalized, normalized);
    }
}
