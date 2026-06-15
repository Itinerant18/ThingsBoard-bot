package com.seple.ThingsBoard_Bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration for the LLM-judge response evaluation pipeline (QA guardrail).
 *
 * <p>Disabled by default — when enabled it issues a SECOND OpenAI call per LLM-backed answer that
 * audits the answer against the source-of-truth context for hallucinations, tenant leaks, and
 * formatting violations. The evaluation runs asynchronously and only logs/meters findings; it never
 * blocks, alters, or delays the user's answer.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "iotchatbot.evaluation")
public class EvaluationConfig {

    /** Master switch. Off by default to avoid the extra LLM cost/latency in normal operation. */
    private boolean enabled = false;

    /** Fraction of LLM answers to evaluate (0.0–1.0). 1.0 = every answer; lower to cap judge cost. */
    private double sampleRate = 1.0;
}
