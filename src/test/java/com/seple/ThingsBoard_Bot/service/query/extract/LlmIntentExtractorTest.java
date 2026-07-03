package com.seple.ThingsBoard_Bot.service.query.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.seple.ThingsBoard_Bot.client.OpenAIClient;
import com.seple.ThingsBoard_Bot.config.ExtractorConfig;
import com.seple.ThingsBoard_Bot.model.dto.ChatMessage;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LlmIntentExtractorTest {

    private OpenAIClient openAIClient;
    private ExtractorConfig config;
    private LlmIntentExtractor extractor;

    @BeforeEach
    void setUp() {
        openAIClient = mock(OpenAIClient.class);
        config = new ExtractorConfig();
        extractor = new LlmIntentExtractor(openAIClient, new ExtractionResultParser(), config,
                new SimpleMeterRegistry(), new ClassPathResource("prompts/intent-extractor-prompt.txt"));
    }

    @Test
    void extractsIntentsFromClientJson() {
        when(openAIClient.completeJson(anyString(), anyList(), anyString())).thenReturn("""
                {"intents":[{"intent":"BATTERY_VOLTAGE","entities":["Malda Town"],"confidence":0.95}]}
                """);

        ExtractionResult result = extractor.extract("battery voltage of Malda Town", List.of());

        assertEquals(1, result.intents().size());
        assertEquals(QueryIntent.BATTERY_VOLTAGE, result.intents().get(0).intent());
        assertEquals(List.of("Malda Town"), result.intents().get(0).entities());
    }

    @Test
    void systemPromptContainsTaxonomyAndQuestionIsPassedThrough() {
        when(openAIClient.completeJson(anyString(), anyList(), anyString())).thenReturn("{\"intents\":[]}");

        extractor.extract("what is the weather?", List.of());

        verify(openAIClient).completeJson(
                argThat(prompt -> prompt.contains("BATTERY_VOLTAGE") && prompt.contains("REFUSAL")
                        && prompt.contains("OUT_OF_SCOPE")),
                anyList(),
                argThat(msg -> msg.contains("what is the weather?")));
    }

    @Test
    void nullClientResponseYieldsEmptyResult() {
        when(openAIClient.completeJson(anyString(), anyList(), anyString())).thenReturn(null);
        assertTrue(extractor.extract("battery voltage of Malda Town", List.of()).isEmpty());
    }

    @Test
    void clientExceptionYieldsEmptyResult() {
        when(openAIClient.completeJson(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("boom"));
        assertTrue(extractor.extract("battery voltage of Malda Town", List.of()).isEmpty());
    }

    @Test
    void blankQuestionShortCircuitsWithoutApiCall() {
        assertTrue(extractor.extract("  ", List.of()).isEmpty());
        assertTrue(extractor.extract(null, List.of()).isEmpty());
        verify(openAIClient, never()).completeJson(anyString(), anyList(), anyString());
    }

    @Test
    void historyTrimmedToConfiguredTurns() {
        config.setHistoryTurns(2);
        when(openAIClient.completeJson(anyString(), anyList(), anyString())).thenReturn("{\"intents\":[]}");

        List<ChatMessage> history = List.of(
                message("user", "q1"), message("assistant", "a1"),
                message("user", "q2"), message("assistant", "a2"));
        extractor.extract("and Bhubaneswar?", history);

        verify(openAIClient).completeJson(anyString(),
                argThat((List<ChatMessage> h) -> h.size() == 2 && "q2".equals(h.get(0).getContent())),
                anyString());
    }

    @Test
    void nullHistoryHandled() {
        when(openAIClient.completeJson(anyString(), anyList(), anyString())).thenReturn("{\"intents\":[]}");
        assertTrue(extractor.extract("anything", null).isEmpty());
        verify(openAIClient).completeJson(anyString(), argThat(List::isEmpty), anyString());
    }

    private ChatMessage message(String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
