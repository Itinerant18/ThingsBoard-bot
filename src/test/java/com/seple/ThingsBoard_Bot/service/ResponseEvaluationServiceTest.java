package com.seple.ThingsBoard_Bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.seple.ThingsBoard_Bot.client.OpenAIClient;
import com.seple.ThingsBoard_Bot.config.EvaluationConfig;
import com.seple.ThingsBoard_Bot.model.dto.EvaluationInput;
import com.seple.ThingsBoard_Bot.model.dto.EvaluationReport;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ResponseEvaluationServiceTest {

    private ResponseEvaluationService newService(OpenAIClient client) {
        EvaluationConfig config = new EvaluationConfig();
        config.setEnabled(true);
        return new ResponseEvaluationService(client, config, new SimpleMeterRegistry(),
                new ClassPathResource("prompts/evaluation-prompt.txt"));
    }

    private EvaluationInput sampleInput() {
        return EvaluationInput.builder()
                .customerPrefix("BOI")
                .activeBranchId("BOI-DX1")
                .userQuestion("What is the CCTV status of BALLY BAZAR?")
                .structuredContextJson("{\"focus_branch\":{\"branch_name\":\"BALLY BAZAR\"}}")
                .intent("CCTV_STATUS")
                .targetBranch("BALLY BAZAR")
                .targetSystem("cctv")
                .global(false)
                .executionPath("LLM_FALLBACK")
                .botAnswer("**Branch BALLY BAZAR: ...**")
                .build();
    }

    @Test
    void fillsAllPlaceholdersWithInputValues() {
        ResponseEvaluationService service = newService(mock(OpenAIClient.class));

        String filled = service.fillTemplate(sampleInput());

        assertThat(filled)
                .doesNotContain("[CUSTOMER_PREFIX]")
                .doesNotContain("[ACTIVE_BRANCH_ID]")
                .doesNotContain("[USER_QUESTION]")
                .doesNotContain("[STRUCTURED_CONTEXT_JSON]")
                .doesNotContain("[METADATA_INTENT]")
                .doesNotContain("[METADATA_BRANCH]")
                .doesNotContain("[METADATA_SYSTEM]")
                .doesNotContain("[IS_GLOBAL]")
                .doesNotContain("[EXECUTION_PATH]")
                .doesNotContain("[BOT_ANSWER]");
        assertThat(filled).contains("BOI", "BALLY BAZAR", "CCTV_STATUS", "false", "LLM_FALLBACK");
    }

    @Test
    void leavesLiteralExampleTokensUntouched() {
        ResponseEvaluationService service = newService(mock(OpenAIClient.class));

        String filled = service.fillTemplate(sampleInput());

        // [SUGGESTIONS] / [Suggestion 1] are literal examples in the criteria, not placeholders.
        assertThat(filled).contains("[SUGGESTIONS]");
        assertThat(filled).contains("[Suggestion 1]");
    }

    @Test
    void substitutesNaForMissingValues() {
        ResponseEvaluationService service = newService(mock(OpenAIClient.class));

        String filled = service.fillTemplate(EvaluationInput.builder()
                .global(true)
                .userQuestion("anything")
                .build());

        // Null prefix/branch/intent collapse to N/A; isGlobal renders as the boolean literal.
        assertThat(filled).contains("**Authorized Customer Tenant Prefix:** N/A");
        assertThat(filled).contains("**Is Global Query:** true");
    }

    @Test
    void parsesJudgeReport() {
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.evaluateJson(anyString(), anyString())).thenReturn(
                "{\"problem_identified\":true,\"severity\":\"CRITICAL\",\"issues\":["
                + "{\"category\":\"SECURITY\",\"description\":\"leak\","
                + "\"factual_contradiction\":{\"context_value\":\"BOI\",\"bot_value\":\"SEPL\"}}]}");

        EvaluationReport report = newService(client).evaluate(sampleInput());

        assertThat(report).isNotNull();
        assertThat(report.isProblemIdentified()).isTrue();
        assertThat(report.getSeverity()).isEqualTo("CRITICAL");
        assertThat(report.getIssues()).hasSize(1);
        assertThat(report.getIssues().get(0).getCategory()).isEqualTo("SECURITY");
        assertThat(report.getIssues().get(0).getFactualContradiction().getContextValue()).isEqualTo("BOI");
        assertThat(report.getIssues().get(0).getFactualContradiction().getBotValue()).isEqualTo("SEPL");
    }

    @Test
    void stripsMarkdownFencesBeforeParsing() {
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.evaluateJson(anyString(), anyString())).thenReturn(
                "```json\n{\"problem_identified\":false,\"severity\":\"NONE\",\"issues\":[]}\n```");

        EvaluationReport report = newService(client).evaluate(sampleInput());

        assertThat(report).isNotNull();
        assertThat(report.isProblemIdentified()).isFalse();
        assertThat(report.getSeverity()).isEqualTo("NONE");
        assertThat(report.getIssues()).isEmpty();
    }

    @Test
    void toleratesUnknownJsonFields() {
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.evaluateJson(anyString(), anyString())).thenReturn(
                "{\"problem_identified\":true,\"severity\":\"MINOR\",\"confidence\":0.9,\"issues\":[]}");

        EvaluationReport report = newService(client).evaluate(sampleInput());

        assertThat(report).isNotNull();
        assertThat(report.getSeverity()).isEqualTo("MINOR");
    }

    @Test
    void returnsNullWhenJudgeReturnsNothing() {
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.evaluateJson(anyString(), anyString())).thenReturn(null);

        assertThat(newService(client).evaluate(sampleInput())).isNull();
    }

    @Test
    void returnsNullWhenJudgeReturnsNonJson() {
        OpenAIClient client = mock(OpenAIClient.class);
        when(client.evaluateJson(anyString(), anyString())).thenReturn("I cannot evaluate this.");

        assertThat(newService(client).evaluate(sampleInput())).isNull();
    }
}
