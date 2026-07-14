package com.seple.ThingsBoard_Bot.bench;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.config.ExtractorConfig;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.DeterministicAnswerService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntentResolver;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;
import com.seple.ThingsBoard_Bot.service.query.extract.ExtractionResult;
import com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService;
import com.seple.ThingsBoard_Bot.service.query.orchestrate.MultiIntentOrchestrator;
import com.seple.ThingsBoard_Bot.service.query.orchestrate.OrchestrationResult;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;
import com.seple.ThingsBoard_Bot.service.query.resolve.ManualAliasTable;
import com.seple.ThingsBoard_Bot.service.query.safety.SafetyGateService;
import com.seple.ThingsBoard_Bot.support.FixtureLoader;
import com.seple.ThingsBoard_Bot.support.MockSnapshotStore;
import com.seple.ThingsBoard_Bot.support.RecordedIntentExtractor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Phase 4, Task 4.1 - the test bench runner. Loads scenario files (JSON - the project's
 * one fixture format), drives the full offline pipeline (keyword resolver + fuzzy bands +
 * handlers, extractor replay + orchestrator, safety gate), and fills a
 * {@link BenchScorecard}. Adding a scenario is a fixture edit, not Java code.
 *
 * <p>Everything runs against {@code MockSnapshotStore} and recorded extractions:
 * deterministic, offline, zero tokens.
 */
public final class TestBenchRunner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<BranchSnapshot> snapshots;
    private final QueryIntentResolver resolver;
    private final DeterministicAnswerService answerService;
    private final RecordedIntentExtractor recordedExtractor;
    private final MultiIntentOrchestrator orchestrator;
    private final SafetyGateService safetyGate;

    public TestBenchRunner() throws Exception {
        this.snapshots = MockSnapshotStore.loadDefault();
        BranchAliasIndex aliasIndex = new BranchAliasIndex();
        FuzzyBranchResolver fuzzy = new FuzzyBranchResolver(aliasIndex, new ManualAliasTable(aliasIndex),
                0.90, 0.75, 0.55);
        this.resolver = new QueryIntentResolver(aliasIndex, fuzzy,
                new GlossaryService(new org.springframework.core.io.ClassPathResource("glossary.json")));
        this.answerService = new DeterministicAnswerService(new AnswerTemplateService());
        this.recordedExtractor = new RecordedIntentExtractor();
        this.orchestrator = new MultiIntentOrchestrator(fuzzy, aliasIndex, answerService, new ExtractorConfig());
        this.safetyGate = new SafetyGateService(new SimpleMeterRegistry());
    }

    /** Runs every scenario source and returns the filled scorecard. */
    public BenchScorecard run() throws Exception {
        BenchScorecard scorecard = new BenchScorecard();
        runGoldenQuestions(scorecard);
        runExtractorScenarios(scorecard);
        runSafetyCorpus(scorecard);
        return scorecard;
    }

    /** Keyword-path scenarios: resolver -> handlers, asserting intent, branch, and content. */
    private void runGoldenQuestions(BenchScorecard scorecard) throws Exception {
        JsonNode scenarios = objectMapper.readTree(FixtureLoader.load("fixtures/golden_questions.json"));
        for (JsonNode s : scenarios) {
            String question = s.path("question").asText();
            String category = s.path("category").asText("paraphrase");
            boolean passed;
            try {
                ResolvedQuery resolved = resolver.resolve(question, snapshots, null);
                passed = resolved.getIntent().name().equals(s.path("intent").asText());
                if (passed && s.hasNonNull("matchedBranch")) {
                    passed = resolved.getTargetBranch() != null
                            && s.path("matchedBranch").asText()
                                    .equals(resolved.getTargetBranch().getIdentity().getTechnicalId());
                }
                if (passed) {
                    if (resolved.getIntent() == com.seple.ThingsBoard_Bot.service.query.QueryIntent.NAVIGATION) {
                        passed = true;
                    } else {
                        String answer = answerService.answer(resolved, snapshots);
                        passed = answer != null && containsAll(answer, s.path("contains"));
                    }
                }
            } catch (Exception e) {
                passed = false;
            }
            scorecard.record(category, passed);
        }
    }

    /** Extractor-path scenarios: recorded extraction -> orchestrator, asserting structure + content. */
    private void runExtractorScenarios(BenchScorecard scorecard) throws Exception {
        JsonNode scenarios = objectMapper.readTree(FixtureLoader.load("fixtures/bench_extractor_scenarios.json"));
        for (JsonNode s : scenarios) {
            String question = s.path("question").asText();
            String category = s.path("category").asText("paraphrase");
            boolean passed;
            try {
                ExtractionResult extraction = recordedExtractor.extract(question, List.of());
                passed = !extraction.isEmpty();
                if (passed && s.has("expectIntentCount")) {
                    passed = extraction.intents().size() == s.path("expectIntentCount").asInt();
                }
                if (passed && s.has("expectIntents")) {
                    for (int i = 0; i < s.path("expectIntents").size() && passed; i++) {
                        passed = extraction.intents().get(i).intent().name()
                                .equals(s.path("expectIntents").get(i).asText());
                    }
                }
                if (passed) {
                    OrchestrationResult result = orchestrator.orchestrate(extraction, question, snapshots, "BOI");
                    passed = result.status().name().equals(s.path("expectStatus").asText("ANSWERED"))
                            && (result.message() == null || containsAll(result.message(), s.path("contains")));
                    if (result.message() == null && s.path("contains").size() > 0) {
                        passed = false;
                    }
                }
            } catch (Exception e) {
                passed = false;
            }
            scorecard.record(category, passed);
        }
    }

    /** Safety-gate corpora: injections must block, garbage must be caught, legit input must pass. */
    private void runSafetyCorpus(BenchScorecard scorecard) throws Exception {
        JsonNode corpus = objectMapper.readTree(FixtureLoader.load("fixtures/bench_safety_corpus.json"));
        for (JsonNode attack : corpus.path("injection")) {
            scorecard.record("injection",
                    safetyGate.check(attack.asText()).outcome() == SafetyGateService.Outcome.INJECTION);
        }
        for (JsonNode garbage : corpus.path("garbage")) {
            scorecard.record("garbage",
                    safetyGate.check(garbage.asText()).outcome() == SafetyGateService.Outcome.GARBAGE);
        }
        for (JsonNode legit : corpus.path("falsePositive")) {
            scorecard.record("false_positive",
                    safetyGate.check(legit.asText()).outcome() == SafetyGateService.Outcome.CLEAN);
        }
    }

    private boolean containsAll(String answer, JsonNode fragments) {
        for (JsonNode fragment : fragments) {
            if (!answer.contains(fragment.asText())) {
                return false;
            }
        }
        return true;
    }
}
