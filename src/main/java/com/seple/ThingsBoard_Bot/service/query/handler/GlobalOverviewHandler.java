package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.BranchSubsystems;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * GLOBAL_OVERVIEW. Groups branches under their hierarchy ancestor path when the hierarchy repos are
 * available, otherwise falls back to a flat list.
 *
 * <p>Two modes off the same deterministic grouping:
 * <ul>
 *   <li>Gateway (default): buckets branches by gateway connectivity.
 *   <li>Per-subsystem: when the query carries a targetSystem (cctv/ias/bas/fas/timeLock/
 *       accessControl), buckets by that subsystem's state, with a distinct Not&nbsp;Installed bucket
 *       so absent subsystems aren't miscounted as Offline.
 * </ul>
 * Counting/classification stays in code (verified truth); the LLM only narrates the result.
 */
@Component
public class GlobalOverviewHandler implements AnswerHandler {

    private final AnswerTemplateService answerTemplateService;

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
            return System.currentTimeMillis() - cachedAt > 300_000; // 5 minutes TTL
        }
    }

    public GlobalOverviewHandler(AnswerTemplateService answerTemplateService) {
        this.answerTemplateService = answerTemplateService;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.GLOBAL_OVERVIEW;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        String subsystem = query == null ? null : query.getTargetSystem();
        if (subsystem != null && !subsystem.isBlank() && subsystemLabel(subsystem) != null) {
            return answerSubsystemOverview(snapshots, customerId, subsystem);
        }
        return answerGatewayOverview(snapshots, customerId);
    }

    // ==================== Gateway overview (default) ====================

    private String answerGatewayOverview(List<BranchSnapshot> snapshots, String customerId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return answerTemplateService.renderGlobalOverview(List.of(), List.of(), List.of());
        }

        Function<BranchSnapshot, NormalizedState> stateFn = s ->
                s.getGateway() != null ? s.getGateway().getState() : NormalizedState.UNKNOWN;

        if (hierarchyNodeRepository == null || branchAncestorPathRepository == null) {
            // Flat fallback when hierarchy repos are unavailable.
            List<String> online = new ArrayList<>();
            List<String> offline = new ArrayList<>();
            List<String> unknown = new ArrayList<>();
            for (BranchSnapshot snapshot : snapshots) {
                String branchName = snapshot.getIdentity() != null ? snapshot.getIdentity().getBranchName() : null;
                if (branchName == null) continue;
                NormalizedState state = stateFn.apply(snapshot);
                if (state == NormalizedState.ONLINE) {
                    online.add(branchName);
                } else if (state == NormalizedState.UNKNOWN) {
                    unknown.add(branchName);
                } else {
                    offline.add(branchName);
                }
            }
            return answerTemplateService.renderGlobalOverview(online, offline, unknown);
        }

        Map<String, String> groupHeaders = resolveGroupHeaders(snapshots, customerId);

        Map<String, List<String>> groupedOnline = new java.util.TreeMap<>();
        Map<String, List<String>> groupedOffline = new java.util.TreeMap<>();
        Map<String, List<String>> groupedUnknown = new java.util.TreeMap<>();
        int onlineCount = 0, offlineCount = 0, unknownCount = 0;

        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null) continue;
            String branchName = snapshot.getIdentity().getBranchName();
            if (branchName == null) continue;
            String groupHeader = groupHeaders.getOrDefault(branchName, "Other");
            NormalizedState state = stateFn.apply(snapshot);
            if (state == NormalizedState.ONLINE) {
                onlineCount++;
                groupedOnline.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            } else if (state == NormalizedState.UNKNOWN) {
                unknownCount++;
                groupedUnknown.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            } else {
                offlineCount++;
                groupedOffline.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            }
        }

        StringBuilder b = new StringBuilder();
        b.append("**Total: ").append(onlineCount).append(" Online | ").append(offlineCount).append(" Offline");
        if (unknownCount > 0) {
            b.append(" | ").append(unknownCount).append(" Unknown");
        }
        b.append("**\nFor your question about all branches, here is the current branch-level status.");
        appendBucket(b, "Online", onlineCount, groupedOnline, false);
        appendBucket(b, "Offline", offlineCount, groupedOffline, true);
        appendBucket(b, "Unknown", unknownCount, groupedUnknown, false);
        return b.toString();
    }

    // ==================== Per-subsystem overview ====================

    private String answerSubsystemOverview(List<BranchSnapshot> snapshots, String customerId, String subsystem) {
        String label = subsystemLabel(subsystem);
        if (snapshots == null || snapshots.isEmpty()) {
            return "No branches are in scope, so there is no " + label + " status to report.";
        }

        Map<String, String> groupHeaders = (hierarchyNodeRepository == null || branchAncestorPathRepository == null)
                ? Map.of()
                : resolveGroupHeaders(snapshots, customerId);

        Map<String, List<String>> groupedOnline = new java.util.TreeMap<>();
        Map<String, List<String>> groupedOffline = new java.util.TreeMap<>();
        Map<String, List<String>> groupedNotInstalled = new java.util.TreeMap<>();
        Map<String, List<String>> groupedUnknown = new java.util.TreeMap<>();
        int onlineCount = 0, offlineCount = 0, notInstalledCount = 0, unknownCount = 0;

        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null) continue;
            String branchName = snapshot.getIdentity().getBranchName();
            if (branchName == null) continue;
            String groupHeader = groupHeaders.getOrDefault(branchName, "Other");
            NormalizedState state = subsystemState(snapshot, subsystem);
            if (state == NormalizedState.ONLINE) {
                onlineCount++;
                groupedOnline.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            } else if (state == NormalizedState.NOT_INSTALLED) {
                notInstalledCount++;
                groupedNotInstalled.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            } else if (state == NormalizedState.UNKNOWN) {
                unknownCount++;
                groupedUnknown.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            } else {
                // OFFLINE / FAULT — a present-but-not-healthy subsystem.
                offlineCount++;
                groupedOffline.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            }
        }

        List<String> onlineBranches = getAllBranchNames(groupedOnline);
        List<String> offlineBranches = getAllBranchNames(groupedOffline);
        List<String> notInstalledBranches = getAllBranchNames(groupedNotInstalled);

        StringBuilder b = new StringBuilder();
        b.append("**").append(label).append(" Subsystem Status Across All Branches:**\n\n");

        b.append("- **").append(onlineCount).append(" Branches** have active/working **Online** ").append(label).append(" devices");
        if (!onlineBranches.isEmpty() && onlineCount <= 5) {
            b.append(" (").append(String.join(", ", onlineBranches)).append(")");
        }
        b.append(".\n");

        b.append("- **").append(offlineCount).append(" Branches** have explicitly **Offline / Faulty** ").append(label).append(" devices");
        if (!offlineBranches.isEmpty() && offlineCount <= 5) {
            b.append(" (").append(String.join(", ", offlineBranches)).append(")");
        }
        b.append(".\n");

        b.append("- **").append(notInstalledCount).append(" Branches** have ").append(label).append(" **Not Installed**");
        if (!notInstalledBranches.isEmpty() && notInstalledCount <= 5) {
            b.append(" (").append(String.join(", ", notInstalledBranches)).append(")");
        }
        b.append(".\n");

        b.append("- **").append(unknownCount).append(" Branches** are **Unknown / Unreachable** (gateways have not sent ").append(label).append(" telemetry).\n");

        appendBucket(b, "Online", onlineCount, groupedOnline, false);
        appendBucket(b, "Offline", offlineCount, groupedOffline, true);
        appendBucket(b, "Not Installed", notInstalledCount, groupedNotInstalled, false);
        appendBucket(b, "Unknown", unknownCount, groupedUnknown, false);
        return b.toString();
    }

    private List<String> getAllBranchNames(Map<String, List<String>> grouped) {
        List<String> all = new ArrayList<>();
        for (List<String> list : grouped.values()) {
            all.addAll(list);
        }
        java.util.Collections.sort(all);
        return all;
    }

    private NormalizedState subsystemState(BranchSnapshot snapshot, String subsystem) {
        BranchSubsystems subs = snapshot.getSubsystems();
        if (subs == null) {
            return NormalizedState.UNKNOWN;
        }
        SubsystemStatus status = switch (subsystem) {
            case "cctv" -> subs.getCctv();
            case "ias" -> subs.getIas();
            case "bas" -> subs.getBas();
            case "fas" -> subs.getFas();
            case "timeLock" -> subs.getTimeLock();
            case "accessControl" -> subs.getAccessControl();
            default -> null;
        };
        if (status == null || status.getState() == null) {
            return NormalizedState.UNKNOWN;
        }
        return status.getState();
    }

    private String subsystemLabel(String subsystem) {
        return switch (subsystem) {
            case "cctv" -> "CCTV";
            case "ias" -> "IAS (Intrusion Alarm)";
            case "bas" -> "BAS (Burglar Alarm)";
            case "fas" -> "FAS (Fire Alarm)";
            case "timeLock" -> "Time Lock (TLS)";
            case "accessControl" -> "Access Control (ACS)";
            default -> null;
        };
    }

    // ==================== Shared grouping / rendering ====================

    /**
     * Map each branch name to its hierarchy group header ("NBG EAST -&gt; ZO HOWRAH", or "Other").
     * Extracted so the gateway and per-subsystem overviews classify identically. Hierarchy is cached
     * per customer for 5 minutes.
     */
    private Map<String, String> resolveGroupHeaders(List<BranchSnapshot> snapshots, String customerId) {
        CachedHierarchy cached = hierarchyCache.get(customerId);
        if (cached == null || cached.isExpired()) {
            List<HierarchyNode> allNodes = hierarchyNodeRepository.findByCustomerId(customerId);
            List<BranchAncestorPath> allPaths = branchAncestorPathRepository.findByCustomerId(customerId);
            cached = new CachedHierarchy(allPaths, allNodes);
            hierarchyCache.put(customerId, cached);
        }

        Map<String, HierarchyNode> leafNodes = new java.util.HashMap<>();
        Map<String, HierarchyNode> nonLeafNodes = new java.util.HashMap<>();
        for (HierarchyNode node : cached.nodes) {
            if (Boolean.TRUE.equals(node.getIsLeaf())) {
                leafNodes.put(normalizeKey(node.getDisplayName()), node);
                leafNodes.put(normalizeKey(node.getNodeId()), node);
            } else {
                nonLeafNodes.put(node.getNodeId(), node);
            }
        }
        Map<String, List<String>> pathMap = new java.util.HashMap<>();
        for (BranchAncestorPath path : cached.paths) {
            pathMap.put(path.getBranchNodeId(), path.getAncestorList());
        }

        Map<String, String> headers = new java.util.HashMap<>();
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

            String groupHeader = "Other";
            if (leaf != null) {
                List<String> ancestors = pathMap.get(leaf.getNodeId());
                if ((ancestors == null || ancestors.isEmpty()) && leaf.getParentId() != null) {
                    ancestors = List.of(leaf.getParentId());
                }
                if (ancestors != null && !ancestors.isEmpty()) {
                    List<String> pathNames = new ArrayList<>();
                    for (String ancestorId : ancestors) {
                        HierarchyNode ancestorNode = nonLeafNodes.get(ancestorId);
                        if (ancestorNode != null && !"HO".equalsIgnoreCase(ancestorNode.getNodeType())) {
                            pathNames.add(ancestorNode.getDisplayName());
                        }
                    }
                    if (!pathNames.isEmpty()) {
                        groupHeader = String.join(" -> ", pathNames);
                    }
                }
            }
            headers.put(branchName, groupHeader);
        }
        return headers;
    }

    private void appendBucket(StringBuilder builder, String title, int count,
                              Map<String, List<String>> grouped, boolean openByDefault) {
        if (grouped.isEmpty()) {
            return;
        }
        builder.append("\n\n").append(title).append(":\n");
        boolean useCollapsible = count > 10;
        if (useCollapsible) {
            builder.append(openByDefault ? "<details open>\n" : "<details>\n")
                    .append("<summary><b>Show/Hide ").append(title).append(" Branches (")
                    .append(count).append(")</b></summary>\n\n");
        }
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            builder.append("- **").append(entry.getKey()).append("**\n");
            List<String> sortedBranches = new ArrayList<>(entry.getValue());
            java.util.Collections.sort(sortedBranches);
            for (String branch : sortedBranches) {
                builder.append("  - ").append(branch).append("\n");
            }
        }
        if (useCollapsible) {
            builder.append("</details>\n");
        }
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
}
