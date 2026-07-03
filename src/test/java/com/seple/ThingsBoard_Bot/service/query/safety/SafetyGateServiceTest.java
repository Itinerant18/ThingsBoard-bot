package com.seple.ThingsBoard_Bot.service.query.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.service.query.safety.SafetyGateService.Outcome;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class SafetyGateServiceTest {

    private final SafetyGateService gate = new SafetyGateService(new SimpleMeterRegistry());

    /**
     * Corpora live in {@code fixtures/bench_safety_corpus.json} - single source shared with
     * the Phase 4 bench runner, so the release scorecard and this unit test can never drift.
     */
    private static final com.fasterxml.jackson.databind.JsonNode CORPUS = loadCorpus();

    private static com.fasterxml.jackson.databind.JsonNode loadCorpus() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                    com.seple.ThingsBoard_Bot.support.FixtureLoader.load("fixtures/bench_safety_corpus.json"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load bench_safety_corpus.json", e);
        }
    }

    private static List<String> corpus(String key) {
        List<String> values = new java.util.ArrayList<>();
        CORPUS.path(key).forEach(n -> values.add(n.asText()));
        return values;
    }

    /** Master-plan release gate: 100% of the injection corpus must block. */
    private static final List<String> INJECTION_CORPUS = corpus("injection");

    /** Wording traps that share words with attacks - all must pass through. */
    private static final List<String> FALSE_POSITIVE_CORPUS = corpus("falsePositive");

    @Test
    void injectionCorpusFullyBlocked() {
        for (String attack : INJECTION_CORPUS) {
            SafetyGateService.GateResult result = gate.check(attack);
            assertEquals(Outcome.INJECTION, result.outcome(), "must block: " + attack);
            assertNotNull(result.reply());
        }
    }

    @Test
    void falsePositiveCorpusFullyPasses() {
        for (String legit : FALSE_POSITIVE_CORPUS) {
            SafetyGateService.GateResult result = gate.check(legit);
            assertEquals(Outcome.CLEAN, result.outcome(), "must pass: " + legit);
            assertNull(result.reply());
        }
    }

    @Test
    void garbageInputCaught() {
        for (String garbage : corpus("garbage")) {
            assertEquals(Outcome.GARBAGE, gate.check(garbage).outcome(), "must be garbage: '" + garbage + "'");
        }
        assertEquals(Outcome.GARBAGE, gate.check(null).outcome());
    }

    @Test
    void garbageReplyOffersRecoveryExamples() {
        SafetyGateService.GateResult result = gate.check("!!!");
        assertEquals(Outcome.GARBAGE, result.outcome());
        assertNotNull(result.reply());
        assertEquals(true, result.reply().contains("battery voltage"));
    }

    @Test
    void shortTokensAndCodesPass() {
        // Bare branch codes, short follow-ups, and numeric ids must never be garbage.
        for (String legit : List.of("MT", "BBSR", "ok", "yes", "BOI-DX7", "358773400033916")) {
            assertEquals(Outcome.CLEAN, gate.check(legit).outcome(), "must pass: " + legit);
        }
    }
}
