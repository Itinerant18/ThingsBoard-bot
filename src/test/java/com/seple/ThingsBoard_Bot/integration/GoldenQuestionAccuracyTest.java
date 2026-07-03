package com.seple.ThingsBoard_Bot.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.DeterministicAnswerService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntentResolver;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;
import com.seple.ThingsBoard_Bot.service.query.resolve.ManualAliasTable;
import com.seple.ThingsBoard_Bot.support.FixtureLoader;
import com.seple.ThingsBoard_Bot.support.MockSnapshotStore;

class GoldenQuestionAccuracyTest {

    private List<BranchSnapshot> snapshots;
    private QueryIntentResolver resolver;
    private DeterministicAnswerService answerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        snapshots = MockSnapshotStore.loadDefault();
        BranchAliasIndex aliasIndex = new BranchAliasIndex();
        resolver = new QueryIntentResolver(aliasIndex,
                new FuzzyBranchResolver(aliasIndex, new ManualAliasTable(aliasIndex), 0.90, 0.75, 0.55));
        answerService = new DeterministicAnswerService(new AnswerTemplateService());
    }

    @Test
    void shouldMatchGoldenQuestions() throws Exception {
        List<GoldenQuestion> questions = objectMapper.readValue(
                FixtureLoader.load("fixtures/golden_questions.json"),
                new TypeReference<List<GoldenQuestion>>() {});

        for (GoldenQuestion question : questions) {
            ResolvedQuery resolved = resolver.resolve(question.question(), snapshots, null);
            assertEquals(QueryIntent.valueOf(question.intent()), resolved.getIntent(), question.question());

            if (question.matchedBranch() != null) {
                assertNotNull(resolved.getTargetBranch(), question.question());
                assertEquals(question.matchedBranch(), resolved.getTargetBranch().getIdentity().getTechnicalId(), question.question());
            }

            String answer = answerService.answer(resolved, snapshots);
            assertNotNull(answer, question.question());
            for (String fragment : question.contains()) {
                assertTrue(answer.contains(fragment), question.question() + " missing fragment: " + fragment + " in " + answer);
            }
        }
    }

    private record GoldenQuestion(String question, String intent, String matchedBranch, List<String> contains) {
    }
}
