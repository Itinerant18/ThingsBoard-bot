package com.seple.ThingsBoard_Bot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Scope-resolution behaviour for the shared {@link RegionalScopeUtil}. Tokens are signed but
 * read unverified (no signing key configured in tests), matching production's advisory-claim mode.
 */
class RegionalScopeUtilTest {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    .getBytes(StandardCharsets.UTF_8));

    private String token(String firstName, String lastName, String sub) {
        var builder = Jwts.builder();
        if (firstName != null) builder.claim("firstName", firstName);
        if (lastName != null) builder.claim("lastName", lastName);
        if (sub != null) builder.claim("sub", sub);
        return builder.signWith(KEY).compact();
    }

    private HierarchyNode region(String nodeId, String displayName) {
        return HierarchyNode.builder().nodeId(nodeId).displayName(displayName)
                .nodeType("NBG").isLeaf(false).build();
    }

    private HierarchyNode leaf(String nodeId, String displayName) {
        return HierarchyNode.builder().nodeId(nodeId).displayName(displayName)
                .nodeType("DEVICE").isLeaf(true).build();
    }

    private BranchAncestorPath path(String branchNodeId, String... ancestors) {
        return BranchAncestorPath.builder().branchNodeId(branchNodeId)
                .ancestorPath(ancestors).pathDepth(ancestors.length).build();
    }

    @Test
    void explicitNbgRegion_narrowsToRegionBranches() {
        HierarchyNode jh = region("nbg-jh", "NBG JH");
        HierarchyNode b1 = leaf("b1", "BRANCH ONE");
        HierarchyNode b2 = leaf("b2", "BRANCH TWO");
        HierarchyNode b3 = leaf("b3", "BRANCH THREE"); // different region
        List<HierarchyNode> allNodes = List.of(jh, b1, b2, b3);
        List<HierarchyNode> leaves = List.of(b1, b2, b3);
        List<BranchAncestorPath> paths = List.of(
                path("b1", "nbg-jh", "boi-root"),
                path("b2", "nbg-jh"),
                path("b3", "nbg-east"));

        // firstName "BOI NBG" + lastName "Jh" => explicit region "NBG JH"
        List<HierarchyNode> result = RegionalScopeUtil.filterByRegion(
                token("BOI NBG", "Jh", "nb.jh@bankofindia.bank.in"), leaves, allNodes, paths);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(n -> n.getNodeId().equals("b1")));
        assertTrue(result.stream().anyMatch(n -> n.getNodeId().equals("b2")));
    }

    @Test
    void noRegionInToken_returnsListUnchanged() {
        HierarchyNode b1 = leaf("b1", "BRANCH ONE");
        List<HierarchyNode> leaves = List.of(b1);
        List<HierarchyNode> result = RegionalScopeUtil.filterByRegion(
                token(null, null, null), leaves, List.of(b1), List.of());
        assertEquals(leaves, result);
    }

    @Test
    void explicitRegionWithNoMatchingNode_failsClosed() {
        HierarchyNode b1 = leaf("b1", "BRANCH ONE");
        HierarchyNode b2 = leaf("b2", "BRANCH TWO");
        List<HierarchyNode> leaves = List.of(b1, b2);
        // Token explicitly names NBG JH, but the hierarchy has no such region node.
        List<HierarchyNode> result = RegionalScopeUtil.filterByRegion(
                token("BOI NBG", "Jh", null), leaves, List.of(b1, b2), List.of());
        assertTrue(result.isEmpty(), "explicit but unresolvable region must fail closed (empty)");
    }

    @Test
    void guessedRegionWithNoMatchingNode_leavesListUnfiltered() {
        HierarchyNode b1 = leaf("b1", "BRANCH ONE");
        HierarchyNode b2 = leaf("b2", "BRANCH TWO");
        List<HierarchyNode> leaves = List.of(b1, b2);
        // Only a sub claim (no firstName/lastName) => region is merely guessed ("ZO NB").
        List<HierarchyNode> result = RegionalScopeUtil.filterByRegion(
                token(null, null, "nb.xx@bankofindia.bank.in"), leaves, List.of(b1, b2), List.of());
        assertEquals(leaves, result, "guessed unresolvable region must not lock the user out");
    }

    @Test
    void abbreviatedHierarchyName_matchesFullWordInToken() {
        // JWT carries the full word (lastName "JHARKHAND" => region "NBG JHARKHAND") while the
        // hierarchy uses the abbreviation "NBG JH". The branch must still scope to NBG JH, and must
        // NOT bleed into a sibling region such as NBG EAST.
        HierarchyNode jh = region("nbg-jh", "NBG JH");
        HierarchyNode east = region("nbg-east", "NBG EAST");
        HierarchyNode b1 = leaf("b1", "BRANCH ONE");   // under NBG JH
        HierarchyNode b2 = leaf("b2", "BRANCH TWO");   // under NBG EAST
        List<HierarchyNode> allNodes = List.of(jh, east, b1, b2);
        List<HierarchyNode> leaves = List.of(b1, b2);
        List<BranchAncestorPath> paths = List.of(
                path("b1", "nbg-jh"),
                path("b2", "nbg-east"));

        List<HierarchyNode> result = RegionalScopeUtil.filterByRegion(
                token("BOI NBG", "JHARKHAND", null), leaves, allNodes, paths);

        assertEquals(1, result.size());
        assertTrue(result.stream().anyMatch(n -> n.getNodeId().equals("b1")));
    }

    @Test
    void resolveExplicitRegionName_picksRegionalPrefixFromName() {
        assertEquals("NBG JH", RegionalScopeUtil.resolveExplicitRegionName(token("BOI NBG", "Jh", null)));
        assertNull(RegionalScopeUtil.resolveExplicitRegionName(token("Some", "User", null)));
    }
}
