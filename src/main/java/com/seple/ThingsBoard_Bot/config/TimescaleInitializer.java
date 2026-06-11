package com.seple.ThingsBoard_Bot.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies TimescaleDB setup (hypertable + retention + indexes) once the schema exists (audit #9).
 *
 * <p>Runs only when {@code iotchatbot.timescale.init-enabled=true} — production with a
 * TimescaleDB/PostgreSQL backend. Default off, so H2/local-dev and the test suite are untouched.
 * Each statement is applied best-effort: a failure (e.g. the timescaledb extension is unavailable)
 * is logged but does not abort startup, since the application still functions on a plain table.
 *
 * <p>NOT live-tested against a TimescaleDB instance in this change set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimescaleInitializer {

    private final DataSource dataSource;
    private final org.springframework.core.env.Environment env;

    @EventListener(ApplicationReadyEvent.class)
    public void initTimescale() {
        boolean enabled = Boolean.parseBoolean(env.getProperty("iotchatbot.timescale.init-enabled", "false"));
        if (!enabled) {
            log.debug("[TIMESCALE] init disabled (iotchatbot.timescale.init-enabled=false) — skipping.");
            return;
        }

        String script;
        try {
            script = StreamUtils.copyToString(
                    new ClassPathResource("db/timescale/timescale_setup.sql").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[TIMESCALE] Could not read timescale_setup.sql: {}", e.getMessage());
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            for (String statement : splitStatements(script)) {
                try (Statement st = conn.createStatement()) {
                    st.execute(statement);
                    log.info("[TIMESCALE] applied: {}", firstLine(statement));
                } catch (Exception e) {
                    log.warn("[TIMESCALE] statement failed (continuing): {} -> {}", firstLine(statement), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[TIMESCALE] init failed: {}", e.getMessage());
        }
    }

    /** Splits on semicolons, dropping blank statements and SQL line comments. */
    private static java.util.List<String> splitStatements(String script) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                out.add(sb.toString().trim());
                sb.setLength(0);
            }
        }
        if (!sb.toString().isBlank()) {
            out.add(sb.toString().trim());
        }
        return out;
    }

    private static String firstLine(String statement) {
        int nl = statement.indexOf('\n');
        return nl > 0 ? statement.substring(0, nl) : statement;
    }
}
