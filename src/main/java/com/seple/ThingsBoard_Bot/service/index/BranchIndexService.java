package com.seple.ThingsBoard_Bot.service.index;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
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

import com.seple.ThingsBoard_Bot.client.UserAwareThingsBoardClient;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class BranchIndexService {

    private final ThingsBoardConfig thingsBoardConfig;
    private final CustomerRepository customerRepository;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final UserAwareThingsBoardClient userAwareThingsBoardClient;
    private final BranchAncestorPathRepository branchAncestorPathRepository;
    private final ConcurrentHashMap<String, List<DeviceIndexEntry>> indexByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAccess = new ConcurrentHashMap<>();

    // Per-entry TTL: cached index untouched for this long is evicted (10 minutes).
    private static final long INDEX_TTL_MS = 10 * 60 * 1000L;

    public BranchIndexService(ThingsBoardConfig thingsBoardConfig,
                              CustomerRepository customerRepository,
                              HierarchyNodeRepository hierarchyNodeRepository,
                              UserAwareThingsBoardClient userAwareThingsBoardClient,
                              BranchAncestorPathRepository branchAncestorPathRepository) {
        this.thingsBoardConfig = thingsBoardConfig;
        this.customerRepository = customerRepository;
        this.hierarchyNodeRepository = hierarchyNodeRepository;
        this.userAwareThingsBoardClient = userAwareThingsBoardClient;
        this.branchAncestorPathRepository = branchAncestorPathRepository;
    }

    public List<DeviceIndexEntry> getIndex(String userToken) {
        String key = cacheKey(userToken);
        List<DeviceIndexEntry> existing = indexByUser.get(key);
        if (existing != null && !existing.isEmpty()) {
            lastAccess.put(key, System.currentTimeMillis());
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
        boolean filtered = false;
        
        try {
            List<Map<String, String>> userDevices = userAwareThingsBoardClient.getUserDevices(userToken);
            if (userDevices != null && !userDevices.isEmpty()) {
                Set<String> authDeviceIds = userDevices.stream()
                        .map(d -> d.get("id"))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                
                nodes = nodes.stream()
                        .filter(node -> {
                            String devId = node.getTbDeviceId() != null ? node.getTbDeviceId().toString() : node.getNodeId();
                            return authDeviceIds.contains(devId);
                        })
                        .toList();
                log.info("Filtered branch index to {} authorized devices for token", nodes.size());
                filtered = true;
            }
        } catch (Exception e) {
            log.error("Failed to filter branch index via ThingsBoard API: {}", e.getMessage());
        }

        // Fallback: local zone-based ancestor path filtering
        if (!filtered) {
            String zoneName = resolveZoneName(userToken);
            if (zoneName != null) {
                log.info("Attempting local fallback filtering for zone: {}", zoneName);
                Optional<HierarchyNode> zoNodeOpt = hierarchyNodeRepository.findAll().stream()
                        .filter(node -> "ZO".equalsIgnoreCase(node.getNodeType()) && 
                                        (node.getDisplayName().equalsIgnoreCase(zoneName) || 
                                         node.getNodeId().toUpperCase().contains(zoneName.replace(" ", "_"))))
                        .findFirst();
                
                if (zoNodeOpt.isPresent()) {
                    String zoNodeId = zoNodeOpt.get().getNodeId();
                    List<BranchAncestorPath> ancestorPaths = branchAncestorPathRepository.findByCustomerId(customerId);
                    Set<String> branchIdsInZone = ancestorPaths.stream()
                            .filter(path -> path.getAncestorList().contains(zoNodeId))
                            .map(BranchAncestorPath::getBranchNodeId)
                            .collect(Collectors.toSet());
                    
                    nodes = nodes.stream()
                            .filter(node -> branchIdsInZone.contains(node.getNodeId()))
                            .toList();
                    log.info("Successfully filtered branch index to {} branches for zone: {}", nodes.size(), zoneName);
                } else {
                    log.warn("Could not find ZO node for zone: {} in database.", zoneName);
                }
            }
        }

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

        String key = cacheKey(userToken);
        indexByUser.put(key, new ArrayList<>(entries));
        lastAccess.put(key, System.currentTimeMillis());
        log.info("Indexed {} devices from local DB for user cache {}", entries.size(), key);
        return entries;
    }

    private String resolveZoneName(String userToken) {
        String firstName = JwtParserUtil.extractClaim(userToken, "firstName");
        String lastName = JwtParserUtil.extractClaim(userToken, "lastName");
        if (firstName != null && lastName != null) {
            String combined = (firstName + " " + lastName).toUpperCase();
            if (combined.contains("ZO ")) {
                return combined.substring(combined.indexOf("ZO ")).trim(); // e.g., "ZO HOWRAH"
            }
        }
        String sub = JwtParserUtil.extractClaim(userToken, "sub");
        if (sub != null && sub.contains("@")) {
            String prefix = sub.split("@")[0].toUpperCase();
            if (prefix.contains(".")) {
                String firstPart = prefix.split("\\.")[0];
                return "ZO " + firstPart; // e.g. "ZO HOWRAH"
            }
            return "ZO " + prefix;
        }
        return null;
    }

    public void invalidate(String userToken) {
        String key = cacheKey(userToken);
        indexByUser.remove(key);
        lastAccess.remove(key);
    }

    /**
     * Evict only stale entries (untouched beyond {@link #INDEX_TTL_MS}) instead of
     * flushing the entire cache. Bounds memory without penalizing active users.
     */
    @Scheduled(fixedDelayString = "${iotchatbot.thingsboard.sync-interval-seconds:60}000")
    public void periodicCleanup() {
        long cutoff = System.currentTimeMillis() - INDEX_TTL_MS;
        int before = indexByUser.size();
        lastAccess.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoff) {
                indexByUser.remove(entry.getKey());
                return true;
            }
            return false;
        });
        int evicted = before - indexByUser.size();
        if (evicted > 0) {
            log.info("[CACHE] Evicted {} stale branch-index entries; {} active.", evicted, indexByUser.size());
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
