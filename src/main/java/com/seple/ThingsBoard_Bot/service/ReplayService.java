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

    public void replayForCustomer(String customerId, Instant startTime, Instant endTime) {
        log.info("[REPLAY] Starting replay for customer: {}, from: {}, to: {}", customerId, startTime, endTime);

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
        log.info("[REPLAY] Found {} events to replay for customer: {}", events.size(), customerId);

        int count = 0;
        for (DeviceEvent event : events) {
            String branchNodeId = event.getBranchNodeId();
            String deviceId = branchNameToDeviceId.getOrDefault(branchNodeId, branchNodeId);
            String branchName = branchNameToDisplayName.getOrDefault(branchNodeId, branchNodeId);

            // Update state in Redis
            redisCacheService.updateDeviceState(customerId, deviceId, event.getField(), event.getNewValue());
            redisCacheService.setDeviceMeta(customerId, deviceId, branchNodeId, branchName);

            // Fetch ancestors from cache
            List<String> ancestors = ancestorPathCache.getAncestors(customerId, branchNodeId);
            if (ancestors.isEmpty()) {
                // Generate default ancestors if not in DB/cache
                ancestors = List.of(customerId + "_HO", customerId + "_ZO_" + branchNodeId, branchNodeId);
                ancestorPathCache.cacheAncestors(customerId, branchNodeId, ancestors);
            }

            // Update hierarchy counters
            luaScriptService.executeUpdateCounters(
                    customerId,
                    deviceId,
                    branchNodeId,
                    ancestors,
                    event.getField(),
                    event.getNewValue(),
                    event.getPrevValue()
            );
            count++;
        }

        log.info("[REPLAY] Replay complete. Processed {} events.", count);
    }
}
