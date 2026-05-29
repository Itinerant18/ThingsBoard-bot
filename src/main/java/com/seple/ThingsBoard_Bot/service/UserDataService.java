package com.seple.ThingsBoard_Bot.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import com.seple.ThingsBoard_Bot.config.ThingsBoardConfig;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.dto.DeviceIndexEntry;
import com.seple.ThingsBoard_Bot.service.index.BranchIndexService;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.query.IntentKeyProfileRegistry;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.repository.CustomerRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.util.JwtParserUtil;

import com.seple.ThingsBoard_Bot.client.UserAwareThingsBoardClient;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserDataService {

    private static final Logger log = LoggerFactory.getLogger(UserDataService.class);
    private final BranchSnapshotMapper branchSnapshotMapper;
    private final BranchIndexService branchIndexService;
    private final IntentKeyProfileRegistry intentKeyProfileRegistry;
    private final ThingsBoardConfig thingsBoardConfig;
    private final CustomerRepository customerRepository;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final RedisCacheService redisCacheService;
    private final UserAwareThingsBoardClient userAwareThingsBoardClient;
    private final BranchAncestorPathRepository branchAncestorPathRepository;

    // Keep an empty cache map to avoid breaking any dailyCacheMemoryWipe references
    private final ConcurrentHashMap<String, Object> userCacheMap = new ConcurrentHashMap<>();

    public UserDataService(BranchSnapshotMapper branchSnapshotMapper,
                           BranchIndexService branchIndexService,
                           IntentKeyProfileRegistry intentKeyProfileRegistry,
                           ThingsBoardConfig thingsBoardConfig,
                           CustomerRepository customerRepository,
                           HierarchyNodeRepository hierarchyNodeRepository,
                           RedisCacheService redisCacheService,
                           UserAwareThingsBoardClient userAwareThingsBoardClient,
                           BranchAncestorPathRepository branchAncestorPathRepository) {
        this.branchSnapshotMapper = branchSnapshotMapper;
        this.branchIndexService = branchIndexService;
        this.intentKeyProfileRegistry = intentKeyProfileRegistry;
        this.thingsBoardConfig = thingsBoardConfig;
        this.customerRepository = customerRepository;
        this.hierarchyNodeRepository = hierarchyNodeRepository;
        this.redisCacheService = redisCacheService;
        this.userAwareThingsBoardClient = userAwareThingsBoardClient;
        this.branchAncestorPathRepository = branchAncestorPathRepository;
    }

    // ==================== Public API ====================

    private List<HierarchyNode> getFilteredUserNodes(String userToken) {
        String customerId = resolveCustomerIdPrefix(userToken);
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
                log.info("Filtered nodes to {} authorized devices for token", nodes.size());
                filtered = true;
            } else {
                log.warn("ThingsBoard returned empty device list for user token.");
            }
        } catch (Exception e) {
            log.error("Failed to filter nodes via ThingsBoard API (e.g. token expired): {}", e.getMessage());
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
                    log.info("Successfully filtered nodes to {} branches for zone: {}", nodes.size(), zoneName);
                } else {
                    log.warn("Could not find ZO node for zone: {} in database.", zoneName);
                }
            }
        }
        
        return nodes;
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

    /**
     * Get all device data for the logged-in user.
     * Fetches metadata from DB and real-time state from Redis.
     */
    public List<Map<String, Object>> getUserDevicesData(String userToken) {
        String customerId = resolveCustomerIdPrefix(userToken);
        List<HierarchyNode> nodes = getFilteredUserNodes(userToken);
        List<Map<String, Object>> devicesData = new ArrayList<>();
        
        for (HierarchyNode node : nodes) {
            Map<String, Object> deviceMap = new HashMap<>();
            String deviceId = node.getTbDeviceId() != null ? node.getTbDeviceId().toString() : node.getNodeId();
            deviceMap.put("device_id", deviceId);
            deviceMap.put("device_name", node.getDisplayName());
            deviceMap.put("deviceName", node.getDisplayName());
            deviceMap.put("formattedBranchName", node.getDisplayName());
            deviceMap.put("branchName", node.getDisplayName());
            deviceMap.put("device_type", "default");
            
            // Enrich with state from Redis
            Map<Object, Object> redisState = redisCacheService.getDeviceState(customerId, deviceId);
            if (redisState != null) {
                redisState.forEach((k, v) -> deviceMap.put(String.valueOf(k), v));
            }
            
            devicesData.add(deviceMap);
        }
        return devicesData;
    }

    /**
     * Get a specific device data by ID from local DB + Redis.
     */
    public Map<String, Object> getUserDeviceDataById(String userToken, String deviceId) {
        String customerId = resolveCustomerIdPrefix(userToken);
        List<HierarchyNode> nodes = getFilteredUserNodes(userToken);
        
        HierarchyNode matchedNode = null;
        for (HierarchyNode node : nodes) {
            String nodeDevId = node.getTbDeviceId() != null ? node.getTbDeviceId().toString() : node.getNodeId();
            if (deviceId.equals(nodeDevId)) {
                matchedNode = node;
                break;
            }
        }
        
        if (matchedNode == null) {
            log.warn("⚠️ Device {} not found in user local DB scope", deviceId);
            return new HashMap<>();
        }
        
        Map<String, Object> deviceMap = new HashMap<>();
        deviceMap.put("device_id", deviceId);
        deviceMap.put("device_name", matchedNode.getDisplayName());
        deviceMap.put("deviceName", matchedNode.getDisplayName());
        deviceMap.put("formattedBranchName", matchedNode.getDisplayName());
        deviceMap.put("branchName", matchedNode.getDisplayName());
        deviceMap.put("device_type", "default");
        
        Map<Object, Object> redisState = redisCacheService.getDeviceState(customerId, deviceId);
        if (redisState != null) {
            redisState.forEach((k, v) -> deviceMap.put(String.valueOf(k), v));
        }
        
        log.info("✅ Found device {} in user local DB scope", deviceId);
        return deviceMap;
    }

    /**
     * Get a flattened map of all user device data, suitable for the ChatService context.
     */
    public Map<String, Object> getUserDevicesDataFlat(String userToken) {
        List<Map<String, Object>> allDevices = getUserDevicesData(userToken);
        Map<String, Object> flat = new HashMap<>();

        if (allDevices.size() == 1) {
            flat.putAll(allDevices.get(0));
        } else {
            flat.put("total_devices", allDevices.size());
            for (Map<String, Object> deviceData : allDevices) {
                String name = deviceData.getOrDefault("device_name", "").toString().trim();
                if (name.isBlank()) {
                    name = deviceData.getOrDefault("device_id", "unknown").toString();
                }
                for (Map.Entry<String, Object> entry : deviceData.entrySet()) {
                    flat.put(name + "." + entry.getKey(), entry.getValue());
                }
            }
        }

        flat.put("fetched_at", System.currentTimeMillis());
        return flat;
    }

    /**
     * Get the user's device list (id + name only).
     */
    public List<Map<String, String>> getUserDevicesList(String userToken) {
        List<HierarchyNode> nodes = getFilteredUserNodes(userToken);
        List<Map<String, String>> result = new ArrayList<>();
        
        for (HierarchyNode node : nodes) {
            Map<String, String> basicInfo = new HashMap<>();
            String deviceId = node.getTbDeviceId() != null ? node.getTbDeviceId().toString() : node.getNodeId();
            basicInfo.put("id", deviceId);
            basicInfo.put("name", node.getDisplayName());
            basicInfo.put("type", "default");
            result.add(basicInfo);
        }
        return result;
    }

    public List<BranchSnapshot> getUserBranchSnapshots(String userToken) {
        List<Map<String, Object>> rawDevices = getUserDevicesData(userToken);
        List<BranchSnapshot> snapshots = new ArrayList<>();
        for (Map<String, Object> rawDevice : rawDevices) {
            snapshots.add(branchSnapshotMapper.map(rawDevice));
        }
        return snapshots;
    }

    public List<BranchSnapshot> getUserBranchIndexSnapshots(String userToken) {
        List<BranchSnapshot> snapshots = new ArrayList<>();
        for (DeviceIndexEntry entry : branchIndexService.getIndex(userToken)) {
            Map<String, Object> minimal = new HashMap<>();
            minimal.put("device_id", entry.getDeviceId());
            minimal.put("device_name", entry.getBranchName());
            minimal.put("deviceName", entry.getBranchName());
            minimal.put("formattedBranchName", entry.getBranchName());
            minimal.put("branchName", entry.getBranchName());
            minimal.put("device_type", entry.getDeviceType());
            snapshots.add(branchSnapshotMapper.map(minimal));
        }
        return snapshots;
    }

    public BranchSnapshot getBranchSnapshotForIntent(String userToken, String branchTechnicalId, QueryIntent intent) {
        if (branchTechnicalId == null || branchTechnicalId.isBlank()) {
            return null;
        }

        List<DeviceIndexEntry> index = branchIndexService.getIndex(userToken);
        String wanted = normalizeKey(branchTechnicalId);
        DeviceIndexEntry matched = null;
        for (DeviceIndexEntry entry : index) {
            if (normalizeKey(entry.getBranchName()).equals(wanted)
                    || (entry.getAliases() != null && entry.getAliases().stream().map(this::normalizeKey).anyMatch(wanted::equals))) {
                matched = entry;
                break;
            }
        }
        if (matched == null) {
            return null;
        }

        String customerId = resolveCustomerIdPrefix(userToken);
        Map<String, Object> raw = new HashMap<>();
        raw.put("device_id", matched.getDeviceId());
        raw.put("device_name", matched.getBranchName());
        raw.put("deviceName", matched.getBranchName());
        raw.put("formattedBranchName", matched.getBranchName());
        raw.put("branchName", matched.getBranchName());
        raw.put("device_type", matched.getDeviceType());

        // Fetch state from Redis cache
        Map<Object, Object> redisState = redisCacheService.getDeviceState(customerId, matched.getDeviceId());
        if (redisState != null) {
            redisState.forEach((k, v) -> raw.put(String.valueOf(k), v));
        }

        return branchSnapshotMapper.map(raw);
    }

    public boolean isTwoStepFetchEnabled() {
        return thingsBoardConfig.isTwoStepFetchEnabled();
    }

    // ==================== Raw Data API ====================

    public Object getRawAttributes(String userToken, String scope, String deviceId) {
        String customerId = resolveCustomerIdPrefix(userToken);
        return redisCacheService.getDeviceState(customerId, deviceId);
    }

    public Object getRawTelemetry(String userToken, String deviceId) {
        String customerId = resolveCustomerIdPrefix(userToken);
        return redisCacheService.getDeviceState(customerId, deviceId);
    }

    /**
     * Invalidate cache for a specific user.
     */
    public void invalidateUserCache(String userToken) {
        branchIndexService.invalidate(userToken);
        log.info("🗑️ Cache invalidated for user");
    }

    // ==================== Helpers ====================

    public String resolveCustomerIdPrefix(String userToken) {
        String tbCustomerId = JwtParserUtil.extractCustomerId(userToken);
        if (tbCustomerId == null) {
            log.warn("Could not extract customer UUID from token. Defaulting to BOI.");
            return "BOI";
        }
        return customerRepository.findByTbCustomerId(tbCustomerId)
                .map(com.seple.ThingsBoard_Bot.entity.Customer::getCustomerId)
                .orElseGet(() -> {
                    log.warn("Customer mapping not found for UUID: {}. Defaulting to BOI.", tbCustomerId);
                    return "BOI";
                });
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase()
                .replace("BOI-", "")
                .replace("BRANCH ", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Clear all user cache daily at 1:00 AM IST to free up memory.
     */
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Kolkata")
    public void dailyCacheMemoryWipe() {
        log.info("🧹 [1:00 AM IST] Wiping all user device cache to free memory...");
        int sizeBefore = userCacheMap.size();
        userCacheMap.clear();
        log.info("🧹 Cache wipe complete. Freed {} user sessions.", sizeBefore);
    }
}
