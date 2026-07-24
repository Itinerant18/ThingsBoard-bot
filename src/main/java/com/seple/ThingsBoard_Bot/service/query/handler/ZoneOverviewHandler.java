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

    /** Shared network summary builder (same package). Injected via field so unit tests still compile. */
    @Autowired(required = false)
    private NetworkStatusHandler networkStatusHandler;

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

        // Filter snapshots to only branches under this zone (or any zone in a multi-zone list).
        List<BranchSnapshot> zoneSnapshots = filterByZone(snapshots, zoneFilter, customerId);
        String zoneLabel = zoneDisplayLabel(zoneFilter);
        if (zoneSnapshots.isEmpty()) {
            return "**No branches found under " + zoneLabel + " in your scope.**";
        }

        String question = query.getOriginalQuestion().toUpperCase(Locale.ROOT);

        // Determine what type of data the user is asking about.
        if (isAlarmRankingQuestion(question)) {
            return renderAlarmRanking(zoneLabel, zoneSnapshots);
        }
        if (isNetworkQuestion(question)) {
            return renderNetworkOverview(zoneLabel, zoneSnapshots);
        }
        if (isPowerQuestion(question)) {
            return renderPowerOverview(zoneLabel, zoneSnapshots);
        }
        if (isSubsystemQuestion(question)) {
            return renderSubsystemOverview(zoneLabel, zoneSnapshots, question);
        }
        if (isOfflineQuestion(question)) {
            return renderOfflineBranches(zoneLabel, zoneSnapshots);
        }
        if (isOnlineQuestion(question)) {
            return renderOnlineBranches(zoneLabel, zoneSnapshots);
        }
        // Default: gateway status overview for the zone.
        return renderGatewayOverview(zoneLabel, zoneSnapshots);
    }

    // --- Filtering ---

    /** One requested zone: its full form ("ZO HOWRAH") and bare name ("HOWRAH") for matching. */
    private record ZoneSpec(String fullFilter, String nameOnly) {}

    /**
     * Parse a zone filter into one or more zone specs. Supports a slash-separated multi-zone list
     * where the prefix is written once, e.g. "ZO Howrah/Barasat/Siliguri" -> ZO HOWRAH, ZO BARASAT,
     * ZO SILIGURI. A single zone parses to a one-element list (unchanged behaviour).
     */
    private List<ZoneSpec> parseZoneSpecs(String zoneFilter) {
        String upper = zoneFilter.toUpperCase(Locale.ROOT)
                .replaceAll("[\\?\\!\\.,;:_]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String prefix = "";
        String remainder = upper;
        for (String p : new String[]{"ZO", "ZONE", "NBG"}) {
            if (upper.startsWith(p + " ")) {
                prefix = p;
                remainder = upper.substring(p.length()).trim();
                break;
            }
        }
        List<ZoneSpec> specs = new ArrayList<>();
        for (String piece : remainder.split("/")) {
            String name = piece.replaceAll("[^A-Z0-9 ]", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            String full = prefix.isEmpty() ? name : prefix + " " + name;
            specs.add(new ZoneSpec(full, name));
        }
        if (specs.isEmpty()) {
            specs.add(new ZoneSpec(upper, upper));
        }
        return specs;
    }

    /** Readable label for the requested zones, e.g. "ZO HOWRAH, ZO BARASAT". */
    private String zoneDisplayLabel(String zoneFilter) {
        List<String> names = new ArrayList<>();
        for (ZoneSpec spec : parseZoneSpecs(zoneFilter)) {
            names.add(spec.fullFilter());
        }
        return String.join(", ", names);
    }

    private List<BranchSnapshot> filterByZone(List<BranchSnapshot> snapshots, String zoneFilter, String customerId) {
        List<ZoneSpec> specs = parseZoneSpecs(zoneFilter);

        // Primary source: hierarchy ancestor names (covers every branch). Falls back to the sparse
        // zo/nbg telemetry keys when the hierarchy repos aren't available (e.g. unit tests).
        Map<String, Set<String>> ancestorNames = resolveAncestorNames(snapshots, customerId);

        List<BranchSnapshot> result = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (matchesByHierarchy(snapshot, ancestorNames, specs)) {
                result.add(snapshot);
                continue;
            }
            String zo = zoneOf(snapshot);
            String nbg = nbgOf(snapshot);
            if (matchesAnyZone(zo, specs) || matchesAnyZone(nbg, specs)) {
                result.add(snapshot);
            }
        }
        return result;
    }

    private boolean matchesAnyZone(String value, List<ZoneSpec> specs) {
        for (ZoneSpec spec : specs) {
            if (matchesZone(value, spec.fullFilter(), spec.nameOnly())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesByHierarchy(BranchSnapshot snapshot, Map<String, Set<String>> ancestorNames,
                                       List<ZoneSpec> specs) {
        if (snapshot.getIdentity() == null || snapshot.getIdentity().getBranchName() == null) {
            return false;
        }
        Set<String> ancestors = ancestorNames.get(snapshot.getIdentity().getBranchName());
        if (ancestors == null) {
            return false;
        }
        for (String ancestor : ancestors) {
            if (matchesAnyZone(ancestor, specs)) {
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
            List<HierarchyNode> allNodes;
            List<BranchAncestorPath> allPaths;
            if ("ALL".equals(customerId)) {
                allNodes = hierarchyNodeRepository.findAll();
                allPaths = branchAncestorPathRepository.findAll();
            } else {
                allNodes = hierarchyNodeRepository.findByCustomerId(customerId);
                allPaths = branchAncestorPathRepository.findByCustomerId(customerId);
            }
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
        if (upper.equals(fullFilter)
                || upper.equals(nameOnly)
                || upper.endsWith(" " + nameOnly)
                || upper.contains(nameOnly)) {
            return true;
        }
        String normValue = normalizeKey(upper);
        String normFilter = normalizeKey(nameOnly);
        if (normValue.equals(normFilter) || normValue.contains(normFilter) || normFilter.contains(normValue)) {
            return true;
        }
        if (normFilter.length() > 3) {
            String stemFilter = normFilter.replaceAll("A$", "");
            String stemValue = normValue.replaceAll("A$", "");
            if (stemValue.equals(stemFilter) || stemValue.contains(stemFilter) || stemFilter.contains(stemValue)) {
                return true;
            }
        }
        return false;
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

    private String renderPowerOverview(String zoneName, List<BranchSnapshot> snapshots) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(zoneName).append(" — Battery & Power Status (").append(snapshots.size()).append(" branches):**\n\n");
        for (BranchSnapshot snapshot : snapshots) {
            String name = support.branchName(snapshot);
            Double batteryVoltage = snapshot.getPower() != null ? snapshot.getPower().getBatteryVoltage() : null;
            Double acVoltage = snapshot.getPower() != null ? snapshot.getPower().getAcVoltage() : null;
            Boolean mainsOn = snapshot.getPower() != null ? snapshot.getPower().getMainsOn() : null;

            sb.append("- **").append(name).append("**:");
            if (batteryVoltage != null) {
                String vStr = batteryVoltage % 1 == 0 ? String.valueOf(batteryVoltage.longValue()) : String.valueOf(batteryVoltage);
                sb.append(" Battery: **").append(vStr).append("V DC**");
            } else {
                sb.append(" Battery: N/A");
            }

            if (acVoltage != null) {
                String acStr = acVoltage % 1 == 0 ? String.valueOf(acVoltage.longValue()) : String.valueOf(acVoltage);
                sb.append(" | AC Mains: ").append(acStr).append("V AC");
            } else if (Boolean.TRUE.equals(mainsOn)) {
                sb.append(" | AC Mains: ON");
            } else if (Boolean.FALSE.equals(mainsOn)) {
                sb.append(" | AC Mains: OFF");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // --- Subsystem detection (mirrors QueryIntentResolver.detectSubsystem) ---

    private boolean isPowerQuestion(String question) {
        return question.contains("BATTERY") || question.contains("VOLTAGE") || question.contains("POWER") || question.contains("MAINS");
    }

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

    /** Step 2: Network questions for a zone — "network status for ZO Howrah", "which ZO has network issues?" */
    private boolean isNetworkQuestion(String question) {
        return question.contains("NETWORK") || question.contains("OPERATOR")
                || question.contains("SIM") || question.contains("CARRIER");
    }

    /** Step 3: Alarm ranking within a zone — "top branches by alarms in ZO Howrah", "most alarms" */
    private boolean isAlarmRankingQuestion(String question) {
        boolean hasAlarm = question.contains("ALARM") || question.contains("ERROR");
        boolean hasRank = question.contains("TOP ") || question.contains("MOST") || question.contains("RANK")
                || question.contains("HIGHEST") || question.contains("WORST");
        return hasAlarm && hasRank;
    }

    // --- Network rendering (Step 2) ---

    private String renderNetworkOverview(String zoneLabel, List<BranchSnapshot> zoneSnapshots) {
        if (networkStatusHandler != null) {
            return networkStatusHandler.buildNetworkSummary(zoneLabel, zoneSnapshots);
        }
        // Fallback if handler not injected (unit-test path)
        return "Network summary not available for " + zoneLabel + ".";
    }

    // --- Alarm ranking rendering (Step 3) ---

    private String renderAlarmRanking(String zoneLabel, List<BranchSnapshot> zoneSnapshots) {
        List<BranchSnapshot> withAlarms = zoneSnapshots.stream()
                .filter(s -> s.getAlerts() != null && s.getAlerts().getAlarmCount() > 0)
                .sorted((a, b) -> Integer.compare(
                        b.getAlerts().getAlarmCount(), a.getAlerts().getAlarmCount()))
                .toList();

        int totalAlarms = zoneSnapshots.stream()
                .mapToInt(s -> s.getAlerts() != null ? s.getAlerts().getAlarmCount() : 0).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("### 🚨 Alarm Ranking — ").append(zoneLabel).append("\n\n");
        sb.append("**Total open alarms across zone: ").append(totalAlarms).append("**\n");
        sb.append("**Branches scoped: ").append(zoneSnapshots.size()).append("**\n\n");

        if (withAlarms.isEmpty()) {
            sb.append("✅ No branches with open alarms in this zone.");
            return sb.toString();
        }

        sb.append("| # | Branch | Alarms |\n|---|--------|--------|");
        int rank = 1;
        for (BranchSnapshot snap : withAlarms) {
            sb.append("\n| ").append(rank++)
              .append(" | ").append(support.branchName(snap))
              .append(" | ").append(snap.getAlerts().getAlarmCount()).append(" |");
        }
        return sb.toString();
    }

    // --- Raw data accessors ---

    private String zoneOf(BranchSnapshot snapshot) {
        return support.firstNonBlank(snapshot.getRawData(), "zo_name", "zoName", "zone_name", "zo");
    }

    private String nbgOf(BranchSnapshot snapshot) {
        return support.firstNonBlank(snapshot.getRawData(), "nbg_name", "nbgName", "nbg");
    }
}
