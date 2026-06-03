package com.seple.ThingsBoard_Bot.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.config.OpenAIConfig;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * OpenAI REST API client using RestTemplate.
 * Sends chat completion requests and returns the AI response.
 */
@Slf4j
@Component
public class OpenAIClient {

    private final OpenAIConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OkHttpClient streamingHttpClient;

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    public OpenAIClient(OpenAIConfig config) {
        this.config = config;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(config.getTimeout()));
        factory.setReadTimeout(java.time.Duration.ofMillis(config.getTimeout()));
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        // Dedicated client for SSE streaming: read timeout must be long enough to
        // span the whole token-by-token stream, not a single response body read.
        this.streamingHttpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Send a chat completion request to OpenAI natively.
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, new ArrayList<>(), userMessage);
    }

    /**
     * Send a chat completion request to OpenAI with Conversation History injected natively into the payload.
     *
     * @param systemPrompt The system message defining the AI's role
     * @param history      The previous user/assistant interaction history
     * @param userMessage  The user's NEW question with context
     * @return The AI's response text
     */
    public String chat(String systemPrompt, List<com.seple.ThingsBoard_Bot.model.dto.ChatMessage> history, String userMessage) {
        log.debug("Calling OpenAI {} with {} char message & {} char history", config.getModel(), userMessage.length(), history != null ? history.size() : 0);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());

            Map<String, Object> requestBody = buildRequestBody(systemPrompt, history, userMessage, false);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_CHAT_URL, entity, String.class);
            long duration = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String reply = responseJson
                        .path("choices").get(0)
                        .path("message")
                        .path("content").asText();

                int totalTokens = responseJson.path("usage").path("total_tokens").asInt(0);

                log.info("✅ OpenAI response received in {}ms ({} tokens used)", duration, totalTokens);
                return reply;
            } else {
                log.error("❌ OpenAI returned non-success: {}", response.getStatusCode());
                return "I'm sorry, I encountered an error communicating with the AI service. Please try again.";
            }

        } catch (Exception e) {
            log.error("❌ Error calling OpenAI: {}", e.getMessage());
            return "I'm sorry, I couldn't process your question right now. Error: " + e.getMessage();
        }
    }

    /**
     * Streaming variant of {@link #chat}. Sends {@code "stream": true} to OpenAI,
     * reads the SSE {@code data:} lines, and invokes {@code onToken} for each
     * delta content chunk as it arrives. Returns the full concatenated answer.
     *
     * @param systemPrompt The system message defining the AI's role
     * @param history      The previous user/assistant interaction history
     * @param userMessage  The user's NEW question with context
     * @param onToken      Callback invoked with each incremental text chunk
     * @return The full concatenated answer text
     */
    public String chatStream(String systemPrompt,
            List<com.seple.ThingsBoard_Bot.model.dto.ChatMessage> history,
            String userMessage, Consumer<String> onToken) {
        log.debug("Streaming OpenAI {} with {} char message & {} char history",
                config.getModel(), userMessage.length(), history != null ? history.size() : 0);

        StringBuilder full = new StringBuilder();
        try {
            Map<String, Object> requestBody = buildRequestBody(systemPrompt, history, userMessage, true);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(OPENAI_CHAT_URL)
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Accept", "text/event-stream")
                    // Disable gzip: OkHttp's transparent gzip buffers blocks of the
                    // SSE body, delaying tokens and breaking incremental streaming.
                    .addHeader("Accept-Encoding", "identity")
                    .post(RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                    .build();

            long startTime = System.currentTimeMillis();
            try (Response response = streamingHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("❌ OpenAI stream returned non-success: {}", response.code());
                    throw new IOException("OpenAI stream returned HTTP " + response.code());
                }

                BufferedSource source = response.body().source();
                while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line == null) {
                        break;
                    }
                    if (line.isEmpty() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    JsonNode node = objectMapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        String chunk = delta.asText();
                        if (!chunk.isEmpty()) {
                            full.append(chunk);
                            onToken.accept(chunk);
                        }
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ OpenAI stream completed in {}ms ({} chars)", duration, full.length());
            return full.toString();

        } catch (Exception e) {
            log.error("❌ Error streaming OpenAI: {}", e.getMessage());
            // If we got partial content before the failure, return what we have
            // so the user still sees a (possibly incomplete) answer.
            if (full.length() > 0) {
                return full.toString();
            }
            throw new RuntimeException("OpenAI streaming failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the OpenAI chat-completions request body shared by the blocking
     * and streaming code paths.
     */
    private Map<String, Object> buildRequestBody(String systemPrompt,
            List<com.seple.ThingsBoard_Bot.model.dto.ChatMessage> history,
            String userMessage, boolean stream) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", config.getTemperature());
        if (stream) {
            requestBody.put("stream", true);
        }

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        // Inject History BEFORE the final user question
        if (history != null && !history.isEmpty()) {
            for (com.seple.ThingsBoard_Bot.model.dto.ChatMessage msg : history) {
                Map<String, String> hm = new HashMap<>();
                hm.put("role", msg.getRole());
                hm.put("content", msg.getContent());
                messages.add(hm);
            }
        }

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        requestBody.put("messages", messages);
        return requestBody;
    }
}
