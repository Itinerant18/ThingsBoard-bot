package com.seple.ThingsBoard_Bot.util;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Single source of truth for regional/zone scoping of a user's branch list.
 *
 * <p>A BOI {@code CUSTOMER_USER} is assigned to one ThingsBoard customer that owns the whole
 * branch inventory; the regional sub-scope (e.g. {@code NBG JH} → 21 branches) is modelled only in
 * the hierarchy via {@link BranchAncestorPath}. The ThingsBoard ACL therefore returns the full
 * customer inventory and cannot narrow to a region on its own — callers MUST additionally apply
 * {@link #filterByRegion} after any device-ACL filtering.
 *
 * <p>Previously this logic was duplicated in {@code UserDataService} (correct, region-aware) and
 * {@code BranchIndexService} (a ZO-only fallback that never ran and could not match {@code NBG}),
 * which let the index path leak all branches to a region-scoped user. Both now delegate here.
 *
 * <p><strong>Fail-closed:</strong> when the token <em>explicitly</em> names a region (via the
 * firstName/lastName claim) that cannot be resolved to a hierarchy node, an empty list is returned
 * rather than the full inventory. A merely <em>guessed</em> region (derived from the {@code sub}
 * claim) that cannot be resolved leaves the list unfiltered, so legitimate non-regional users are
 * not locked out.
 */
@Slf4j
public final class RegionalScopeUtil {

    private static final List<String> REGIONAL_PREFIXES =
            List.of("FGMO", "LHO", "ZO", "CO", "RO", "RBO", "NBG");

    private RegionalScopeUtil() {
    }

    /**
     * Narrows {@code leafNodes} to the branches within the user's regional scope.
     *
     * @param userToken     the caller's JWT (claims are advisory; see {@link JwtParserUtil})
     * @param leafNodes     the leaf (branch) nodes already filtered by the ThingsBoard device ACL
     * @param allNodes      all hierarchy nodes for the customer (leaf and non-leaf)
     * @param ancestorPaths branch → ancestor-id paths for the customer
     * @return the region-scoped subset of {@code leafNodes} (possibly empty when failing closed)
     */
    public static List<HierarchyNode> filterByRegion(String userToken,
                                                     List<HierarchyNode> leafNodes,
                                                     List<HierarchyNode> allNodes,
                                                     List<BranchAncestorPath> ancestorPaths) {
        String regionName = resolveRegionName(userToken);
        if (regionName == null) {
            // Token carries no regional scope at all — nothing to narrow.
            return leafNodes;
        }

        String normalizedRegion = normalizeName(regionName);
        Optional<HierarchyNode> regionNodeOpt = allNodes.stream()
                .filter(node -> !Boolean.TRUE.equals(node.getIsLeaf()) && regionMatches(node, normalizedRegion))
                .findFirst();

        if (regionNodeOpt.isEmpty()) {
            if (resolveExplicitRegionName(userToken) != null) {
                // FAIL CLOSED: the token explicitly scopes the user to a region we cannot resolve.
                // Returning the full inventory here would be a scope leak, so deny instead.
                log.warn("[SCOPE] Token explicitly scopes to region '{}' but no matching hierarchy "
                        + "node was found; failing closed (empty scope).", regionName);
                return List.of();
            }
            // Region was only guessed from the sub claim; leave the list unfiltered rather than
            // lock out a user who may legitimately be customer-wide.
            log.warn("[SCOPE] Could not resolve guessed region '{}' to a hierarchy node; leaving "
                    + "branch list unfiltered.", regionName);
            return leafNodes;
        }

        String regionNodeId = regionNodeOpt.get().getNodeId();
        Set<String> branchIdsInRegion = ancestorPaths.stream()
                .filter(path -> path.getAncestorList().contains(regionNodeId))
                .map(BranchAncestorPath::getBranchNodeId)
                .collect(Collectors.toSet());

        List<HierarchyNode> scoped = leafNodes.stream()
                .filter(node -> branchIdsInRegion.contains(node.getNodeId()))
                .toList();
        log.info("[SCOPE] Filtered branch list to {} branches for region '{}'.", scoped.size(), regionName);
        return scoped;
    }

    /**
     * Resolves the region/zone name a token is scoped to, or {@code null} if none can be derived.
     * Prefers the explicit firstName/lastName claim, then falls back to a guess from {@code sub}.
     */
    public static String resolveRegionName(String userToken) {
        String explicit = resolveExplicitRegionName(userToken);
        if (explicit != null) {
            return explicit;
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
     * Resolves a region name only from the explicit firstName/lastName claim (a reliable signal
     * that the user is region-scoped), or {@code null} if the claim carries no regional prefix.
     */
    public static String resolveExplicitRegionName(String userToken) {
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
        return null;
    }

    /**
     * Matches a hierarchy node against the normalized region name from the token.
     *
     * <p>Beyond exact display-name and node-id containment, this tolerates abbreviation mismatches
     * between the JWT and the hierarchy: the token may carry the full word (e.g. {@code NBG
     * JHARKHAND}) while the hierarchy uses an abbreviation (e.g. {@code NBG JH}), or vice-versa.
     * A prefix match in either direction is accepted, guarded by a minimum length so a bare prefix
     * such as {@code NBG} cannot match every NBG region.
     */
    private static boolean regionMatches(HierarchyNode node, String normalizedRegion) {
        if (normalizedRegion.isEmpty()) {
            return false;
        }
        String nameNorm = normalizeName(node.getDisplayName());
        String idNorm = normalizeName(node.getNodeId());
        if (nameNorm.equals(normalizedRegion) || idNorm.equals(normalizedRegion)) {
            return true;
        }
        // The node id often embeds the region token (e.g. "BOI_NBG_JH_..."); keep the substring match.
        if (idNorm.contains(normalizedRegion)) {
            return true;
        }
        // Abbreviation tolerance: "NBGJHARKHAND" (token) vs "NBGJH" (data). Require >= 5 chars on the
        // shorter side so "NBG" alone cannot prefix-match unrelated NBG regions.
        if (nameNorm.length() >= 5 && (normalizedRegion.startsWith(nameNorm) || nameNorm.startsWith(normalizedRegion))) {
            return true;
        }
        return false;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }
}
