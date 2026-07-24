package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * ZONE_OVERVIEW.  Handles zone-scoped data questions such as "gateway status for
 * ZO Howrah", "offline branches in ZO Barasat", "CCTV health for ZO Ranchi".
 *
 * <p>Filters the user's snapshots to only branches whose {@code zo_name} or
 * {@code nbg_name} matches the zone named in the question, then produces a
 * zone-level summary with per-branch details.
 */
@Component
public class ZoneOverviewHandler implements AnswerHandler {

    private final AnswerSupport support;

    // Zone membership lives in the hierarchy (ancestor paths), not device telemetry — the zo/nbg
    // telemetry keys are present on <10% of devices. Field-injected so the manual test constructor
    // (repos null) keeps working and falls back to the telemetry match.
    @Autowired(required = false)
    private HierarchyNodeRepository hierarchyNodeRepository;

    @Autowired(required = false)
    private BranchAncestorPathRepository branchAncestorPathRepository;

    private final Map<String, CachedHierarchy> hierarchyCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedHierarchy {
        final List<BranchAncestorPath> paths;
        final List<HierarchyNode> nodes;
        final long cachedAt;

        CachedHierarchy(List<BranchAncestorPath> paths, List<HierarchyNode> nodes) {
            this.paths = paths;
            this.nodes = nodes;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > 300_000;
        }
    }

    public ZoneOverviewHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.ZONE_OVERVIEW;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        String zoneFilter = query.getZoneFilter();
        if (zoneFilter == null || zoneFilter.isBlank()) {
            return "**Could not determine which zone you are asking about. Please specify the zone name (e.g., ZO Howrah).**";
        }

        // Filter snapshots to only branches under this zone.
        List<BranchSnapshot> zoneSnapshots = filterByZone(snapshots, zoneFilter, customerId);
        if (zoneSnapshots.isEmpty()) {
            return "**No branches found under " + zoneFilter + " in your scope.**";
        }

        String question = query.getOriginalQuestion().toUpperCase(Locale.ROOT);

