package com.seple.ThingsBoard_Bot.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.context.annotation.Profile;
import com.seple.ThingsBoard_Bot.model.dto.ChatRequest;
import com.seple.ThingsBoard_Bot.model.dto.ChatResponse;
import com.seple.ThingsBoard_Bot.service.ChatService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@Profile("chat")
public class ChatController {

    private final ChatService chatService;

    /**
     * Dedicated, BOUNDED pool so SSE streams don't tie up servlet threads and can't exhaust the
     * JVM under load (audit #14). When the pool + queue are full, new streams are rejected and the
     * client gets a "server busy" error instead of the server thrashing. Sizes are env-tunable.
     */
    private static final int SSE_CORE_THREADS = Integer.getInteger("sse.core.threads", 8);
    private static final int SSE_MAX_THREADS = Integer.getInteger("sse.max.threads", 64);
    private static final int SSE_QUEUE_CAPACITY = Integer.getInteger("sse.queue.capacity", 128);

    private final ExecutorService streamExecutor = new ThreadPoolExecutor(
            SSE_CORE_THREADS, SSE_MAX_THREADS, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY),
            new ThreadPoolExecutor.AbortPolicy());

    /** SSE connection lifetime; long enough to cover slow LLM streams. */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * POST /api/v1/chat/ask
     * Answer a user question about IoT device data.
     * If X-TB-Token header is present, data is scoped to that user's devices only.
     */
    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestion(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-TB-Token", required = false) String userToken) {

        log.info("Received chat request: '{}' (user token: {})",
                request.getQuestion(), userToken != null ? "present" : "absent");

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ChatResponse.builder()
                            .error(true)
                            .errorMessage("Question cannot be empty")
                            .timestamp(System.currentTimeMillis())
                            .build()
            );
        }

        ChatResponse response = chatService.answerQuestion(request, userToken);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/chat/ask/stream
     * Streaming variant of {@link #askQuestion}. Returns an SSE stream that emits
     * {@code token} events (incremental text for LLM answers), a terminal
     * {@code done} event (full answer + metadata), or an {@code error} event.
     * Deterministic answers emit a single {@code done} event with no tokens.
     */
    @PostMapping("/ask/stream")
    public SseEmitter askQuestionStream(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-TB-Token", required = false) String userToken,
            HttpServletResponse response) {

        log.info("Received streaming chat request: '{}' (user token: {})",
                request.getQuestion(), userToken != null ? "present" : "absent");

        // Defeat buffering anywhere between server and browser so tokens arrive
        // immediately and the stream renders fluently rather than in one burst.
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no"); // nginx: disable proxy buffering
        response.setHeader("Connection", "keep-alive");

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            streamExecutor.execute(() -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("errorMessage", "Question cannot be empty")));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.warn("SSE stream error: {}", e.getMessage()));

        try {
            streamExecutor.execute(() -> chatService.answerQuestionStreaming(request, userToken, emitter));
        } catch (RejectedExecutionException rejected) {
            log.warn("SSE stream rejected — pool saturated");
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("errorMessage", "Server is busy. Please try again in a moment.")));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
        return emitter;
    }

    /**
     * GET /api/v1/chat/init
     * Non-blocking call to pre-fetch user devices data and cache it in the backend.
     * Should be called quietly in the background right after login.
     */
    @GetMapping("/init")
    public ResponseEntity<Void> initCache(@RequestHeader(value = "X-TB-Token", required = true) String userToken) {
        log.info("Received cache init request (user token present)");
        // Trigger initialization (this happens synchronously in the request thread currently, 
        // but returns instantly to the frontend since the frontend doesn't rely on the response body)
        chatService.initializeUserCache(userToken);
        return ResponseEntity.ok().build();
    }
}

