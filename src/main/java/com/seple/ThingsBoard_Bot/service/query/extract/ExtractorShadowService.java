package com.seple.ThingsBoard_Bot.service.query.extract;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.config.ExtractorConfig;
import com.seple.ThingsBoard_Bot.model.dto.ChatMessage;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 2 shadow mode: runs the LLM extractor asynchronously AFTER the deterministic
 * resolver has already answered, and only records whether the two agree. Zero user impact -
 * the outcome is a metric ({@code extractor.agreement}) and a log line. This produces the
 * calibration data that gates the flip to active mode (target: >=95% agreement on the
 * deterministic set, per the paraphrase release gate).
 */
@Slf4j
@Service
public class ExtractorShadowService {

    private final IntentExtractor intentExtractor;
    private final ExtractorConfig config;
    private final MeterRegistry meterRegistry;
    private final BranchAliasIndex aliasIndex;
    private final ExecutorService executor;

    public ExtractorShadowService(IntentExtractor intentExtractor, ExtractorConfig config,
            MeterRegistry meterRegistry, BranchAliasIndex aliasIndex) {
        this.intentExtractor = intentExtractor;
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.aliasIndex = aliasIndex;
        // Small bounded daemon pool: shadow comparison is best-effort. If it backs up,
        // drop rather than queue forever (same policy as the response-evaluation judge).
        this.executor = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(32),
                r -> {
                    Thread t = new Thread(r, "extractor-shadow");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Fire-and-forget shadow comparison. No-op unless mode is SHADOW. Never throws. */
    public void maybeShadow(String question, List<ChatMessage> history, ResolvedQuery resolved) {
        if (config.getMode() != ExtractorConfig.Mode.SHADOW || question == null || resolved == null) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    compare(question, history, resolved);
                } catch (Exception e) {
                    log.warn("[SHADOW] comparison failed: {}", e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            meterRegistry.counter("extractor.agreement", "outcome", "dropped").increment();
        }
    }

    /** Package-private so tests can run the comparison synchronously. */
    void compare(String question, List<ChatMessage> history, ResolvedQuery resolved) {
        ExtractionResult extraction = intentExtractor.extract(question, history);
        String outcome = classify(extraction, resolved);
        meterRegistry.counter("extractor.agreement", "outcome", outcome).increment();

        ExtractedIntent top = extraction.isEmpty() ? null : topByConfidence(extraction);
        log.info("[SHADOW] outcome={} resolver={} extractor={} conf={} q='{}'",
                outcome,
                resolved.getIntent(),
                top != null ? top.intent() : "-",
                top != null ? top.confidence() : "-",
                question.length() > 120 ? question.substring(0, 120) + "..." : question);
    }

    private String classify(ExtractionResult extraction, ResolvedQuery resolved) {
        if (extraction.isEmpty()) {
            return "empty";
        }
        ExtractedIntent top = topByConfidence(extraction);
        if (top.intent() != resolved.getIntent()) {
            return "intent_mismatch";
        }
        return entitiesAgree(top, resolved) ? "match" : "entity_mismatch";
    }

    /**
     * Entity agreement: when the resolver found a branch, at least one extracted entity must
     * normalize onto that branch's technical id or display name. When it found none, the
     * extractor must also have produced no entities.
     */
    private boolean entitiesAgree(ExtractedIntent top, ResolvedQuery resolved) {
        if (resolved.getTargetBranch() == null || resolved.getTargetBranch().getIdentity() == null) {
            return top.entities().isEmpty();
        }
        String technicalId = aliasIndex.compact(resolved.getTargetBranch().getIdentity().getTechnicalId());
        String branchName = aliasIndex.compact(resolved.getTargetBranch().getIdentity().getBranchName());
        for (String entity : top.entities()) {
            String compact = aliasIndex.compact(entity);
            if (compact.isBlank()) {
                continue;
            }
            if (technicalId.contains(compact) || compact.contains(technicalId)
                    || branchName.contains(compact) || compact.contains(branchName)) {
                return true;
            }
        }
        return false;
    }

    private ExtractedIntent topByConfidence(ExtractionResult extraction) {
        ExtractedIntent top = extraction.intents().get(0);
        for (ExtractedIntent candidate : extraction.intents()) {
            if (candidate.confidence() > top.confidence()) {
                top = candidate;
            }
        }
        return top;
    }
}
