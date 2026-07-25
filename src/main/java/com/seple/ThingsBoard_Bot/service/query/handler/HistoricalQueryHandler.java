package com.seple.ThingsBoard_Bot.service.query.handler;

import com.seple.ThingsBoard_Bot.entity.DeviceEvent;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.repository.DeviceEventRepository;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class HistoricalQueryHandler implements AnswerHandler {

    private final DeviceEventRepository deviceEventRepository;

    public HistoricalQueryHandler(DeviceEventRepository deviceEventRepository) {
        this.deviceEventRepository = deviceEventRepository;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.HISTORICAL_OFFLINE_QUERY || 
               intent == QueryIntent.DAY_OVER_DAY_TREND;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        if (customerId == null) {
            return "Unable to determine customer context for historical query.";
        }

        Instant now = Instant.now();

        if (query.getIntent() == QueryIntent.HISTORICAL_OFFLINE_QUERY) {
            // Default to past 24 hours
            Instant start = now.minus(24, ChronoUnit.HOURS);
            List<DeviceEvent> offlineEvents = deviceEventRepository.findOfflineEvents(customerId, start, now);

            if (offlineEvents.isEmpty()) {
                return "No devices went offline in the past 24 hours.";
            }

            long uniqueDevices = offlineEvents.stream()
                .map(DeviceEvent::getBranchNodeId)
                .distinct()
                .count();

            StringBuilder sb = new StringBuilder();
            sb.append("In the past 24 hours, **").append(uniqueDevices).append("** devices/branches went offline.\n\n");
            
            // List up to 10 for brevity
            sb.append("Recent offline events:\n");
            offlineEvents.stream().limit(10).forEach(event -> {
                sb.append("- ").append(event.getBranchNodeId())
                  .append(" at ").append(event.getEventTime().toString()).append("\n");
            });

            if (uniqueDevices > 10) {
                sb.append("\n*(Showing top 10 most recent events)*");
            }
            
            return sb.toString();
        } 
        
        if (query.getIntent() == QueryIntent.DAY_OVER_DAY_TREND) {
            // Simple net change comparison
            Instant startToday = now.minus(24, ChronoUnit.HOURS);
            Instant startYesterday = startToday.minus(24, ChronoUnit.HOURS);
            
            // We can count how many devices went offline today vs yesterday
            List<DeviceEvent> offlineToday = deviceEventRepository.findOfflineEvents(customerId, startToday, now);
            List<DeviceEvent> offlineYesterday = deviceEventRepository.findOfflineEvents(customerId, startYesterday, startToday);
            
            long todayCount = offlineToday.stream().map(DeviceEvent::getBranchNodeId).distinct().count();
            long yesterdayCount = offlineYesterday.stream().map(DeviceEvent::getBranchNodeId).distinct().count();
            
            long diff = todayCount - yesterdayCount;
            String direction = diff > 0 ? "increased by " + diff : (diff < 0 ? "decreased by " + Math.abs(diff) : "remained exactly the same");
            
            return String.format(
                "Day-over-day trend for offline devices %s compared to yesterday.\n\n" +
                "- **Past 24 hours**: %d devices went offline\n" +
                "- **Previous 24 hours**: %d devices went offline", 
                direction, todayCount, yesterdayCount);
        }

        return "Query not fully supported yet.";
    }
}
