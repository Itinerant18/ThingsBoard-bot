package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * Hierarchy navigation: list NBGs/zones, list branches under a node, and the owning NBG/zone of a
 * branch. Derives the hierarchy from the already region-scoped snapshot list (each branch carries
 * nbg/zone names in its raw data), so it automatically respects the user's scope without re-querying
 * the hierarchy tables. NOTE: this answers structure/navigation only -- per-node health scores and
 * rankings are not in the device snapshot and are deliberately left to the honest-decline path.
 */
@Component
public class HierarchyHandler implements AnswerHandler {

    private final AnswerSupport support;

    public HierarchyHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.HIERARCHY_LIST_NODES
                || intent == QueryIntent.HIERARCHY_BRANCHES_UNDER
                || intent == QueryIntent.HIERARCHY_OWNER;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        return switch (query.getIntent()) {
            case HIERARCHY_LIST_NODES -> answerListNodes(query, snapshots);
            case HIERARCHY_BRANCHES_UNDER -> answerBranchesUnder(query, snapshots);
            case HIERARCHY_OWNER -> answerOwner(query);
            default -> null;
        };
    }

    private String answerListNodes(ResolvedQuery query, List<BranchSnapshot> snapshots) {
        String question = upper(query.getOriginalQuestion());
        boolean wantNbg = question.contains("NBG");
        String levelLabel = wantNbg ? "NBG" : "Zone";

        List<String> names = distinct(snapshots, wantNbg);
        if (names.isEmpty()) {
            return "**No " + levelLabel + " information is available in your scope.**";
        }
        return "**There " + (names.size() == 1 ? "is" : "are") + " " + names.size() + " " + levelLabel
                + (names.size() == 1 ? "" : "s") + " in your scope: " + String.join(", ", names) + ".**";
    }

    private String answerBranchesUnder(ResolvedQuery query, List<BranchSnapshot> snapshots) {
        String question = upper(query.getOriginalQuestion());

        // Match the named node against the distinct NBG/zone values actually in scope (longest first
        // so "ZO HOWRAH" wins over a stray "HOWRAH"). Then list the branches under it.
        String matchedNode = matchNodeInQuestion(snapshots, question);
        if (matchedNode == null) {
            return "**No matching NBG or zone was found in your scope.**";
        }

        String matchedNorm = matchedNode.toUpperCase(Locale.ROOT);
        List<String> branches = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            String nbg = nbgOf(snapshot);
            String zone = zoneOf(snapshot);
            if ((nbg != null && nbg.toUpperCase(Locale.ROOT).equals(matchedNorm))
                    || (zone != null && zone.toUpperCase(Locale.ROOT).equals(matchedNorm))) {
                branches.add(support.branchName(snapshot));
            }
        }
        if (branches.isEmpty()) {
            return "**No branches were found under " + matchedNode + " in your scope.**";
        }
        return "**" + matchedNode + " has " + branches.size() + " branch"
                + (branches.size() == 1 ? "" : "es") + " in your scope: " + String.join(", ", branches) + ".**";
    }

    private String answerOwner(ResolvedQuery query) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        String nbg = nbgOf(branch);
        String zone = zoneOf(branch);
        if (nbg == null && zone == null) {
            return "**For Branch " + support.branchName(branch) + ", the hierarchy (NBG/Zone) is not available.**";
        }
        List<String> parts = new ArrayList<>();
        if (nbg != null) {
            parts.add("NBG " + nbg.replaceFirst("(?i)^NBG\\s+", ""));
        }
        if (zone != null) {
            parts.add("Zone " + zone.replaceFirst("(?i)^ZO\\s+", ""));
        }
        return "**Branch " + support.branchName(branch) + " belongs to " + String.join(", ", parts) + ".**";
    }

    /** Distinct NBG (or zone) display names across the scoped snapshots, case-insensitively deduped. */
    private List<String> distinct(List<BranchSnapshot> snapshots, boolean nbg) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (BranchSnapshot snapshot : snapshots) {
            String value = nbg ? nbgOf(snapshot) : zoneOf(snapshot);
            if (value != null) {
                byKey.putIfAbsent(value.toUpperCase(Locale.ROOT), value);
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** The longest distinct NBG/zone name that appears in the question, or null. */
    private String matchNodeInQuestion(List<BranchSnapshot> snapshots, String upperQuestion) {
        List<String> candidates = new ArrayList<>();
        candidates.addAll(distinct(snapshots, true));
        candidates.addAll(distinct(snapshots, false));
        candidates.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String candidate : candidates) {
            if (upperQuestion.contains(candidate.toUpperCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private String nbgOf(BranchSnapshot snapshot) {
        return support.firstNonBlank(snapshot.getRawData(), "nbg_name", "nbgName", "nbg");
    }

    private String zoneOf(BranchSnapshot snapshot) {
        return support.firstNonBlank(snapshot.getRawData(), "zo_name", "zoName", "zone_name", "zo");
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
