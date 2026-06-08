package com.seple.ThingsBoard_Bot.controller;

import com.seple.ThingsBoard_Bot.model.dto.TbEventPayload;
import com.seple.ThingsBoard_Bot.service.EventParseService;
import com.seple.ThingsBoard_Bot.service.RabbitMQQueueService;
import com.seple.ThingsBoard_Bot.config.SecurityProperties;
import com.seple.ThingsBoard_Bot.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Profile("ingestion")
public class WebhookController {

    private final EventParseService eventParseService;
    private final RabbitMQQueueService rabbitMQQueueService;
    private final SecurityProperties securityProperties;

    @PostMapping("/thingsboard")
    public ResponseEntity<Void> receiveThingsBoardWebhook(
            @RequestHeader(value = "X-HMAC-SHA256", required = false) String hmac,
            @RequestBody String rawBody) {

        log.info("📥 Received webhook from ThingsBoard (body size: {} bytes)", rawBody.length());

        // HMAC verification: enforced only when a shared secret is configured, so local
        // development keeps working. Reject tampered/unsigned payloads when enabled.
        if (securityProperties.isWebhookHmacEnabled()) {
            if (!HmacUtil.isValid(securityProperties.getWebhookHmacSecret(), rawBody, hmac)) {
                log.warn("🚫 Rejected webhook with invalid/missing HMAC signature");
                return ResponseEntity.status(401).build();
            }
        } else {
            log.warn("[SECURITY] Webhook received with NO HMAC secret configured — payload is UNVERIFIED. "
                    + "Set iotchatbot.security.webhook-hmac-secret to enforce signing.");
        }

        try {
            TbEventPayload event = eventParseService.parsePayload(rawBody);
            
            if (event == null) {
                log.warn("⚠️ Failed to parse webhook payload");
                return ResponseEntity.badRequest().build();
            }
            
            log.info("📦 Parsed event: device={}, customer={}, field={}, value={}→{}",
                    event.getDeviceName(),
                    event.getCustomerId(),
                    event.getField(),
                    event.getPrevValue(),
                    event.getNewValue());
            
            rabbitMQQueueService.publishEvent(event.getCustomerId(), event);
            log.info("✅ Event published for customer: {} (routing key: iot.event.{})",
                    event.getCustomerId(), event.getCustomerId());
            
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("❌ Error processing webhook: {} - {}", e.getMessage(), e.getClass().getName());
            if (e.getCause() != null) {
                log.error("❌ Caused by: {} - {}", e.getCause().getMessage(), e.getCause().getClass().getName());
            }
            return ResponseEntity.status(500).build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "endpoint", "thingsboard-webhook",
            "timestamp", Instant.now().toString()
        ));
    }
}