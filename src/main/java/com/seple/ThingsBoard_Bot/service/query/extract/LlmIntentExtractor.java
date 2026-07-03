package com.seple.ThingsBoard_Bot.service.query.extract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.client.OpenAIClient;
import com.seple.ThingsBoard_Bot.config.ExtractorConfig;
import com.seple.ThingsBoard_Bot.model.dto.ChatMessage;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Production {@link IntentExtractor}: one deterministic JSON-mode OpenAI call (temperature 0)
 * with the extractor system prompt and the last N conversation turns, parsed fail-closed.
 * Any failure - API error, malformed output, timeout - returns an empty result so the caller
 * falls back to the pre-Phase-2 behavior.
 */
@Slf4j
@Service
public class LlmIntentExtractor implements IntentExtractor {

    /** The system prompt carries all instructions; the user turn is just the question. */
    private static final String QUESTION_PREFIX = "Extract the intents from this question:\n";

    private final OpenAIClient openAIClient;
    private final ExtractionResultParser parser;
    private final ExtractorConfig config;
    private final MeterRegistry meterRegistry;
    private final Timer latencyTimer;
    private final String systemPrompt;

    public LlmIntentExtractor(OpenAIClient openAIClient, ExtractionResultParser parser, ExtractorConfig config,
            MeterRegistry meterRegistry,
            @Value("classpath:prompts/intent-extractor-prompt.txt") Resource promptResource) {
        this.openAIClient = openAIClient;
        this.parser = parser;
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.latencyTimer = Timer.builder("extractor.latency")
                .description("LLM intent-extractor call latency")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.systemPrompt = load(promptResource);
    }

    @Override
    public ExtractionResult extract(String question, List<ChatMessage> history) {
        if (question == null || question.isBlank()) {
            return ExtractionResult.empty();
        }
        try {
            long start = System.currentTimeMillis();
            String json = openAIClient.completeJson(systemPrompt, trimHistory(history),
                    QUESTION_PREFIX + question);
            latencyTimer.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);

            if (json == null) {
                meterRegistry.counter("extractor.result", "outcome", "error").increment();
                return ExtractionResult.empty();
            }
            ExtractionResult result = parser.parse(json);
            meterRegistry.counter("extractor.result", "outcome", result.isEmpty() ? "empty" : "ok").increment();
            return result;
        } catch (Exception e) {
            // Belt and braces: completeJson/parse are already non-throwing, but the extractor
            // must never propagate anything into the chat pipeline.
            meterRegistry.counter("extractor.result", "outcome", "error").increment();
            log.warn("Intent extraction failed - falling back. {}", e.getMessage());
            return ExtractionResult.empty();
        }
    }

    private List<ChatMessage> trimHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int turns = Math.max(0, config.getHistoryTurns());
        return history.size() <= turns ? history : history.subList(history.size() - turns, history.size());
    }

    private String load(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load classpath:prompts/intent-extractor-prompt.txt", e);
        }
    }
}
