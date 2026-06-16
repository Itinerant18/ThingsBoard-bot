package com.seple.ThingsBoard_Bot.service.query;

import com.seple.ThingsBoard_Bot.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisQueryService {

    private final RedisCacheService redisCacheService;
    private final com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository hierarchyNodeRepository;

    public long getGlobalCounter(String customerId, String counterName) {
        if ("ALL".equals(customerId)) {
            long total = 0;
            for (String custId : hierarchyNodeRepository.findDistinctCustomerIds()) {
                total += getGlobalCounter(custId, counterName);
            }
            return total;
        }
        Map<Object, Object> counters = redisCacheService.getGlobalCounters(customerId);
        if (counters != null && counters.containsKey(counterName)) {
            try {
                return Long.parseLong(String.valueOf(counters.get(counterName)));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public long getNodeCounter(String customerId, String nodeId, String counterName) {
        if ("ALL".equals(customerId)) {
            customerId = hierarchyNodeRepository.findById(nodeId)
                    .map(com.seple.ThingsBoard_Bot.entity.HierarchyNode::getCustomerId)
                    .orElse("BOI");
        }
        Map<Object, Object> counters = redisCacheService.getNodeCounters(customerId, nodeId);
        if (counters != null && counters.containsKey(counterName)) {
            try {
                return Long.parseLong(String.valueOf(counters.get(counterName)));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public Map<Object, Object> getDeviceState(String customerId, String deviceId) {
        return redisCacheService.getDeviceState(customerId, deviceId);
    }
}
