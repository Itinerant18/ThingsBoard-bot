package com.seple.ThingsBoard_Bot.service;

import com.seple.ThingsBoard_Bot.model.dto.TbEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("consumer")
public class EventConsumerService {

    private final IdempotencyService idempotencyService;
    private final EventWriteService eventWriteService;
    private final RedisCacheService redisCacheService;
    private final AncestorPathCache ancestorPathCache;
    private final LuaScriptService luaScriptService;
    private final BranchAncestorPathRepository branchAncestorPathRepository;

    @RabbitListener(id = "eventListener", queues = "iot.events")
    public void consume(TbEventPayload event) {
        log.info("📥 Consumed event: device={}, field={}, value={}",
                event.getDeviceName(), event.getField(), event.getNewValue());

        if (event == null || event.getTbMessageId() == null) {
            // Unparseable / un-keyable: ack-and-drop (cannot dedupe or DLQ-route meaningfully).
            log.warn("⚠️ Invalid event payload (no message id), dropping");
            return;
        }

        String messageId = event.getTbMessageId();

        // Skip if a prior delivery already completed successfully. The id is marked only AFTER
        // both the DB write and Redis update succeed (below), so a mid-processing crash leaves it
        // unmarked and the message eligible for redelivery instead of being lost (audit #4).
        if (idempotencyService.exists(messageId)) {
            log.info("[IDEM] Already processed, skipping: {}", messageId);
            return;
        }

        try {
            eventWriteService.writeToDatabase(event);
            log.info("✅ Event written to database");
            updateRedisCache(event);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // A prior delivery already persisted this event (unique tb_message_id). Treat as done.
            log.info("[IDEM] Duplicate detected at DB layer, skipping: {}", messageId);
            idempotencyService.mark(messageId);
            return;
        }
        // Exceptions other than the duplicate case propagate on purpose: the listener's retry +
        // dead-letter config (RabbitMQConfig / application.properties) handles them rather than
        // silently dropping the event.

        idempotencyService.mark(messageId);
        log.info("✅ Event fully processed: {}", messageId);
    }

    private void updateRedisCache(TbEventPayload event) {
        String customerId = event.getCustomerId();
        String deviceId = event.getDeviceId();
        String branchNodeId = event.getBranchName();
        
        redisCacheService.updateDeviceState(customerId, deviceId, event.getField(), event.getNewValue());
        
        redisCacheService.setDeviceMeta(customerId, deviceId, branchNodeId, event.getBranchName());
        
        List<String> ancestors = ancestorPathCache.getAncestors(customerId, branchNodeId);
        if (ancestors.isEmpty()) {
            Optional<BranchAncestorPath> dbPathOpt = branchAncestorPathRepository.findByBranchNodeIdAndCustomerId(branchNodeId, customerId);
            if (dbPathOpt.isPresent()) {
                ancestors = dbPathOpt.get().getAncestorList();
                ancestorPathCache.cacheAncestors(customerId, branchNodeId, ancestors);
            } else {
                ancestors = buildDefaultAncestors(customerId, branchNodeId);
                ancestorPathCache.cacheAncestors(customerId, branchNodeId, ancestors);
            }
        }
        
        luaScriptService.executeUpdateCounters(
                customerId,
                deviceId,
                branchNodeId,
                ancestors,
                event.getField(),
                event.getNewValue(),
                event.getPrevValue()
        );
        
        log.info("✅ Redis cache updated for {}/{}", customerId, deviceId);
    }

    private List<String> buildDefaultAncestors(String customerId, String branchNodeId) {
        return Arrays.asList(
                customerId + "_HO",
                customerId + "_ZO_" + branchNodeId,
                branchNodeId
        );
    }
}