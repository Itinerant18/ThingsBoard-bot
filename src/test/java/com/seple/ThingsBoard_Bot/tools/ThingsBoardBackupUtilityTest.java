package com.seple.ThingsBoard_Bot.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;

class ThingsBoardBackupUtilityTest {

    private ThingsBoardBackupUtility utility;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        utility = new ThingsBoardBackupUtility();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldFlattenStringifiedJsonAndKeepOriginal() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("simpleKey", "simpleValue");
        root.put("gatewayStatus", "{\"SYSTEM ON\":\"true\",\"battery_voltage\":14.0,\"list\":[1,2,3]}");

        JsonNode result = utility.flattenJsonNode(root);

        assertNotNull(result);
        assertEquals("simpleValue", result.get("simpleKey").asText());
        assertEquals("{\"SYSTEM ON\":\"true\",\"battery_voltage\":14.0,\"list\":[1,2,3]}", result.get("gatewayStatus").asText());
        
        // Flattened assertions
        assertEquals("true", result.get("gatewayStatus_SYSTEM ON").asText());
        assertEquals(14.0, result.get("gatewayStatus_battery_voltage").asDouble());
        
        // Nested array container should be serialized to a string
        assertEquals("[1,2,3]", result.get("gatewayStatus_list").asText());
    }

    @Test
    void shouldFlattenAlreadyParsedJsonObjectAndKeepOriginal() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode nestedObj = objectMapper.createObjectNode();
        nestedObj.put("dev_id", 358773400033916L);
        root.set("imei_id", nestedObj);

        JsonNode result = utility.flattenJsonNode(root);

        assertNotNull(result);
        // Original parsed object is preserved
        assertNotNull(result.get("imei_id"));
        assertTrue(result.get("imei_id").isObject());
        assertEquals(358773400033916L, result.get("imei_id").get("dev_id").asLong());
        
        // Flattened assertions
        assertEquals(358773400033916L, result.get("imei_id_dev_id").asLong());
    }
}