        // Determine what type of data the user is asking about.
        if (isSubsystemQuestion(question)) {
            return renderSubsystemOverview(zoneFilter, zoneSnapshots, question);
        }
        if (isOfflineQuestion(question)) {
            return renderOfflineBranches(zoneFilter, zoneSnapshots);
        }
        if (isOnlineQuestion(question)) {
            return renderOnlineBranches(zoneFilter, zoneSnapshots);
        }
        // Default: gateway status overview for the zone.
        return renderGatewayOverview(zoneFilter, zoneSnapshots);
    }

    // --- Filtering ---

    private List<BranchSnapshot> filterByZone(List<BranchSnapshot> snapshots, String zoneFilter, String customerId) {
        String upperFilter = zoneFilter.toUpperCase(Locale.ROOT);
        // Strip the prefix for matching (e.g., "ZO HOWRAH" -> "HOWRAH",
        // "NBG EAST" -> "EAST") so we can also match raw values that don't
        // include the prefix.
        String nameOnly = upperFilter
                .replaceFirst("^ZO\\s+", "")
                .replaceFirst("^ZONE\\s+", "")
                .replaceFirst("^NBG\\s+", "")
                .trim();

        // Primary source: hierarchy ancestor names (covers every branch). Falls back to the sparse
        // zo/nbg telemetry keys when the hierarchy repos aren't available (e.g. unit tests).
        Map<String, Set<String>> ancestorNames = resolveAncestorNames(snapshots, customerId);

        List<BranchSnapshot> result = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (matchesByHierarchy(snapshot, ancestorNames, upperFilter, nameOnly)) {
                result.add(snapshot);
                continue;
            }
            String zo = zoneOf(snapshot);
            String nbg = nbgOf(snapshot);
            if (matchesZone(zo, upperFilter, nameOnly) || matchesZone(nbg, upperFilter, nameOnly)) {
                result.add(snapshot);
            }
        }
        return result;
    }

    private boolean matchesByHierarchy(BranchSnapshot snapshot, Map<String, Set<String>> ancestorNames,
                                       String upperFilter, String nameOnly) {
        if (snapshot.getIdentity() == null || snapshot.getIdentity().getBranchName() == null) {
            return false;
        }
        Set<String> ancestors = ancestorNames.get(snapshot.getIdentity().getBranchName());
        if (ancestors == null) {
            return false;
        }
        for (String ancestor : ancestors) {
            if (matchesZone(ancestor, upperFilter, nameOnly)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Map each branch name to the set of its ancestor (zone/NBG) display names, resolved from the
     * hierarchy. Returns an empty map when the hierarchy repos are unavailable. Cached per customer.
     */
    private Map<String, Set<String>> resolveAncestorNames(List<BranchSnapshot> snapshots, String customerId) {
        if (hierarchyNodeRepository == null || branchAncestorPathRepository == null || customerId == null) {
            return Map.of();
        }
        CachedHierarchy cached = hierarchyCache.get(customerId);
        if (cached == null || cached.isExpired()) {
            List<HierarchyNode> allNodes = hierarchyNodeRepository.findByCustomerId(customerId);
            List<BranchAncestorPath> allPaths = branchAncestorPathRepository.findByCustomerId(customerId);
            cached = new CachedHierarchy(allPaths, allNodes);
            hierarchyCache.put(customerId, cached);
        }

        Map<String, HierarchyNode> leafNodes = new HashMap<>();
        Map<String, HierarchyNode> nonLeafNodes = new HashMap<>();
        for (HierarchyNode node : cached.nodes) {
            if (Boolean.TRUE.equals(node.getIsLeaf())) {
                leafNodes.put(normalizeKey(node.getDisplayName()), node);
                leafNodes.put(normalizeKey(node.getNodeId()), node);
            } else {
                nonLeafNodes.put(node.getNodeId(), node);
            }
        }
        Map<String, List<String>> pathMap = new HashMap<>();
        for (BranchAncestorPath path : cached.paths) {
            pathMap.put(path.getBranchNodeId(), path.getAncestorList());
        }

        Map<String, Set<String>> byBranch = new HashMap<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null) continue;
            String branchName = snapshot.getIdentity().getBranchName();
            if (branchName == null) continue;

            HierarchyNode leaf = leafNodes.get(normalizeKey(branchName));
            if (leaf == null) {
                String devId = snapshot.getIdentity().getDeviceId();
                if (devId != null) {
                    leaf = leafNodes.get(normalizeKey(devId));
                }
            }
            if (leaf == null) continue;

            List<String> ancestors = pathMap.get(leaf.getNodeId());
            if ((ancestors == null || ancestors.isEmpty()) && leaf.getParentId() != null) {
                ancestors = List.of(leaf.getParentId());
            }
            if (ancestors == null) continue;

            Set<String> names = new HashSet<>();
            for (String ancestorId : ancestors) {
                HierarchyNode ancestorNode = nonLeafNodes.get(ancestorId);
                if (ancestorNode != null && ancestorNode.getDisplayName() != null) {
                    names.add(ancestorNode.getDisplayName());
                }
            }
            if (!names.isEmpty()) {
                byBranch.put(branchName, names);
            }
        }
        return byBranch;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT)
                .replace("BOI-", "")
                .replace("BRANCH ", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean matchesZone(String value, String fullFilter, String nameOnly) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String upper = value.toUpperCase(Locale.ROOT).trim();
        return upper.equals(fullFilter)
                || upper.equals(nameOnly)
                || upper.endsWith(" " + nameOnly)
                || upper.contains(nameOnly);
    }

    // --- Renderers ---

    private String renderGatewayOverview(String zoneName, List<BranchSnapshot> snapshots) {
        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (BranchSnapshot snapshot : snapshots) {
            String name = support.branchName(snapshot);
            NormalizedState state = snapshot.getGateway().getState();
            switch (state) {
                case ONLINE -> online.add(name);
                case OFFLINE, FAULT -> offline.add(name);
                default -> unknown.add(name);
            }
        }
        Collections.sort(online);
        Collections.sort(offline);
        Collections.sort(unknown);

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(zoneName).append(" — Gateway Status (")
          .append(snapshots.size()).append(" branches)**\n");
        sb.append("Online: ").append(online.size())
          .append(" | Offline: ").append(offline.size());
        if (!unknown.isEmpty()) {
            sb.append(" | Unknown: ").append(unknown.size());
        }
        sb.append("\n");

        if (!online.isEmpty()) {
            sb.append("\n✅ **Online:**\n");
            for (String name : online) {
                sb.append("  - ").append(name).append("\n");
            }
        }
        if (!offline.isEmpty()) {
            sb.append("\n❌ **Offline:**\n");
            for (String name : offline) {
                sb.append("  - ").append(name).append("\n");
            }
        }
        if (!unknown.isEmpty()) {
            sb.append("\n❓ **Unknown:**\n");
            for (String name : unknown) {
                sb.append("  - ").append(name).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String renderOfflineBranches(String zoneName, List<BranchSnapshot> snapshots) {
        List<String> offline = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            NormalizedState state = snapshot.getGateway().getState();
            if (state == NormalizedState.OFFLINE || state == NormalizedState.FAULT) {
                offline.add(support.branchName(snapshot));
            }
        }
        Collections.sort(offline);

        if (offline.isEmpty()) {
            return "**All " + snapshots.size() + " branches in " + zoneName + " are currently Online. No offline branches found.**";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(zoneName).append(" — ").append(offline.size())
          .append(" Offline Branch").append(offline.size() == 1 ? "" : "es")
          .append(" (out of ").append(snapshots.size()).append("):**\n");
        for (String name : offline) {
            sb.append("  - ").append(name).append("\n");
        }
        return sb.toString().trim();
    }

    private String renderOnlineBranches(String zoneName, List<BranchSnapshot> snapshots) {
        List<String> online = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getGateway().getState() == NormalizedState.ONLINE) {
                online.add(support.branchName(snapshot));
            }
        }
        Collections.sort(online);

        if (online.isEmpty()) {
            return "**No branches in " + zoneName + " are currently Online.**";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(zoneName).append(" — ").append(online.size())
          .append(" Online Branch").append(online.size() == 1 ? "" : "es")
          .append(" (out of ").append(snapshots.size()).append("):**\n");
        for (String name : online) {
            sb.append("  - ").append(name).append("\n");
        }
        return sb.toString().trim();
    }

    private String renderSubsystemOverview(String zoneName, List<BranchSnapshot> snapshots, String question) {
        String subsystem = detectSubsystem(question);
        String subsystemLabel = subsystemLabel(subsystem);

        Map<String, List<String>> grouped = new TreeMap<>();
        for (BranchSnapshot snapshot : snapshots) {
            String name = support.branchName(snapshot);
            SubsystemStatus status = support.subsystemByTarget(snapshot, subsystem);
            String stateLabel;
            if (status == null) {
                stateLabel = "N/A";
            } else {
                stateLabel = support.formatState(status.getState());
            }
            grouped.computeIfAbsent(stateLabel, k -> new ArrayList<>()).add(name);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(zoneName).append(" — ").append(subsystemLabel)
          .append(" Status (").append(snapshots.size()).append(" branches):**\n");

        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            List<String> names = entry.getValue();
            Collections.sort(names);
            sb.append("\n**").append(entry.getKey()).append("** (").append(names.size()).append("):\n");
            for (String name : names) {
                sb.append("  - ").append(name).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // --- Subsystem detection (mirrors QueryIntentResolver.detectSubsystem) ---

    private String detectSubsystem(String question) {
        if (question.contains("IAS") || question.contains("INTRUSION")) return "ias";
        if (question.contains("BAS")) return "bas";
        if (question.contains("FAS") || question.contains("FIRE")) return "fas";
        if (question.contains("TIME LOCK")) return "timeLock";
        if (question.contains("ACCESS CONTROL") || question.contains("ACS")) return "accessControl";
        if (question.contains("CCTV") || question.contains("CAMERA")) return "cctv";
        return null;
    }

    private String subsystemLabel(String subsystem) {
        if (subsystem == null) return "Subsystem";
        return switch (subsystem) {
            case "ias" -> "IAS (Intrusion Alarm)";
            case "bas" -> "BAS (Building Alarm)";
            case "fas" -> "FAS (Fire Alarm)";
            case "timeLock" -> "Time Lock";
            case "accessControl" -> "Access Control";
            case "cctv" -> "CCTV";
            default -> subsystem.toUpperCase(Locale.ROOT);
        };
    }

    // --- Question classifiers ---

    private boolean isSubsystemQuestion(String question) {
        return detectSubsystem(question) != null;
    }

    private boolean isOfflineQuestion(String question) {
        return question.contains("OFFLINE") || question.contains("INACTIVE") || question.contains("DOWN");
    }

    private boolean isOnlineQuestion(String question) {
        return (question.contains("ONLINE") || question.contains("ACTIVE"))
                && !question.contains("INACTIVE");
    }

    // --- Raw data accessors ---

    private String zoneOf(BranchSnapshot snapshot) {
        return support.firstNonBlank(snapshot.getRawData(), "zo_name", "zoName", "zone_name", "zo");
    }

    private String nbgOf(BranchSnapshot snapshot) {
        return support.firstNonBlank(snapshot.getRawData(), "nbg_name", "nbgName", "nbg");
    }
}
