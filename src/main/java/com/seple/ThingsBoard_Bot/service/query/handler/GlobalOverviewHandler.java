package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * GLOBAL_OVERVIEW. Groups branches under their hierarchy ancestor path when the hierarchy repos
 * are available, otherwise falls back to a flat online/offline list. Logic moved verbatim from
 * the original DeterministicAnswerService.
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
        return answerGlobalOverview(snapshots, customerId);
    }

    private String answerGlobalOverview(List<BranchSnapshot> snapshots, String customerId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return answerTemplateService.renderGlobalOverview(List.of(), List.of());
        }

        if (hierarchyNodeRepository == null || branchAncestorPathRepository == null) {
            // Fall back to old behavior (flat list)
            List<String> online = new ArrayList<>();
            List<String> offline = new ArrayList<>();
            for (BranchSnapshot snapshot : snapshots) {
                if (snapshot.getGateway().getState() == NormalizedState.ONLINE) {
                    online.add(snapshot.getIdentity().getBranchName());
                } else {
                    offline.add(snapshot.getIdentity().getBranchName());
                }
            }
            return answerTemplateService.renderGlobalOverview(online, offline);
        }

        // Load all hierarchy nodes and paths for this customer (cached).
        CachedHierarchy cached = hierarchyCache.get(customerId);
        if (cached == null || cached.isExpired()) {
            List<HierarchyNode> allNodes = hierarchyNodeRepository.findByCustomerId(customerId);
            List<BranchAncestorPath> allPaths = branchAncestorPathRepository.findByCustomerId(customerId);
            cached = new CachedHierarchy(allPaths, allNodes);
            hierarchyCache.put(customerId, cached);
        }
        List<HierarchyNode> allNodes = cached.nodes;
        List<BranchAncestorPath> allPaths = cached.paths;

        // Build mapping structures:
        // 1. Map of normalized display name/node ID -> HierarchyNode (leaves)
        Map<String, HierarchyNode> leafNodes = new java.util.HashMap<>();
        // 2. Map of nodeId -> HierarchyNode (non-leaves)
        Map<String, HierarchyNode> nonLeafNodes = new java.util.HashMap<>();

        for (HierarchyNode node : allNodes) {
            if (Boolean.TRUE.equals(node.getIsLeaf())) {
                leafNodes.put(normalizeKey(node.getDisplayName()), node);
                leafNodes.put(normalizeKey(node.getNodeId()), node);
            } else {
                nonLeafNodes.put(node.getNodeId(), node);
            }
        }

        // 3. Map of branch nodeId -> Ancestor path list
        Map<String, List<String>> pathMap = new java.util.HashMap<>();
        for (BranchAncestorPath path : allPaths) {
            pathMap.put(path.getBranchNodeId(), path.getAncestorList());
        }

        // Group the branches by their ancestor path display name.
        Map<String, List<String>> groupedOnline = new java.util.TreeMap<>();
        Map<String, List<String>> groupedOffline = new java.util.TreeMap<>();

        int onlineCount = 0;
        int offlineCount = 0;

        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null) continue;
            String branchName = snapshot.getIdentity().getBranchName();
            if (branchName == null) continue;

            NormalizedState state = snapshot.getGateway().getState();
            boolean isOnline = (state == NormalizedState.ONLINE);
            if (isOnline) {
                onlineCount++;
            } else {
                offlineCount++;
            }

            // Find hierarchy path for this branch
            String normName = normalizeKey(branchName);
            HierarchyNode leaf = leafNodes.get(normName);
            if (leaf == null) {
                // Try searching by deviceId or technicalId
                String devId = snapshot.getIdentity().getDeviceId();
                if (devId != null) {
                    leaf = leafNodes.get(normalizeKey(devId));
                }
            }

            String groupHeader = "Other";
            if (leaf != null) {
                List<String> ancestors = pathMap.get(leaf.getNodeId());
                if (ancestors == null || ancestors.isEmpty()) {
                    if (leaf.getParentId() != null) {
                        ancestors = List.of(leaf.getParentId());
                    }
                }

                if (ancestors != null && !ancestors.isEmpty()) {
                    List<String> pathNames = new ArrayList<>();
                    for (String ancestorId : ancestors) {
                        HierarchyNode ancestorNode = nonLeafNodes.get(ancestorId);
                        if (ancestorNode != null) {
                            // Skip HO root node
                            if ("HO".equalsIgnoreCase(ancestorNode.getNodeType())) {
                                continue;
                            }
                            pathNames.add(ancestorNode.getDisplayName());
                        }
                    }
                    if (!pathNames.isEmpty()) {
                        groupHeader = String.join(" -> ", pathNames);
                    }
                }
            }

            if (isOnline) {
                groupedOnline.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            } else {
                groupedOffline.computeIfAbsent(groupHeader, k -> new ArrayList<>()).add(branchName);
            }
        }

        // Now build the markdown response using our grouped lists.
        return renderGroupedGlobalOverview(onlineCount, offlineCount, groupedOnline, groupedOffline);
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

    private String renderGroupedGlobalOverview(
            int onlineCount, int offlineCount,
            Map<String, List<String>> groupedOnline,
            Map<String, List<String>> groupedOffline) {

        StringBuilder builder = new StringBuilder();
        builder.append("**Total: ")
                .append(onlineCount).append(" Online | ")
                .append(offlineCount).append(" Offline**");
        builder.append("\nFor your question about all branches, here is the current branch-level status.");

        if (!groupedOnline.isEmpty()) {
            builder.append("\n\nOnline:\n");
            boolean useCollapsible = onlineCount > 10;
            if (useCollapsible) {
                builder.append("<details>\n<summary><b>Show/Hide Online Branches (").append(onlineCount).append(")</b></summary>\n\n");
            }
            for (Map.Entry<String, List<String>> entry : groupedOnline.entrySet()) {
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

        if (!groupedOffline.isEmpty()) {
            builder.append("\nOffline:\n");
            boolean useCollapsible = offlineCount > 10;
            if (useCollapsible) {
                builder.append("<details open>\n<summary><b>Show/Hide Offline Branches (").append(offlineCount).append(")</b></summary>\n\n");
            }
            for (Map.Entry<String, List<String>> entry : groupedOffline.entrySet()) {
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

        return builder.toString();
    }
}
