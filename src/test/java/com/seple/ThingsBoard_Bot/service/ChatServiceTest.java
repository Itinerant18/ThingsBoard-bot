package com.seple.ThingsBoard_Bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.seple.ThingsBoard_Bot.client.OpenAIClient;
import com.seple.ThingsBoard_Bot.config.ChatbotConfig;
import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.PowerStatus;
import com.seple.ThingsBoard_Bot.model.dto.ChatMessage;
import com.seple.ThingsBoard_Bot.model.dto.ChatRequest;
import com.seple.ThingsBoard_Bot.model.dto.ChatResponse;
import com.seple.ThingsBoard_Bot.model.dto.GlobalOverviewCounters;
import com.seple.ThingsBoard_Bot.service.query.DeterministicAnswerService;
import com.seple.ThingsBoard_Bot.service.index.GlobalAggregatorService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.QueryIntentResolver;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;
import com.seple.ThingsBoard_Bot.util.StructuredContextBuilder;
import com.seple.ThingsBoard_Bot.service.query.QueryRouterService;

class ChatServiceTest {

    private final UserDataService userDataService = mock(UserDataService.class);
    private final OpenAIClient openAIClient = mock(OpenAIClient.class);
    private final ChartService chartService = mock(ChartService.class);
    private final ChatMemoryService chatMemoryService = mock(ChatMemoryService.class);
    private final ChatbotConfig chatbotConfig = mock(ChatbotConfig.class);
    private final QueryIntentResolver queryIntentResolver = mock(QueryIntentResolver.class);
    private final DeterministicAnswerService deterministicAnswerService = mock(DeterministicAnswerService.class);
    private final StructuredContextBuilder structuredContextBuilder = mock(StructuredContextBuilder.class);
    private final GlobalAggregatorService globalAggregatorService = mock(GlobalAggregatorService.class);
    private final QueryRouterService queryRouterService = mock(QueryRouterService.class);
    private final ResponseEvaluationService responseEvaluationService = mock(ResponseEvaluationService.class);
    private final com.seple.ThingsBoard_Bot.service.query.extract.ExtractorShadowService extractorShadowService =
            mock(com.seple.ThingsBoard_Bot.service.query.extract.ExtractorShadowService.class);

    private final ChatService service = new ChatService(
            userDataService,
            openAIClient,
            chartService,
            chatMemoryService,
            chatbotConfig,
            queryIntentResolver,
            deterministicAnswerService,
            structuredContextBuilder,
            globalAggregatorService,
            queryRouterService,
            responseEvaluationService,
            extractorShadowService,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            new org.springframework.core.io.ClassPathResource("prompts/system-prompt.txt"));

    @Test
    void shouldApplyPendingTopicForBranchOnlyFollowUpAndUseDeterministicAnswer() {
        String token = "jwt-token";
        String question = "CHANDANNAGAR";
        BranchSnapshot branch = BranchSnapshot.builder()
                .identity(BranchIdentity.builder()
                        .branchName("BRANCH CHANDANNAGAR")
                        .technicalId("BOI-CHANDANNAGAR")
                        .build())
                .power(PowerStatus.builder().batteryVoltage(0.0).build())
                .build();
        List<BranchSnapshot> snapshots = List.of(branch);

        ResolvedQuery initial = ResolvedQuery.builder()
                .intent(QueryIntent.GENERAL_LLM)
                .originalQuestion(question)
                .targetBranch(branch)
                .targetSystem(null)
                .global(false)
                .ambiguous(false)
                .branchFromMemory(false)
                .deterministic(false)
                .confidence(0.55)
                .build();

        when(chatMemoryService.getHistory(token)).thenReturn(List.of(new ChatMessage("assistant", "prompt")));
        when(chatMemoryService.getActiveBranch(token)).thenReturn(null);
        when(chatMemoryService.getPendingTopic(token)).thenReturn("BATTERY_VOLTAGE");
        when(userDataService.getUserBranchSnapshots(token)).thenReturn(snapshots);
        when(queryIntentResolver.resolve(eq(question), eq(snapshots), eq(null))).thenReturn(initial);
        when(chatbotConfig.isDeterministicAnswersEnabled()).thenReturn(true);
        when(deterministicAnswerService.answer(any(ResolvedQuery.class), eq(snapshots), any()))
                .thenReturn("For Branch CHANDANNAGAR, Battery Voltage Reading is 0.0V DC.");

        ChatResponse response = service.answerQuestion(new ChatRequest(question, null, null), token);

        ArgumentCaptor<ResolvedQuery> queryCaptor = ArgumentCaptor.forClass(ResolvedQuery.class);
        verify(deterministicAnswerService).answer(queryCaptor.capture(), eq(snapshots), any());
        assertEquals(QueryIntent.BATTERY_VOLTAGE, queryCaptor.getValue().getIntent());
        assertFalse(response.isError());
        String expectedAnswer = "For Branch CHANDANNAGAR, Battery Voltage Reading is 0.0V DC.\n\n[SUGGESTIONS]\n" +
                "- What is the CCTV status of BRANCH CHANDANNAGAR?\n" +
                "- Are there any active alarms for BRANCH CHANDANNAGAR?\n";
        assertEquals(expectedAnswer, response.getAnswer());
        verify(chatMemoryService).setPendingTopic(token, null);
        verify(openAIClient, never()).chat(any(), any(), any());
    }

    @Test
    void shouldUseAggregatorForGlobalOverviewWhenEnabled() {
        String token = "jwt-token";
        String question = "is there any inactive branches among all my branches";
        List<BranchSnapshot> snapshots = List.of();

        ResolvedQuery global = ResolvedQuery.builder()
                .intent(QueryIntent.GLOBAL_OVERVIEW)
                .originalQuestion(question)
                .global(true)
                .ambiguous(false)
                .deterministic(true)
                .confidence(0.95)
                .build();

        when(chatMemoryService.getHistory(token)).thenReturn(List.of());
        when(chatMemoryService.getActiveBranch(token)).thenReturn(null);
        when(userDataService.isTwoStepFetchEnabled()).thenReturn(true);
        when(userDataService.getUserBranchIndexSnapshots(token)).thenReturn(snapshots);
        when(queryIntentResolver.resolve(eq(question), eq(snapshots), eq(null))).thenReturn(global);
        when(globalAggregatorService.isEnabled()).thenReturn(true);
        when(globalAggregatorService.fetchGlobalOverview(token)).thenReturn(GlobalOverviewCounters.builder()
                .onlineBranches(12)
                .offlineBranches(0)
                .sourceDeviceId("agg-1")
                .build());
        when(deterministicAnswerService.answerGlobalOverview(any(GlobalOverviewCounters.class)))
                .thenReturn("**Total: 12 Online | 0 Offline**\nFor your question about all branches, here is the current branch-level status.");

        ChatResponse response = service.answerQuestion(new ChatRequest(question, null, null), token);

        assertFalse(response.isError());
        assertEquals("GLOBAL_OVERVIEW", response.getMetadata().getIntent());
        verify(openAIClient, never()).chat(any(), any(), any());
    }
}
