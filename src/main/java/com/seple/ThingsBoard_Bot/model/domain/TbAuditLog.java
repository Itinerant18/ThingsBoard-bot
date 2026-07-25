package com.seple.ThingsBoard_Bot.model.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TbAuditLog {
    private String id;
    private long createdTime;
    private String entityId;
    private String entityType;
    private String entityName;
    private String userId;
    private String userName;
    private String actionType;
    private String actionStatus;
    private JsonNode actionData;
}
