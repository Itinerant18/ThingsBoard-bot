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
import com.seple.ThingsBoard_Bot.util.RegionalScopeUtil;

import lombok.extern.slf4j.Slf4j;

import com.seple.ThingsBoard_Bot.client.UserAwareThingsBoardClient;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

@Slf4j
@Service
public class BranchIndexService {

    private final ThingsBoardConfig thingsBoardConfig;
    private final CustomerRepository customerRepository;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final UserAwareThingsBoardClient userAwareThingsBoardClient;
    private final BranchAncestorPathRepository branchAncestorPathRepository;
    private final com.seple.ThingsBoard_Bot.config.SecurityProperties securityProperties;
    private final ConcurrentHashMap<String, List<DeviceIndexEntry>> indexByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAccess = new ConcurrentHashMap<>();

    // Per-entry TTL: cached index untouched for this long is evicted (10 minutes).
    private static final long INDEX_TTL_MS = 10 * 60 * 1000L;

    public BranchIndexService(ThingsBoardConfig thingsBoardConfig,
                              CustomerRepository customerRepository,
                              HierarchyNodeRepository hierarchyNodeRepository,
                              UserAwareThingsBoardClient userAwareThingsBoardClient,
                              BranchAncestorPathRepository branchAncestorPathRepository,
                              com.seple.ThingsBoard_Bot.config.SecurityProperties securityProperties) {
        this.thingsBoardConfig = thingsBoardConfig;
        this.customerRepository = customerRepository;
        this.hierarchyNodeRepository = hierarchyNodeRepository;
        this.userAwareThingsBoardClient = userAwareThingsBoardClient;
        this.branchAncestorPathRepository = branchAncestorPathRepository;
        this.securityProperties = securityProperties;
    }

    /**
     * Resolution failure handler, consistent with {@code UserDataService.unmappedCustomer}. Fails
     * closed (throws → HTTP 403) when strict customer mapping is enabled (the default), otherwise
     * falls back to BOI for local dev only, with a loud warning.
     */
    private String unmappedCustomer(String tbCustomerId) {
        if (securityProperties.isStrictCustomerMappingEnabled()) {
            throw new com.seple.ThingsBoard_Bot.exception.UnprovisionedCustomerException(
                    tbCustomerId == null ? "<no customerId claim>" : tbCustomerId);
        }
        log.warn("[SECURITY] No customer mapping for tbCustomerId {} — defaulting index to BOI. "
                + "UNSAFE in production; strict customer mapping is the default.", tbCustomerId);
        return "BOI";
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
        boolean isTenantAdmin = userToken == null || userToken.isBlank() || JwtParserUtil.hasScope(userToken, "TENANT_ADMIN");
        String customerId;
        List<HierarchyNode> nodes;

        if (isTenantAdmin) {
            customerId = "ALL";
            nodes = hierarchyNodeRepository.findAll().stream()
                    .filter(n -> Boolean.TRUE.equals(n.getIsLeaf()))
                    .collect(Collectors.toList());
        } else {
            // Tenant is resolved from the caller's token, never hardcoded. An unmapped customer
            // fails closed (strict, the default) so a mis-mapped user can't be shown BOI's branches.
            String tbCustomerId = JwtParserUtil.extractCustomerId(userToken);
            customerId = (tbCustomerId != null)
                    ? customerRepository.findByTbCustomerId(tbCustomerId)
                            .map(com.seple.ThingsBoard_Bot.entity.Customer::getCustomerId)
                            .orElseGet(() -> unmappedCustomer(tbCustomerId))
                    : unmappedCustomer(null);
            nodes = hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true);
        }

        if (!isTenantAdmin) {
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
                }
            } catch (Exception e) {
                log.error("Failed to filter branch index via ThingsBoard API: {}", e.getMessage());
            }

            // ALWAYS apply regional ancestor-path scoping after the device ACL. The ThingsBoard
            // customer ACL returns the whole inventory, so a region-scoped user (e.g. NBG JH) must
            // be narrowed here. Shared with UserDataService via RegionalScopeUtil so the two
            // retrieval paths cannot diverge and leak (this path previously skipped regional
            // scoping entirely, leaking all branches to a region-scoped user).
            List<HierarchyNode> allNodes = hierarchyNodeRepository.findByCustomerId(customerId);
            List<BranchAncestorPath> ancestorPaths = branchAncestorPathRepository.findByCustomerId(customerId);
            nodes = RegionalScopeUtil.filterByRegion(userToken, nodes, allNodes, ancestorPaths);
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
                            .customerId(node.getCustomerId())
                            .build();
                })
                .toList();

        String key = cacheKey(userToken);
        indexByUser.put(key, new ArrayList<>(entries));
        lastAccess.put(key, System.currentTimeMillis());
        log.info("Indexed {} devices from local DB for user cache {}", entries.size(), key);
        return entries;
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
        Set<String> set = new java.util.LinkedHashSet<>();
        set.add(branchName);
        set.add(normalized);
        set.add(compact);
        return new ArrayList<>(set);
    }

    private String cacheKey(String userToken) {
        return String.valueOf(userToken.hashCode());
    }
}
