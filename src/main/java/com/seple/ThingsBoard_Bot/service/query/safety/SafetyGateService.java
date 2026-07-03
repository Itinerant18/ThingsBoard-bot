package com.seple.ThingsBoard_Bot.service.query.safety;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 3, Task 3.3 - cheap pre-LLM safety gate. Runs on every question BEFORE the router,
 * resolver, extractor, or any LLM call, so injection attempts and garbage input are stopped
 * at zero token cost. This is the first of three independent layers (pre-gate -> extractor
 * REFUSAL classification -> system-prompt injection guard on the generation call).
 *
 * <p>False-positive discipline: only strong markers block. Operational phrasings that share
 * words with attacks ("how do I ignore a false alarm", "act on this alert", "what is the
 * system prompt response time") must pass - covered by tests.
 */
@Slf4j
@Service
public class SafetyGateService {

    public enum Outcome {
        CLEAN, INJECTION, GARBAGE
    }

    public record GateResult(Outcome outcome, String reply) {
        static final GateResult CLEAN_RESULT = new GateResult(Outcome.CLEAN, null);
    }

    private static final String INJECTION_REPLY =
            "I can only help with questions about your branch monitoring data. "
                    + "I can't follow instructions embedded in questions.";

    private static final String GARBAGE_REPLY =
            "I didn't catch that. Try asking about a branch - for example: "
                    + "\"What is the battery voltage at Malda Town?\" or \"Which branches are offline?\"";

    /** Strong injection markers only - each pattern is a deliberate manipulation phrasing. */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above|earlier)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("disregard\\s+(your|the|all)\\s+(instructions?|rules?|prompts?)"),
            Pattern.compile("forget\\s+(everything|all previous|your instructions?)"),
            Pattern.compile("(reveal|print|show|display|output|dump|leak|repeat|share)\\b.{0,30}\\bsystem prompt"),
            Pattern.compile("your\\s+system\\s+prompt"),
            Pattern.compile("you\\s+are\\s+now\\s+(a|an|in)\\b"),
            Pattern.compile("pretend\\s+to\\s+be\\b"),
            Pattern.compile("act\\s+as\\s+(a|an)\\s+(?!technician|guard|banker)\\w+.{0,20}(mode|ai|assistant|model|bot)"),
            Pattern.compile("(developer|dan|god|jailbreak)\\s+mode"),
            Pattern.compile("<<<|end_user_question"),
            Pattern.compile("new\\s+instructions?\\s*:"),
            Pattern.compile("override\\s+(your|the)\\s+(rules?|instructions?|restrictions?)"));

    private final MeterRegistry meterRegistry;

    public SafetyGateService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public GateResult check(String question) {
        GateResult result = evaluate(question);
        meterRegistry.counter("safety.gate", "outcome", result.outcome().name().toLowerCase(Locale.ROOT))
                .increment();
        if (result.outcome() == Outcome.INJECTION) {
            log.warn("[SAFETY] Blocked injection attempt: '{}'",
                    question.length() > 200 ? question.substring(0, 200) + "..." : question);
        }
        return result;
    }

    private GateResult evaluate(String question) {
        if (question == null || question.isBlank()) {
            return new GateResult(Outcome.GARBAGE, GARBAGE_REPLY);
        }
        String lower = question.toLowerCase(Locale.ROOT);

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                return new GateResult(Outcome.INJECTION, INJECTION_REPLY);
            }
        }

        String alnum = lower.replaceAll("[^a-z0-9]", "");
        if (alnum.isEmpty()) {
            // Punctuation/emoji/symbol-only input.
            return new GateResult(Outcome.GARBAGE, GARBAGE_REPLY);
        }
        if (alnum.length() >= 5 && alnum.chars().distinct().count() == 1) {
            // Single repeated character ("aaaaaaa", "111111").
            return new GateResult(Outcome.GARBAGE, GARBAGE_REPLY);
        }
        if (!lower.trim().contains(" ") && alnum.length() >= 8 && !alnum.matches(".*\\d.*")
                && longestConsonantRun(alnum) >= 6) {
            // Single long letter token with an implausible consonant run - keyboard mash
            // ("asdfghjkl" has an 8-consonant run; real words top out around 5). Digits are
            // exempt so device ids and codes pass.
            return new GateResult(Outcome.GARBAGE, GARBAGE_REPLY);
        }

        return GateResult.CLEAN_RESULT;
    }

    private int longestConsonantRun(String alnum) {
        int longest = 0;
        int current = 0;
        for (char c : alnum.toCharArray()) {
            if (c >= 'a' && c <= 'z' && "aeiou".indexOf(c) < 0) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }
}
