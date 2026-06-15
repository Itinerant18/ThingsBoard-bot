package com.seple.ThingsBoard_Bot.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.client.OpenAIClient;
import com.seple.ThingsBoard_Bot.config.EvaluationConfig;
import com.seple.ThingsBoard_Bot.model.dto.EvaluationInput;
import com.seple.ThingsBoard_Bot.model.dto.EvaluationReport;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM-judge QA guardrail (built from {@code prompts/evaluation-prompt.txt}).
 *
 * <p>After an LLM-backed answer is produced, this service can re-submit the answer + its
 * source-of-truth context to a second, deterministic OpenAI call that audits the answer for
 * hallucinations, cross-tenant leaks, header/format violations, and intent drift. The verdict is a
 * structured {@link EvaluationReport}.
 *
 * <p><b>Non-intrusive by design:</b> {@link #evaluateAsync} runs off the request thread on a bounded
 * pool and only LOGS findings + emits the {@code chat.evaluation} metric. It never blocks, mutates,
 * or delays the user's answer, and it swallows all judge failures. Disabled by default
 * ({@code iotchatbot.evaluation.enabled=false}).
 */
@Slf4j
@Service
public class ResponseEvaluationService {

    /** Minimal user turn — the filled template (system turn) carries all the instructions + data. */
    private static final String JUDGE_USER_TRIGGER = "Produce the JSON evaluation report now.";

    private final OpenAIClient openAIClient;
    private final EvaluationConfig config;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;
    private final ExecutorService executor;

    public ResponseEvaluationService(OpenAIClient openAIClient, EvaluationConfig config,
            MeterRegistry meterRegistry,
            @Value("classpath:prompts/evaluation-prompt.txt") Resource promptResource) {
        this.openAIClient = openAIClient;
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.promptTemplate = load(promptResource);
        // Small bounded pool: judging is best-effort. If it backs up, drop rather than queue forever.
        this.executor = new ThreadPoolExecutor(1, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                r -> {
                    Thread t = new Thread(r, "response-eval");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Fire-and-forget evaluation: runs off the request thread, logs + counts the verdict, and never
     * throws back into the caller. No-op when disabled or sampled out.
     */
    public void evaluateAsync(EvaluationInput input) {
        if (!config.isEnabled() || input == null) {
            return;
        }
        if (config.getSampleRate() < 1.0
                && ThreadLocalRandom.current().nextDouble() > config.getSampleRate()) {
            return; // sampled out
        }
        try {
            executor.execute(() -> {
                try {
                    handleReport(input, evaluate(input));
                } catch (Exception e) {
                    log.warn("[EVAL] Evaluation task failed: {}", e.getMessage());
                }
            });
        } catch (RejectedExecutionException rejected) {
            meterRegistry.counter("chat.evaluation", "result", "rejected").increment();
            log.debug("[EVAL] Evaluation queue saturated — skipping this turn.");
        }
    }

    /**
     * Synchronous evaluation. Fills the template, calls the judge, and parses the verdict.
     *
     * @return the parsed report, or {@code null} if the judge produced no usable JSON.
     */
    public EvaluationReport evaluate(EvaluationInput input) {
        String prompt = fillTemplate(input);
        String raw = openAIClient.evaluateJson(prompt, JUDGE_USER_TRIGGER);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseReport(raw);
    }

    private void handleReport(EvaluationInput input, EvaluationReport report) {
        if (report == null) {
            meterRegistry.counter("chat.evaluation", "result", "unparseable").increment();
            return;
        }

        String severity = report.getSeverity() == null ? "UNKNOWN" : report.getSeverity().toUpperCase();
        meterRegistry.counter("chat.evaluation",
                "severity", severity,
                "problem", String.valueOf(report.isProblemIdentified())).increment();

        if (!report.isProblemIdentified() || "NONE".equals(severity)) {
            log.debug("[EVAL] OK — no issues for branch '{}' intent '{}'",
                    input.getTargetBranch(), input.getIntent());
            return;
        }

        int issueCount = report.getIssues() == null ? 0 : report.getIssues().size();
        if ("CRITICAL".equals(severity) || "MAJOR".equals(severity)) {
            log.warn("[EVAL] {} issue(s) [{}] for branch '{}' intent '{}': {}",
                    issueCount, severity, input.getTargetBranch(), input.getIntent(), summarize(report));
        } else {
            log.info("[EVAL] {} issue(s) [{}] for branch '{}' intent '{}'",
                    issueCount, severity, input.getTargetBranch(), input.getIntent());
        }
    }

    private String summarize(EvaluationReport report) {
        if (report.getIssues() == null) {
            return "";
        }
        return report.getIssues().stream()
                .map(i -> i.getCategory() + ": " + i.getDescription())
                .collect(Collectors.joining(" | "));
    }

    /** Substitutes the {@code [PLACEHOLDER]} tokens in the template with the actual answer inputs. */
    String fillTemplate(EvaluationInput input) {
        return promptTemplate
                .replace("[CUSTOMER_PREFIX]", na(input.getCustomerPrefix()))
                .replace("[ACTIVE_BRANCH_ID]", na(input.getActiveBranchId()))
                .replace("[USER_QUESTION]", blank(input.getUserQuestion()))
                .replace("[STRUCTURED_CONTEXT_JSON]", blank(input.getStructuredContextJson()))
                .replace("[METADATA_INTENT]", na(input.getIntent()))
                .replace("[METADATA_BRANCH]", na(input.getTargetBranch()))
                .replace("[METADATA_SYSTEM]", na(input.getTargetSystem()))
                .replace("[IS_GLOBAL]", String.valueOf(input.isGlobal()))
                .replace("[EXECUTION_PATH]", na(input.getExecutionPath()))
                .replace("[BOT_ANSWER]", blank(input.getBotAnswer()));
    }

    /** Parses the judge's response into a report, tolerating markdown fences / surrounding prose. */
    EvaluationReport parseReport(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) {
            log.warn("[EVAL] Judge output contained no JSON object: {}", truncate(raw));
            return null;
        }
        try {
            return objectMapper.readValue(json, EvaluationReport.class);
        } catch (Exception e) {
            log.warn("[EVAL] Failed to parse judge JSON: {} | raw: {}", e.getMessage(), truncate(json));
            return null;
        }
    }

    /** Takes the substring from the first '{' to the last '}', stripping fences/preamble. */
    private static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private static String na(String v) {
        return (v == null || v.isBlank()) ? "N/A" : v;
    }

    private static String blank(String v) {
        return v == null ? "" : v;
    }

    private static String truncate(String v) {
        return v.length() <= 500 ? v : v.substring(0, 500) + "…";
    }

    private static String load(Resource resource) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load classpath:prompts/evaluation-prompt.txt", e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
