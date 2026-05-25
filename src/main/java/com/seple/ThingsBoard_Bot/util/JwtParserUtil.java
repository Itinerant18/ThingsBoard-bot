package com.seple.ThingsBoard_Bot.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.Base64;

@Slf4j
public class JwtParserUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String extractCustomerId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode json = mapper.readTree(payload);
            
            // ThingsBoard JWT token holds customerId as a nested object or directly
            JsonNode customerIdNode = json.get("customerId");
            if (customerIdNode != null) {
                if (customerIdNode.isContainerNode() && customerIdNode.has("id")) {
                    return customerIdNode.get("id").asText(null);
                }
                return customerIdNode.asText(null);
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to parse JWT payload: {}", e.getMessage());
            return null;
        }
    }
}
