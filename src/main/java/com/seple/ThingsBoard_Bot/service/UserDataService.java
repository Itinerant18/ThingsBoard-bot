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
    private final com.seple.ThingsBoard_Bot.config.SecurityProperties securityProperties;

    // Keep an empty cache map to avoid breaking any dailyCacheMemoryWipe references
    private final ConcurrentHashMap<String, Object> userCacheMap = new ConcurrentHashMap<>();

    private final Map<String, CachedHierarchy> hierarchyCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedHierarchy {
        final List<HierarchyNode> allNodes;
        final List<BranchAncestorPath> ancestorPaths;
        final long cachedAt;

        CachedHierarchy(List<HierarchyNode> allNodes, List<BranchAncestorPath> ancestorPaths) {
            this.allNodes = allNodes;
            this.ancestorPaths = ancestorPaths;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > 300_000; // 5 minutes TTL
        }
    }

    private CachedHierarchy getCachedHierarchy(String customerId) {
        CachedHierarchy cached = hierarchyCache.get(customerId);
        if (cached == null || cached.isExpired()) {
            List<HierarchyNode> allNodes;
            List<BranchAncestorPath> ancestorPaths;
            if ("ALL".equals(customerId)) {
                allNodes = hierarchyNodeRepository.findAll();
                ancestorPaths = branchAncestorPathRepository.findAll();
            } else {
                allNodes = hierarchyNodeRepository.findByCustomerId(customerId);
                ancestorPaths = branchAncestorPathRepository.findByCustomerId(customerId);
            }
            cached = new CachedHierarchy(allNodes, ancestorPaths);
            hierarchyCache.put(customerId, cached);
        }
        return cached;
    }

    public UserDataService(BranchSnapshotMapper branchSnapshotMapper,
                           BranchIndexService branchIndexService,
                           IntentKeyProfileRegistry intentKeyProfileRegistry,
                           ThingsBoardConfig thingsBoardConfig,
                           CustomerRepository customerRepository,
                           HierarchyNodeRepository hierarchyNodeRepository,
                           RedisCacheService redisCacheService,
                           UserAwareThingsBoardClient userAwareThingsBoardClient,
                           BranchAncestorPathRepository branchAncestorPathRepository,
                           com.seple.ThingsBoard_Bot.config.SecurityProperties securityProperties) {
        this.branchSnapshotMapper = branchSnapshotMapper;
        this.branchIndexService = branchIndexService;
        this.intentKeyProfileRegistry = intentKeyProfileRegistry;
        this.thingsBoardConfig = thingsBoardConfig;
        this.customerRepository = customerRepository;
        this.hierarchyNodeRepository = hierarchyNodeRepository;
        this.redisCacheService = redisCacheService;
        this.userAwareThingsBoardClient = userAwareThingsBoardClient;
        this.branchAncestorPathRepository = branchAncestorPathRepository;
        this.securityProperties = securityProperties;
    }

    // ==================== Public API ====================

    private static final List<String> REGIONAL_PREFIXES = List.of("FGMO", "LHO", "ZO", "CO", "RO", "RBO", "NBG");

    /** Data older than this is flagged stale. Override with -Diotchatbot.staleness.threshold.ms. */
    private static final long STALENESS_THRESHOLD_MS = Long.getLong("iotchatbot.staleness.threshold.ms", 600_000L);

    /** Adds {@code data_as_of} / {@code data_stale} from the device's lastUpdatedAt stamp (audit #15). */
    private static void enrichStaleness(Map<String, Object> deviceMap) {
        Object lastUpdated = deviceMap.get("lastUpdatedAt");
        if (lastUpdated == null) {
            return;
        }
        try {
            long ts = Long.parseLong(String.valueOf(lastUpdated));
            deviceMap.put("data_as_of", java.time.Instant.ofEpochMilli(ts).toString());
            deviceMap.put("data_stale", (System.currentTimeMillis() - ts) > STALENESS_THRESHOLD_MS);
        } catch (NumberFormatException ignored) {
            // non-numeric stamp; leave unflagged
        }
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }

    private List<HierarchyNode> getFilteredUserNodes(String userToken) {
        boolean isTenantAdmin = JwtParserUtil.hasScope(userToken, "TENANT_ADMIN");
        String customerId = resolveCustomerIdPrefix(userToken);
        CachedHierarchy cached = getCachedHierarchy(customerId);
        
        List<HierarchyNode> nodes = cached.allNodes.stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsLeaf()))
                .collect(Collectors.toList());
        
        if (isTenantAdmin) {
            return nodes; // Bypass ThingsBoard filter and regional filtering for tenant admin!
        }
        
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
            }
        } catch (Exception e) {
            log.error("Failed to filter nodes via ThingsBoard API (e.g. token expired): {}", e.getMessage());
        }
        
        // ALWAYS apply local regional ancestor path filtering if the token resolves to a specific region/zone
        String regionName = resolveRegionName(userToken);
        if (regionName != null) {
            log.info("Applying regional filtering for region: {}", regionName);
            String normalizedRegion = normalizeName(regionName);
            
            Optional<HierarchyNode> regionNodeOpt = cached.allNodes.stream()
                    .filter(node -> !Boolean.TRUE.equals(node.getIsLeaf()) && 
                                    (normalizeName(node.getDisplayName()).equals(normalizedRegion) || 
                                     normalizeName(node.getNodeId()).contains(normalizedRegion)))
                    .findFirst();
            
            if (regionNodeOpt.isPresent()) {
                String regionNodeId = regionNodeOpt.get().getNodeId();
                List<BranchAncestorPath> ancestorPaths = cached.ancestorPaths;
                Set<String> branchIdsInRegion = ancestorPaths.stream()
                        .filter(path -> path.getAncestorList().contains(regionNodeId))
                        .map(BranchAncestorPath::getBranchNodeId)
                        .collect(Collectors.toSet());
                
                nodes = nodes.stream()
                        .filter(node -> branchIdsInRegion.contains(node.getNodeId()))
                        .toList();
                log.info("Successfully filtered nodes to {} branches for region: {}", nodes.size(), regionName);
            } else {
                log.warn("Could not find matching hierarchy node for region: {} (normalized: {}) in database.", regionName, normalizedRegion);
            }
        }
        
        return nodes;
    }

    private String resolveRegionName(String userToken) {
        String firstName = JwtParserUtil.extractClaim(userToken, "firstName");
        String lastName = JwtParserUtil.extractClaim(userToken, "lastName");
        if (firstName != null && lastName != null) {
            String combined = (firstName + " " + lastName).toUpperCase();
            for (String prefix : REGIONAL_PREFIXES) {
                if (combined.contains(prefix + " ")) {
                    return combined.substring(combined.indexOf(prefix + " ")).trim();
                }
            }
        }
        String sub = JwtParserUtil.extractClaim(userToken, "sub");
        if (sub != null && sub.contains("@")) {
            String prefix = sub.split("@")[0].toUpperCase();
            if (prefix.contains(".")) {
                String firstPart = prefix.split("\\.")[0];
                for (String p : REGIONAL_PREFIXES) {
                    if (prefix.startsWith(p + ".")) {
                        return prefix.replace(".", " ");
                    }
                }
                return "ZO " + firstPart;
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
            Map<Object, Object> redisState = redisCacheService.getDeviceState(node.getCustomerId(), deviceId);
            if (redisState != null) {
                redisState.forEach((k, v) -> deviceMap.put(String.valueOf(k), v));
            }
            enrichStaleness(deviceMap);

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
        
        Map<Object, Object> redisState = redisCacheService.getDeviceState(matchedNode.getCustomerId(), deviceId);
        if (redisState != null) {
            redisState.forEach((k, v) -> deviceMap.put(String.valueOf(k), v));
        }
        enrichStaleness(deviceMap);

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
        if ("ALL".equals(customerId)) {
            customerId = matched.getCustomerId();
        }
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
        enrichStaleness(raw);

        return branchSnapshotMapper.map(raw);
    }

    public String detectUnauthorizedBranchName(String question, String userToken) {
        String customerId = resolveCustomerIdPrefix(userToken);
        CachedHierarchy cached = getCachedHierarchy(customerId);
        
        List<HierarchyNode> allNodes = cached.allNodes.stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsLeaf()))
                .collect(Collectors.toList());
                
        List<HierarchyNode> authNodes = getFilteredUserNodes(userToken);
        
        log.info("[SCOPING] detectUnauthorizedBranchName for customerId={}, allNodes.size={}, authNodes.size={}", 
                 customerId, allNodes.size(), authNodes.size());
        
        java.util.Set<String> authNames = new java.util.HashSet<>();
        for (HierarchyNode node : authNodes) {
            authNames.add(normalizeKey(node.getDisplayName()));
            authNames.add(normalizeKey(node.getNodeId()));
        }
        
        String normalizedQuestion = normalizeKey(question);
        log.info("[SCOPING] normalizedQuestion: '{}'", normalizedQuestion);

        
        // Sort allNodes by name length descending to match longest name first
        List<HierarchyNode> sortedNodes = allNodes.stream()
                .sorted((a, b) -> Integer.compare(b.getDisplayName().length(), a.getDisplayName().length()))
                .toList();
                
        log.info("[SCOPING] authNames: {}", authNames);
        
        for (HierarchyNode node : sortedNodes) {
            String normName = normalizeKey(node.getDisplayName());
            String normId = normalizeKey(node.getNodeId());
            
            // Check if the name or ID is contained in the question
            if ((normName.length() >= 4 && normalizedQuestion.contains(normName)) || 
                (normId.length() >= 5 && normalizedQuestion.contains(normId))) {
                
                boolean nameAuth = authNames.contains(normName);
                boolean idAuth = authNames.contains(normId);
                
                log.info("[SCOPING] Matched branch in question: node.displayName='{}', nodeId='{}', normName='{}', normId='{}', nameAuth={}, idAuth={}", 
                         node.getDisplayName(), node.getNodeId(), normName, normId, nameAuth, idAuth);
                
                // If this matched branch is NOT in the user's authorized nodes, return its display name
                if (!nameAuth && !idAuth) {
                    log.info("[SCOPING] Rejecting unauthorized query for branch display name: '{}'", node.getDisplayName());
                    return node.getDisplayName();
                }
            }
        }
        log.info("[SCOPING] No unauthorized branch name detected in question.");
        return null;
    }

    public boolean isTwoStepFetchEnabled() {
        return thingsBoardConfig.isTwoStepFetchEnabled();
    }

    // ==================== Raw Data API ====================

    public Object getRawAttributes(String userToken, String scope, String deviceId) {
        String customerId = resolveCustomerIdPrefix(userToken);
        if ("ALL".equals(customerId)) {
            customerId = findCustomerIdForDevice(deviceId);
        }
        return redisCacheService.getDeviceState(customerId, deviceId);
    }

    public Object getRawTelemetry(String userToken, String deviceId) {
        String customerId = resolveCustomerIdPrefix(userToken);
        if ("ALL".equals(customerId)) {
            customerId = findCustomerIdForDevice(deviceId);
        }
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
        if (JwtParserUtil.hasScope(userToken, "TENANT_ADMIN")) {
            return "ALL";
        }
        String tbCustomerId = JwtParserUtil.extractCustomerId(userToken);
        if (tbCustomerId == null) {
            return unmappedCustomer(null);
        }
        return customerRepository.findByTbCustomerId(tbCustomerId)
                .map(com.seple.ThingsBoard_Bot.entity.Customer::getCustomerId)
                .orElseGet(() -> unmappedCustomer(tbCustomerId));
    }

    private String findCustomerIdForDevice(String deviceId) {
        Optional<HierarchyNode> nodeOpt = hierarchyNodeRepository.findById(deviceId);
        if (nodeOpt.isPresent()) {
            return nodeOpt.get().getCustomerId();
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(deviceId);
            Optional<HierarchyNode> nodeUuidOpt = hierarchyNodeRepository.findByTbDeviceId(uuid);
            if (nodeUuidOpt.isPresent()) {
                return nodeUuidOpt.get().getCustomerId();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "BOI"; // Fallback
    }

    /**
     * Resolution failure handler. Fails closed (throws → HTTP 403) when strict customer mapping
     * is enabled, otherwise preserves the legacy "BOI" fallback for local development with a loud
     * warning. Defaulting an unmapped user to a real tenant is a cross-tenant data leak, so prod
     * MUST run with strict mapping on.
     */
    private String unmappedCustomer(String tbCustomerId) {
        if (securityProperties.isStrictCustomerMappingEnabled()) {
            throw new com.seple.ThingsBoard_Bot.exception.UnprovisionedCustomerException(
                    tbCustomerId == null ? "<no customerId claim>" : tbCustomerId);
        }
        log.warn("[SECURITY] No customer mapping for UUID: {} — defaulting to BOI. This is UNSAFE in "
                + "production; set IOTCHATBOT_SECURITY_STRICT_CUSTOMER_MAPPING=true to fail closed.", tbCustomerId);
        return "BOI";
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
