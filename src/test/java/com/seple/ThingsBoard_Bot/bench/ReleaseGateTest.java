package com.seple.ThingsBoard_Bot.bench;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.bench.BenchScorecard.CategoryResult;

/**
 * Phase 4, Task 4.3 - the release gate. Runs the full bench (golden questions, extractor
 * replay scenarios, safety corpora), prints the scorecard, and fails the build when any
 * BLOCKING category is below its threshold. Non-blocking categories only warn.
 */
class ReleaseGateTest {

    @Test
    void releaseGateMustPass() throws Exception {
        BenchScorecard scorecard = new TestBenchRunner().run();

        System.out.println();
        System.out.println("=== SAI RELEASE SCORECARD ===");
        System.out.println(scorecard.render());
        System.out.println();

        for (CategoryResult r : scorecard.results()) {
            if (r.blocking()) {
                assertTrue(r.meetsThreshold(), String.format(
                        "BLOCKING category '%s' below threshold: %.1f%% < %.0f%% (%d/%d passed)",
                        r.category(), r.rate() * 100, r.threshold() * 100, r.passed(), r.total()));
            }
        }
        assertTrue(scorecard.releaseGatePasses(), "release gate blocked");
    }
}
