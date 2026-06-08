package com.seple.ThingsBoard_Bot.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a project-root {@code .env} file into the Spring {@link ConfigurableEnvironment} so that
 * {@code ${VAR}} placeholders in {@code application-dev.properties} resolve during local runs —
 * without committing real secrets to any tracked file.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Self-contained (no third-party dotenv dependency) and registered via Boot's
 *       {@code EnvironmentPostProcessor.imports} so it is guaranteed to run under Spring Boot 4.</li>
 *   <li>The {@code .env} source is added with {@code addLast}, i.e. <em>lowest</em> precedence.
 *       Real OS environment variables, system properties, and profile property files all win over
 *       it. This makes production (where {@code .env} is absent and real env vars are set) behave
 *       exactly as before.</li>
 *   <li>No-ops silently when {@code .env} is missing (the normal case in CI and production).</li>
 * </ul>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvFile";
    private static final String DOTENV_FILE = ".env";

    private final Log log;

    public DotenvEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DotenvEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envPath = Path.of(DOTENV_FILE);
        if (!Files.exists(envPath)) {
            return;
        }
        try {
            Map<String, Object> values = parseLines(Files.readAllLines(envPath, StandardCharsets.UTF_8));
            if (!values.isEmpty()) {
                environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
                log.info("Loaded " + values.size() + " entries from .env (lowest precedence; OS env overrides)");
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file: " + e.getMessage());
        }
    }

    /** Parses {@code KEY=VALUE} lines, ignoring blanks and {@code #} comments. Package-private for tests. */
    static Map<String, Object> parseLines(List<String> lines) {
        Map<String, Object> values = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = stripQuotes(line.substring(eq + 1).strip());
            values.put(key, value);
        }
        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
