package com.seple.ThingsBoard_Bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.entity.DeviceEvent;
import com.seple.ThingsBoard_Bot.model.dto.TbEventPayload;
import com.seple.ThingsBoard_Bot.repository.DeviceEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class EventWriteService {

    private final DeviceEventRepository deviceEventRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public EventWriteService(DeviceEventRepository deviceEventRepository, ObjectMapper objectMapper) {
        this.deviceEventRepository = deviceEventRepository;
        this.objectMapper = objectMapper;
    }

    public void writeToDatabase(TbEventPayload event) {
        try {
            Map<String, Object> payloadData = event.getAttributes() != null ? event.getAttributes() : event.getTelemetry();
            
            DeviceEvent entity = new DeviceEvent();
            entity.setCustomerId(event.getCustomerId());
            entity.setBranchNodeId(event.getBranchName());
            
            // Idempotency requires a stable message id. Minting a random UUID for a missing id
            // would make the event impossible to deduplicate, so reject it instead (audit #10).
            if (event.getTbMessageId() == null || event.getTbMessageId().isBlank()) {
                throw new IllegalArgumentException(
                        "Event has no tbMessageId; refusing to persist a non-deduplicatable event");
            }
            try {
                entity.setTbMessageId(UUID.fromString(event.getTbMessageId()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Event tbMessageId is not a valid UUID: " + event.getTbMessageId(), e);
            }
            
            entity.setLogType(event.getLogType());
            entity.setField(event.getField());
            entity.setPrevValue(event.getPrevValue());
            entity.setNewValue(event.getNewValue());
            entity.setEventTime(event.getEventTime() != null ? event.getEventTime() : Instant.now());
            entity.setReceivedAt(event.getReceivedAt() != null ? event.getReceivedAt() : Instant.now());
            entity.setRawPayload(payloadData);

            deviceEventRepository.save(entity);
            log.info("✅ Event saved: customer={}, field={}, value={}",
                    event.getCustomerId(), event.getField(), event.getNewValue());

        } catch (Exception e) {
            log.error("❌ Failed to write event: {}", e.getMessage());
            throw new RuntimeException("Failed to write event", e);
        }
    }
}