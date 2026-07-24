package com.seple.ThingsBoard_Bot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.client.ThingsBoardClient;
import com.seple.ThingsBoard_Bot.config.ThingsBoardConfig;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically pulls every device's latest telemetry + attributes from ThingsBoard (admin token)
 * and rebuilds the Redis snapshot the global/fleet handlers read. Single-branch queries already
 * live-fetch per request; global queries read Redis, so without this they go stale wherever the
 * webhook stream is absent (local dev) or drops fields. Off by default — enable only in the JVM
 * that serves chat reads via {@code iotchatbot.thingsboard.scheduled-sync-enabled=true}.
 *
 * <p>ponytail: naive per-device fetch loop — ~1 telemetry + 3 attribute calls per device per cycle.
 * Fine for the current ~150-device fleet at a multi-minute interval; switch to the bulk entity-query
 * API if the fleet or interval makes the call volume matter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledLiveSyncService {

    private static final List<String> ATTRIBUTE_SCOPES = List.of("SERVER_SCOPE", "SHARED_SCOPE", "CLIENT_SCOPE");

    private final ThingsBoardConfig config;
    private final ThingsBoardClient thingsBoardClient;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final RedisCacheService redisCacheService;
    private final ReplayService replayService;

    @Scheduled(fixedDelayString = "${iotchatbot.thingsboard.scheduled-sync-interval-ms:60000}",
               initialDelayString = "${iotchatbot.thingsboard.scheduled-sync-initial-delay-ms:5000}")
    public void syncAllCustomers() {
        if (!config.isScheduledSyncEnabled()) {
            return;
        }
        List<String> customerIds = hierarchyNodeRepository.findDistinctCustomerIds();
        log.info("[LIVE-SYNC] Starting scheduled live sync for {} customers", customerIds.size());
        for (String customerId : customerIds) {
            try {
                syncCustomer(customerId);
            } catch (Exception e) {
                log.error("[LIVE-SYNC] Sync failed for customer {}: {}", customerId, e.getMessage());
            }
        }
    }

    private void syncCustomer(String customerId) {
        // Reuse the replay lock so a scheduled sync and a manual replay can't rebuild the same
        // customer's Redis keys at once (would corrupt counters). Skip this cycle if one is running.
        if (!redisCacheService.tryAcquireReplayLock(customerId)) {
            log.info("[LIVE-SYNC] Rebuild already in progress for {} — skipping this cycle", customerId);
            return;
        }
        try {
            List<HierarchyNode> devices = hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true);
            Map<String, Map<String, String>> deviceStates = new HashMap<>();
            Map<String, String> deviceNodeIds = new HashMap<>();
            Map<String, String> deviceBranchNames = new HashMap<>();
            int fetched = 0;

            for (HierarchyNode node : devices) {
                if (node.getTbDeviceId() == null) {
                    continue; // no ThingsBoard device to poll
                }
                String deviceId = node.getTbDeviceId().toString();
                Map<String, String> fields = new HashMap<>();
                try {
                    Map<String, Object> telemetry = thingsBoardClient.getTelemetry(deviceId);
                    telemetry.forEach((k, v) -> fields.put(k, String.valueOf(v)));
                    for (String scope : ATTRIBUTE_SCOPES) {
                        Map<String, Object> attrs = thingsBoardClient.getAttributes(scope, deviceId);
                        attrs.forEach((k, v) -> fields.put(k, String.valueOf(v)));
                    }
                } catch (Exception e) {
                    log.warn("[LIVE-SYNC] Failed to fetch device {} ({}): {}", node.getDisplayName(), deviceId, e.getMessage());
                    continue;
                }
                if (fields.isEmpty()) {
                    continue;
                }
                deviceStates.put(deviceId, fields);
                deviceNodeIds.put(deviceId, node.getNodeId());
                deviceBranchNames.put(deviceId, node.getDisplayName());
                fetched++;
            }

            if (deviceStates.isEmpty()) {
                log.info("[LIVE-SYNC] No device data fetched for {} — leaving existing Redis snapshot intact", customerId);
                return;
            }
            replayService.rebuildRedisFromDeviceStates(customerId, deviceStates, deviceNodeIds, deviceBranchNames);
            log.info("[LIVE-SYNC] Synced {} devices for customer {}", fetched, customerId);
        } finally {
            redisCacheService.releaseReplayLock(customerId);
        }
    }
}
