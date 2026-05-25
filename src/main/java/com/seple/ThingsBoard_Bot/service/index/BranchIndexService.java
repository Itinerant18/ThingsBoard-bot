package com.seple.ThingsBoard_Bot.service.index;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.config.ThingsBoardConfig;
import com.seple.ThingsBoard_Bot.model.dto.DeviceIndexEntry;
import com.seple.ThingsBoard_Bot.repository.CustomerRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.util.JwtParserUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BranchIndexService {

    private final ThingsBoardConfig thingsBoardConfig;
    private final CustomerRepository customerRepository;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final ConcurrentHashMap<String, List<DeviceIndexEntry>> indexByUser = new ConcurrentHashMap<>();

    public BranchIndexService(ThingsBoardConfig thingsBoardConfig,
                              CustomerRepository customerRepository,
                              HierarchyNodeRepository hierarchyNodeRepository) {
        this.thingsBoardConfig = thingsBoardConfig;
        this.customerRepository = customerRepository;
        this.hierarchyNodeRepository = hierarchyNodeRepository;
    }

    public List<DeviceIndexEntry> getIndex(String userToken) {
        String key = cacheKey(userToken);
        List<DeviceIndexEntry> existing = indexByUser.get(key);
        if (existing != null && !existing.isEmpty()) {
            return new ArrayList<>(existing);
        }
        List<DeviceIndexEntry> refreshed = refreshIndex(userToken);
        return new ArrayList<>(refreshed);
    }

    public List<DeviceIndexEntry> refreshIndex(String userToken) {
        String tbCustomerId = JwtParserUtil.extractCustomerId(userToken);
        String customerId = "BOI";
        if (tbCustomerId != null) {
            customerId = customerRepository.findByTbCustomerId(tbCustomerId)
                    .map(com.seple.ThingsBoard_Bot.entity.Customer::getCustomerId)
                    .orElse("BOI");
        }

        List<HierarchyNode> nodes = hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true);
        List<DeviceIndexEntry> entries = nodes.stream()
                .map(node -> {
                    String deviceId = node.getTbDeviceId() != null ? node.getTbDeviceId().toString() : node.getNodeId();
                    return DeviceIndexEntry.builder()
                            .deviceId(deviceId)
                            .branchName(node.getDisplayName())
                            .deviceType("default")
                            .aliases(aliases(node.getDisplayName()))
                            .indexedAt(System.currentTimeMillis())
                            .build();
                })
                .toList();

        indexByUser.put(cacheKey(userToken), new ArrayList<>(entries));
        log.info("Indexed {} devices from local DB for user cache {}", entries.size(), cacheKey(userToken));
        return entries;
    }

    public void invalidate(String userToken) {
        indexByUser.remove(cacheKey(userToken));
    }

    @Scheduled(fixedDelayString = "${iotchatbot.thingsboard.sync-interval-seconds:60}000")
    public void periodicCleanup() {
        if (indexByUser.size() > 5000) {
            log.warn("Branch index cache is large ({}). Clearing inactive cache entries.", indexByUser.size());
            indexByUser.clear();
        }
    }

    private List<String> aliases(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            return List.of();
        }
        String normalized = branchName.toUpperCase(Locale.ROOT)
                .replace("BRANCH ", "")
                .replace("BOI-", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        String compact = normalized.replace(" ", "");
        return new ArrayList<>(Set.of(branchName, normalized, compact));
    }

    private String cacheKey(String userToken) {
        return String.valueOf(userToken.hashCode());
    }
}
