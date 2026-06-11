package com.seple.ThingsBoard_Bot.service;

import com.seple.ThingsBoard_Bot.entity.DeviceEvent;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.DeviceEventRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    private final DeviceEventRepository deviceEventRepository;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final AncestorPathService ancestorPathService;
    private final RedisCacheService redisCacheService;
    private final LuaScriptService luaScriptService;
    private final AncestorPathCache ancestorPathCache;

    /**
     * Optional: only present in a JVM that hosts the consumer listener. Field-injected (not in the
     * constructor) so existing wiring/tests are unchanged. Used to pause local ingestion during a
     * replay so live events don't race the rebuild.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry listenerRegistry;

    public void replayForCustomer(String customerId, Instant startTime, Instant endTime) {
        if ("ALL".equalsIgnoreCase(customerId)) {
            log.info("[REPLAY] Requested replay for ALL customers");
            List<String> customerIds = hierarchyNodeRepository.findDistinctCustomerIds();
            log.info("[REPLAY] Found {} distinct customers to replay: {}", customerIds.size(), customerIds);
            for (String cid : customerIds) {
                try {
                    replaySingleCustomer(cid, startTime, endTime);
                } catch (Exception e) {
                    log.error("[REPLAY] Replay failed for customer: {}", cid, e);
                }
            }
        } else {
            replaySingleCustomer(customerId, startTime, endTime);
        }
    }

    private void replaySingleCustomer(String customerId, Instant startTime, Instant endTime) {
        // Concurrency guard: refuse overlapping replays for the same customer, which would clear
        // and rebuild the same Redis keys simultaneously and corrupt counters (audit #12).
        if (!redisCacheService.tryAcquireReplayLock(customerId)) {
            log.warn("[REPLAY] Replay already in progress for customer {} — skipping concurrent run", customerId);
            throw new IllegalStateException("Replay already in progress for customer " + customerId);
        }
        boolean pausedConsumer = pauseConsumer();
        try {
            doReplaySingleCustomer(customerId, startTime, endTime);
        } finally {
            if (pausedConsumer) {
                resumeConsumer();
            }
            redisCacheService.releaseReplayLock(customerId);
        }
    }

    private void doReplaySingleCustomer(String customerId, Instant startTime, Instant endTime) {
        log.info("[REPLAY] Starting optimized in-memory replay for customer: {}, from: {}, to: {}", customerId, startTime, endTime);

        // Step 1: Clear Redis cache for this customer
        redisCacheService.clearCustomerCache(customerId);

        // Step 2: Reload ancestor paths from DB into cache
        ancestorPathService.reloadForCustomer(customerId);

        // Step 3: Fetch all branches for mapping
        List<HierarchyNode> branches = hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true);
        Map<String, String> branchNameToDeviceId = new HashMap<>();
        Map<String, String> branchNameToDisplayName = new HashMap<>();
        for (HierarchyNode node : branches) {
            String devId = node.getTbDeviceId() != null ? node.getTbDeviceId().toString() : node.getNodeId();
            branchNameToDeviceId.put(node.getNodeId(), devId);
            branchNameToDeviceId.put(node.getDisplayName(), devId);
            branchNameToDisplayName.put(node.getNodeId(), node.getDisplayName());
            branchNameToDisplayName.put(node.getDisplayName(), node.getDisplayName());
        }

        // Step 4: Stream/Fetch events sorted by eventTime ASC
        List<DeviceEvent> events = deviceEventRepository.streamByCustomerIdAndTimeRange(customerId, startTime, endTime);
        log.info("[REPLAY] Found {} events for customer: {}", events.size(), customerId);

        // Step 5: Aggregate states in memory
        Map<String, Map<String, String>> deviceStates = new HashMap<>();
        Map<String, String> deviceNodeIds = new HashMap<>();
        Map<String, String> deviceBranchNames = new HashMap<>();

        for (DeviceEvent event : events) {
            String branchNodeId = event.getBranchNodeId();
            String deviceId = branchNameToDeviceId.getOrDefault(branchNodeId, branchNodeId);
            String branchName = branchNameToDisplayName.getOrDefault(branchNodeId, branchNodeId);

            deviceNodeIds.put(deviceId, branchNodeId);
            deviceBranchNames.put(deviceId, branchName);

            String value = event.getNewValue();
            if (event.getRawPayload() != null && event.getRawPayload().containsKey("value")) {
                Object rawVal = event.getRawPayload().get("value");
                if (rawVal != null) {
                    value = String.valueOf(rawVal);
                }
            }
            deviceStates.computeIfAbsent(deviceId, k -> new HashMap<>())
                        .put(event.getField(), value);
        }

        // Step 6: Compute node and global counters in memory
        Map<String, Map<String, Integer>> nodeCounters = new HashMap<>();
        Map<String, Integer> globalCounters = new HashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : deviceStates.entrySet()) {
            String deviceId = entry.getKey();
            String branchNodeId = deviceNodeIds.get(deviceId);
            if (branchNodeId == null) continue;

            List<String> ancestors = ancestorPathCache.getAncestors(customerId, branchNodeId);
            if (ancestors.isEmpty()) {
                // Generate default ancestors if not in DB/cache
                ancestors = List.of(customerId + "_HO", customerId + "_ZO_" + branchNodeId, branchNodeId);
                ancestorPathCache.cacheAncestors(customerId, branchNodeId, ancestors);
            }

            Map<String, String> fields = entry.getValue();
            for (Map.Entry<String, String> fieldEntry : fields.entrySet()) {
                String field = fieldEntry.getKey();
                String value = fieldEntry.getValue();

                if (isStatusField(field) && isOnlineState(value)) {
                    // Increment global counter
                    globalCounters.put(field, globalCounters.getOrDefault(field, 0) + 1);

                    // Increment ancestor counters
                    for (String ancestorNodeId : ancestors) {
                        nodeCounters.computeIfAbsent(ancestorNodeId, k -> new HashMap<>())
                                    .put(field, nodeCounters.get(ancestorNodeId).getOrDefault(field, 0) + 1);
                    }
                }
            }
        }

        // Step 7: Push aggregated states & counters to Redis in a single pipelined batch
        log.info("[REPLAY] Writing pipelined bulk data to Redis. Devices={}, NodeCounters={}", 
                 deviceStates.size(), nodeCounters.size());
        redisCacheService.writeBulkData(customerId, deviceStates, deviceNodeIds, deviceBranchNames, nodeCounters, globalCounters);

        log.info("[REPLAY] Replay complete for customer: {}", customerId);
    }

    /** Stops the local event consumer during replay so live events don't race the rebuild. */
    private boolean pauseConsumer() {
        if (listenerRegistry == null) {
            return false;
        }
        var container = listenerRegistry.getListenerContainer("eventListener");
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("[REPLAY] Paused event consumer for the duration of the replay");
            return true;
        }
        return false;
    }

    private void resumeConsumer() {
        if (listenerRegistry == null) {
            return;
        }
        var container = listenerRegistry.getListenerContainer("eventListener");
        if (container != null && !container.isRunning()) {
            container.start();
            log.info("[REPLAY] Resumed event consumer after replay");
        }
    }

    private boolean isStatusField(String field) {
        return field != null && (field.contains("status") || field.contains("Status") ||
               field.equals("gateway_status") || field.equals("online"));
    }

    private boolean isOnlineState(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        return lower.equals("online") || lower.equals("on") || lower.equals("1") || lower.equals("true");
    }
}
