package com.seple.ThingsBoard_Bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Phase 2 LLM intent-extractor settings ({@code iotchatbot.extractor.*}).
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code off} (default) - pipeline behaves exactly as before Phase 2</li>
 *   <li>{@code shadow} - extractor runs async after normal resolution; result is only
 *       logged and counted against the deterministic resolver's outcome (zero user impact,
 *       produces the calibration data for cutover)</li>
 *   <li>{@code active} - extractor drives the GENERAL_LLM/ambiguous branch</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "iotchatbot.extractor")
public class ExtractorConfig {

    public enum Mode {
        OFF, SHADOW, ACTIVE
    }

    private Mode mode = Mode.OFF;

    /** How many recent conversation turns are passed to the extractor for follow-up context. */
    private int historyTurns = 4;

    /** Intents below this extractor confidence are declined instead of executed (active mode). */
    private double confidenceGate = 0.60;
}
