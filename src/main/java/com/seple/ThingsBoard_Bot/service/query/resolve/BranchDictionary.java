package com.seple.ThingsBoard_Bot.service.query.resolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;

/**
 * Immutable snapshot of all branch name variants for one customer scope. Built either
 * from live PostgreSQL hierarchy nodes or from {@link BranchSnapshot}s (offline/tests).
 * Variant normalization is delegated to {@link BranchAliasIndex} so exact-match and
 * fuzzy-match layers agree on spelling.
 */
public final class BranchDictionary {

    private final List<BranchEntry> entries;
    private final Map<String, BranchEntry> exactLookup;

    private BranchDictionary(List<BranchEntry> entries, Map<String, BranchEntry> exactLookup) {
        this.entries = Collections.unmodifiableList(entries);
        this.exactLookup = Collections.unmodifiableMap(exactLookup);
    }

    public static BranchDictionary fromSnapshots(List<BranchSnapshot> snapshots, String customerId,
            BranchAliasIndex aliasIndex) {
        List<BranchEntry> entries = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null) {
                continue;
            }
            Set<String> variants = new LinkedHashSet<>();
            addVariants(variants, snapshot.getIdentity().getTechnicalId(), aliasIndex);
            addVariants(variants, snapshot.getIdentity().getBranchName(), aliasIndex);
            List<String> aliases = snapshot.getIdentity().getAliases();
            if (aliases != null) {
                for (String alias : aliases) {
                    addVariants(variants, alias, aliasIndex);
                }
            }
            if (!variants.isEmpty()) {
                entries.add(new BranchEntry(snapshot.getIdentity().getTechnicalId(),
                        snapshot.getIdentity().getBranchName(), customerId, variants));
            }
        }
        return index(entries);
    }

    public static BranchDictionary fromHierarchyNodes(List<HierarchyNode> leafNodes, BranchAliasIndex aliasIndex) {
        List<BranchEntry> entries = new ArrayList<>();
        for (HierarchyNode node : leafNodes) {
            Set<String> variants = new LinkedHashSet<>();
            addVariants(variants, node.getNodeId(), aliasIndex);
            addVariants(variants, node.getDisplayName(), aliasIndex);
            if (!variants.isEmpty()) {
                entries.add(new BranchEntry(node.getNodeId(), node.getDisplayName(), node.getCustomerId(), variants));
            }
        }
        return index(entries);
    }

    private static BranchDictionary index(List<BranchEntry> entries) {
        Map<String, BranchEntry> lookup = new HashMap<>();
        for (BranchEntry entry : entries) {
            for (String variant : entry.variants()) {
                lookup.putIfAbsent(variant, entry);
            }
        }
        return new BranchDictionary(entries, lookup);
    }

    private static void addVariants(Set<String> variants, String name, BranchAliasIndex aliasIndex) {
        if (name == null || name.isBlank()) {
            return;
        }
        variants.addAll(aliasIndex.aliasVariants(name));
    }

    public List<BranchEntry> entries() {
        return entries;
    }

    /** Exact variant lookup - the fast path before any fuzzy scoring runs. */
    public BranchEntry findExact(String variant) {
        return variant == null ? null : exactLookup.get(variant);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public static BranchDictionary empty() {
        return new BranchDictionary(List.of(), Map.of());
    }
}
