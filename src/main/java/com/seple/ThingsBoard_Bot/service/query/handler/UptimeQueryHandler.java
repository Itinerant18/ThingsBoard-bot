package com.seple.ThingsBoard_Bot.service.query.handler;

import com.seple.ThingsBoard_Bot.entity.DeviceEvent;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.repository.DeviceEventRepository;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class UptimeQueryHandler implements AnswerHandler {

    private final DeviceEventRepository deviceEventRepository;

    public UptimeQueryHandler(DeviceEventRepository deviceEventRepository) {
        this.deviceEventRepository = deviceEventRepository;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.UPTIME_QUERY;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        if (query.getTargetBranch() == null) {
            return "Please specify a branch to check its uptime (e.g. 'What is the uptime for LILUAH?').";
        }

        String branchNodeId = query.getTargetBranch().getIdentity().getTechnicalId();

        // Default to past 7 days
        Instant end = Instant.now();
        Instant start = end.minus(7, ChronoUnit.DAYS);

        // Get initial state at the start of the window
        Optional<DeviceEvent> lastEventBefore = deviceEventRepository.findLatestStateBefore(
                customerId, branchNodeId, "active", start);
        
        boolean isCurrentlyActive = false;
        if (lastEventBefore.isPresent()) {
            isCurrentlyActive = "true".equalsIgnoreCase(lastEventBefore.get().getNewValue());
        }

        // Get all state changes within the window
        List<DeviceEvent> changes = deviceEventRepository.findStateChanges(
                customerId, branchNodeId, "active", start, end);

        long totalDurationMs = Duration.between(start, end).toMillis();
        long uptimeMs = 0;

        Instant lastTransition = start;

        for (DeviceEvent change : changes) {
            boolean newState = "true".equalsIgnoreCase(change.getNewValue());
            if (isCurrentlyActive && !newState) {
                // Was active, just went inactive
                uptimeMs += Duration.between(lastTransition, change.getEventTime()).toMillis();
            }
            isCurrentlyActive = newState;
            lastTransition = change.getEventTime();
        }

        // Add remaining time if it was active until the end of the window
        if (isCurrentlyActive) {
            uptimeMs += Duration.between(lastTransition, end).toMillis();
        }

        double uptimePercentage = (double) uptimeMs / totalDurationMs * 100.0;
        
        return String.format("The overall uptime for **%s** over the last 7 days is **%.2f%%**.", 
                query.getTargetBranch().getIdentity().getBranchName(), uptimePercentage);
    }
}
