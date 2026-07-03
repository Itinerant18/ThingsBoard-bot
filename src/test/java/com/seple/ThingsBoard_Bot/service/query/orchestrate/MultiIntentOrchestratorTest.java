package com.seple.ThingsBoard_Bot.service.query.orchestrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.config.ExtractorConfig;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.DeterministicAnswerService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.extract.ExtractedIntent;
import com.seple.ThingsBoard_Bot.service.query.extract.ExtractionResult;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;
import com.seple.ThingsBoard_Bot.service.query.resolve.ManualAliasTable;
import com.seple.ThingsBoard_Bot.support.MockSnapshotStore;

/**
 * Runs the orchestrator against the real fixture snapshots and the real handler chain -
 * no mocks - so combined multi-intent answers are exercised end to end.
 */
class MultiIntentOrchestratorTest {

    private List<BranchSnapshot> snapshots;
    private MultiIntentOrchestrator orchestrator;
    private ExtractorConfig config;

    @BeforeEach
    void setUp() throws Exception {
        snapshots = MockSnapshotStore.loadDefault();
        BranchAliasIndex aliasIndex = new BranchAliasIndex();
        config = new ExtractorConfig();
        orchestrator = new MultiIntentOrchestrator(
                new FuzzyBranchResolver(aliasIndex, new ManualAliasTable(aliasIndex), 0.90, 0.75, 0.55),
                aliasIndex,
                new DeterministicAnswerService(new AnswerTemplateService()),
                config);
    }

    private ExtractedIntent intent(QueryIntent intent, double confidence, String... entities) {
        return new ExtractedIntent(intent, List.of(entities), null, confidence);
    }

    @Test
    void singleIntentAnswers() {
        OrchestrationResult result = orchestrator.orchestrate(
                new ExtractionResult(List.of(intent(QueryIntent.BATTERY_VOLTAGE, 0.95, "Tarakeshwar"))),
                "What is Tarakeshwar battery voltage?", snapshots, "BOI");

        assertEquals(OrchestrationResult.Status.ANSWERED, result.status());
        assertTrue(result.message().contains("Battery Voltage Reading"));
    }

    @Test
    void typoEntityResolvesThroughFuzzyBands() {
        OrchestrationResult result = orchestrator.orchestrate(
                new ExtractionResult(List.of(intent(QueryIntent.BATTERY_VOLTAGE, 0.95, "Tarakeswar"))),
                "battery voltage of Tarakeswar", snapshots, "BOI");

        assertEquals(OrchestrationResult.Status.ANSWERED, result.status());
        assertTrue(result.message().contains("Battery Voltage Reading"));
    }

    @Test
    void multiIntentCombinesSections() {
        OrchestrationResult result = orchestrator.orchestrate(
                new ExtractionResult(List.of(
                        intent(QueryIntent.BATTERY_VOLTAGE, 0.95, "Tarakeshwar"),
                        intent(QueryIntent.CCTV_STATUS, 0.9, "Liluah"))),
                "battery of Tarakeshwar and cameras of Liluah", snapshots, "BOI");

        assertEquals(OrchestrationResult.Status.ANSWERED, result.status());
        assertTrue(result.message().contains("Battery Voltage Reading"));
        assertTrue(result.message().contains("ONLINE"));
        assertTrue(result.message().contains("---"), "sections must be visibly separated");
    }

    @Test
    void unknownEntityProducesClarificationNotFabrication() {
        OrchestrationResult result = orchestrator.orchestrate(
                new ExtractionResult(List.of(intent(QueryIntent.BATTERY_VOLTAGE, 0.95, "QXZPLW999"))),
                "battery voltage of QXZPLW999", snapshots, "BOI");

        assertEquals(OrchestrationResult.Status.CLARIFICATION, result.status());
        assertNotNull(result.message());
        assertTrue(result.message().contains("QXZPLW999"));
    }

    @Test
    void lowConfidenceIntentsAreGatedOut() {
        config.setConfidenceGate(0.60);
        OrchestrationResult result = orchestrator.orchestrate(
                new ExtractionResult(List.of(intent(QueryIntent.BATTERY_VOLTAGE, 0.30, "Tarakeshwar"))),
                "maybe battery?", snapshots, "BOI");

        assertEquals(OrchestrationResult.Status.UNANSWERED, result.status());
    }

    @Test
    void emptyExtractionIsUnanswered() {
        assertEquals(OrchestrationResult.Status.UNANSWERED,
                orchestrator.orchestrate(ExtractionResult.empty(), "q", snapshots, "BOI").status());
        assertEquals(OrchestrationResult.Status.UNANSWERED,
                orchestrator.orchestrate(null, "q", snapshots, "BOI").status());
    }

    @Test
    void globalOverviewNeedsNoEntities() {
        OrchestrationResult result = orchestrator.orchestrate(
                new ExtractionResult(List.of(intent(QueryIntent.GLOBAL_OVERVIEW, 0.95))),
                "list all branches", snapshots, "BOI");

        assertEquals(OrchestrationResult.Status.ANSWERED, result.status());
        assertTrue(result.message().contains("Total:"));
    }

    @Test
    void unhandledIntentFallsBackToUnanswered() {
        OrchestrationResult result = orchestrator.orchestrate(
                new ExtractionResult(List.of(intent(QueryIntent.GENERAL_LLM, 0.95))),
                "something the handlers cannot do", snapshots, "BOI");

        assertEquals(OrchestrationResult.Status.UNANSWERED, result.status());
    }
}
