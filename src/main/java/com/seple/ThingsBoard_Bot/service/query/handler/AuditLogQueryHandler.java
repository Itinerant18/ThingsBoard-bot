package com.seple.ThingsBoard_Bot.service.query.handler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.client.UserAwareThingsBoardClient;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.TbAuditLog;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

@Component
public class AuditLogQueryHandler implements AnswerHandler {

    private final UserAwareThingsBoardClient client;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
            .withZone(ZoneId.systemDefault());

    public AuditLogQueryHandler(UserAwareThingsBoardClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.AUDIT_LOG_QUERY;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        // Default to last 24 hours
        long endTime = System.currentTimeMillis();
        long startTime = endTime - (24 * 60 * 60 * 1000);
        
        List<TbAuditLog> logs = client.fetchAuditLogs(null, startTime, endTime, 20); // fetch top 20 for brief display
        
        if (logs == null || logs.isEmpty()) {
            return "No audit logs found for the last 24 hours.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here are the recent audit logs from the last 24 hours:\n\n");
        for (TbAuditLog log : logs) {
            String timeStr = FORMATTER.format(Instant.ofEpochMilli(log.getCreatedTime()));
            String user = log.getUserName() != null ? log.getUserName() : "System";
            String action = log.getActionType() != null ? log.getActionType() : "UNKNOWN_ACTION";
            String target = log.getEntityName() != null ? log.getEntityName() : (log.getEntityType() != null ? log.getEntityType() : "Entity");
            String status = log.getActionStatus();
            
            sb.append("- [").append(timeStr).append("] ").append(user).append(" performed **").append(action)
              .append("** on ").append(target).append(" (Status: ").append(status).append(")\n");
        }
        return sb.toString();
    }
}
