package com.seple.ThingsBoard_Bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.dto.TbEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.ApplicationScope;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
@ApplicationScope
public class EventParseService {

    private final ObjectMapper objectMapper;
    private final RedisCacheService redisCacheService;

    @Value("${iotchatbot.customers.prefixes:BOI,BOB,SBI,CB,IB,PNB,UBI,CBI,IOB,UCO}")
    private String customerPrefixes;

    private String[] getCustomerPrefixes() {
        if (customerPrefixes == null || customerPrefixes.isBlank()) {
            return new String[]{"BOI", "BOB", "SBI", "CB", "IB", "PNB", "UBI", "CBI", "IOB", "UCO"};
        }
        String[] parts = customerPrefixes.split(",");
        String[] trimmed = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            trimmed[i] = parts[i].trim().toUpperCase();
        }
        return trimmed;
    }

    public EventParseService(ObjectMapper objectMapper, RedisCacheService redisCacheService) {
        this.objectMapper = objectMapper;
        this.redisCacheService = redisCacheService;
    }

    public TbEventPayload parsePayload(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            
            String deviceName = getTextValue(root, "deviceName");
            String deviceId = getTextValue(root, "deviceId");
            
            if (deviceName == null || deviceName.isBlank()) {
                log.warn("⚠️ No deviceName in payload");
                return null;
            }
            
            String customerId = extractCustomerId(deviceName);
            String branchName = extractBranchName(deviceName);
            
            TbEventPayload event = new TbEventPayload();
            event.setDeviceName(deviceName);
            event.setDeviceId(deviceId != null ? deviceId : "");
            event.setCustomerId(customerId);
            event.setBranchName(branchName);
            event.setReceivedAt(Instant.now());
            
            parseAttributeChanges(root, event);
            parseTelemetryChanges(root, event);
            
            return event;
            
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    private void parseAttributeChanges(JsonNode root, TbEventPayload event) {
        JsonNode dataNode = root.get("data");
        JsonNode currentAttr = dataNode != null ? dataNode.get("currentAttr") : null;
        JsonNode prevAttr = dataNode != null ? dataNode.get("prevAttr") : null;
        
        if (currentAttr == null || currentAttr.isEmpty()) {
            return;
        }
        
        Iterator<Map.Entry<String, JsonNode>> fields = currentAttr.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String field = entry.getKey();
            JsonNode valNode = entry.getValue();
            String newValue = valNode.isContainerNode() ? valNode.toString() : valNode.asText();
            
            String prevValue = "";
            if (prevAttr != null && prevAttr.has(field)) {
                JsonNode prevValNode = prevAttr.get(field);
                prevValue = prevValNode.isContainerNode() ? prevValNode.toString() : prevValNode.asText();
            } else if (redisCacheService != null) {
                prevValue = redisCacheService.getDeviceStateField(event.getCustomerId(), event.getDeviceId(), field);
            }
            
            if (!newValue.equals(prevValue)) {
                event.setTbMessageId(java.util.UUID.randomUUID().toString());
                event.setLogType("ATTRIBUTE_CHANGE");
                event.setField(field);
                event.setPrevValue(prevValue);
                event.setNewValue(newValue);
                event.setEventTime(Instant.now());
                event.setAttributes(flatten(currentAttr));
                return;
            }
        }
    }

    private void parseTelemetryChanges(JsonNode root, TbEventPayload event) {
        JsonNode dataNode = root.get("data");
        JsonNode currentTs = dataNode != null ? dataNode.get("currentTelemetry") : null;
        JsonNode prevTs = dataNode != null ? dataNode.get("prevTelemetry") : null;
        
        if (currentTs == null || currentTs.isEmpty()) {
            return;
        }
        
        Iterator<Map.Entry<String, JsonNode>> fields = currentTs.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String field = entry.getKey();
            JsonNode valNode = entry.getValue();
            String newValue = valNode.isContainerNode() ? valNode.toString() : valNode.asText();
            
            String prevValue = "";
            if (prevTs != null && prevTs.has(field)) {
                JsonNode prevValNode = prevTs.get(field);
                prevValue = prevValNode.isContainerNode() ? prevValNode.toString() : prevValNode.asText();
            } else if (redisCacheService != null) {
                prevValue = redisCacheService.getDeviceStateField(event.getCustomerId(), event.getDeviceId(), field);
            }
            
            if (!newValue.equals(prevValue)) {
                event.setTbMessageId(java.util.UUID.randomUUID().toString());
                event.setLogType("TELEMETRY_CHANGE");
                event.setField(field);
                event.setPrevValue(prevValue);
                event.setNewValue(newValue);
                event.setEventTime(Instant.now());
                event.setTelemetry(flatten(currentTs));
                return;
            }
        }
    }

    private String extractCustomerId(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return "UNKNOWN";
        }
        
        String upperName = deviceName.toUpperCase();
        for (String prefix : getCustomerPrefixes()) {
            if (upperName.startsWith(prefix + "-") || upperName.startsWith(prefix)) {
                return prefix;
            }
        }
        
        log.warn("⚠️ Unknown customer prefix for device: {}", deviceName);
        return "UNKNOWN";
    }

    private String extractBranchName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return "";
        }
        
        String upperName = deviceName.toUpperCase();
        for (String prefix : getCustomerPrefixes()) {
            if (upperName.startsWith(prefix + "-")) {
                return deviceName.substring(prefix.length() + 1);
            }
        }
        
        return deviceName;
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asText() : null;
    }

    private Map<String, Object> flatten(JsonNode node) {
        Map<String, Object> result = new HashMap<>();
        if (node == null || node.isEmpty()) {
            return result;
        }
        
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode valNode = entry.getValue();
            result.put(entry.getKey(), valNode.isContainerNode() ? valNode.toString() : valNode.asText());
        }
        return result;
    }
}