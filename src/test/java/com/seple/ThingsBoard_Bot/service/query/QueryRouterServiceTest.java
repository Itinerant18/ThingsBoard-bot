package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.service.query.QueryRouterService.QueryComplexity;

class QueryRouterServiceTest {

    private RedisQueryService redisQueryService;
    private NodeNameResolver nodeNameResolver;
    private QueryRouterService queryRouterService;

    @BeforeEach
    void setUp() {
        redisQueryService = mock(RedisQueryService.class);
        nodeNameResolver = mock(NodeNameResolver.class);
        queryRouterService = new QueryRouterService(redisQueryService, nodeNameResolver,
                new QueryRuleRegistry(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    void shouldClassifyGlobalCountsAsSimple() {
        assertEquals(QueryComplexity.SIMPLE_REDIS, queryRouterService.classify("how many online"));
        assertEquals(QueryComplexity.SIMPLE_REDIS, queryRouterService.classify("list offline branches"));
        assertEquals(QueryComplexity.SIMPLE_REDIS, queryRouterService.classify("how many connected devices"));
    }

    @Test
    void shouldClassifyRegionalOrZoneQueriesAsSimple() {
        assertEquals(QueryComplexity.SIMPLE_REDIS, queryRouterService.classify("status in zone Kolkata"));
        assertEquals(QueryComplexity.SIMPLE_REDIS, queryRouterService.classify("how many online in region North"));
    }

    @Test
    void shouldClassifyBranchStatusStartAsSimple() {
        assertEquals(QueryComplexity.SIMPLE_REDIS, queryRouterService.classify("status of branch XYZ"));
        assertEquals(QueryComplexity.SIMPLE_REDIS, queryRouterService.classify("show status of branch ABC"));
    }

    @Test
    void shouldClassifyComplexQuestionsAsComplex() {
        assertEquals(QueryComplexity.COMPLEX_LLM, queryRouterService.classify("why is the voltage reading low?"));
        assertEquals(QueryComplexity.COMPLEX_LLM, queryRouterService.classify("what happened to the cameras yesterday?"));
    }

    @Test
    void shouldRouteAndAnswerGlobalOnlineCounts() {
        when(redisQueryService.getGlobalCounter("BOI", "total_online")).thenReturn(15L);

        String answer = queryRouterService.routeAndAnswerSimple("BOI", "how many active branches");
        assertEquals("**15 branches are currently ONLINE across all branches.**", answer);
    }

    @Test
    void shouldRouteAndAnswerGlobalOfflineCounts() {
        when(redisQueryService.getGlobalCounter("BOI", "total_offline")).thenReturn(3L);

        String answer = queryRouterService.routeAndAnswerSimple("BOI", "how many branches are offline");
        assertEquals("**3 branches are currently OFFLINE across all branches.**", answer);
    }

    @Test
    void shouldRouteAndAnswerNodeSpecificOnlineCounts() {
        HierarchyNode zone = new HierarchyNode();
        zone.setNodeId("zone-kolkata");
        zone.setDisplayName("Kolkata Zone");
        zone.setNodeType("ZONE");

        when(nodeNameResolver.resolveNodeInQuestion("BOI", "how many online in Kolkata Zone")).thenReturn(Optional.of(zone));
        when(redisQueryService.getNodeCounter("BOI", "zone-kolkata", "total_online")).thenReturn(5L);

        String answer = queryRouterService.routeAndAnswerSimple("BOI", "how many online in Kolkata Zone");
        assertEquals("**5 branches are currently ONLINE in Kolkata Zone (ZONE).**", answer);
    }

    @Test
    void shouldRouteAndAnswerNodeSpecificOfflineCounts() {
        HierarchyNode region = new HierarchyNode();
        region.setNodeId("reg-north");
        region.setDisplayName("North Region");
        region.setNodeType("REGION");

        when(nodeNameResolver.resolveNodeInQuestion("BOI", "how many offline in North Region")).thenReturn(Optional.of(region));
        when(redisQueryService.getNodeCounter("BOI", "reg-north", "total_offline")).thenReturn(2L);

        String answer = queryRouterService.routeAndAnswerSimple("BOI", "how many offline in North Region");
        assertEquals("**2 branches are currently OFFLINE in North Region (REGION).**", answer);
    }

    @Test
    void shouldRouteAndAnswerNodeOverview() {
        HierarchyNode node = new HierarchyNode();
        node.setNodeId("zo-patna");
        node.setDisplayName("ZO Patna");
        node.setNodeType("ZO");

        when(nodeNameResolver.resolveNodeInQuestion("BOI", "status in zo patna")).thenReturn(Optional.of(node));
        when(redisQueryService.getNodeCounter("BOI", "zo-patna", "total_branches")).thenReturn(10L);
        when(redisQueryService.getNodeCounter("BOI", "zo-patna", "total_online")).thenReturn(8L);
        when(redisQueryService.getNodeCounter("BOI", "zo-patna", "total_offline")).thenReturn(2L);

        String answer = queryRouterService.routeAndAnswerSimple("BOI", "status in zo patna");
        assertEquals("**Hierarchy Node ZO Patna (ZO):**\n- Total Branches: 10\n- Online: 8\n- Offline: 2", answer);
    }

    @Test
    void shouldReturnNullWhenCannotBeAnsweredFromRedis() {
        assertNull(queryRouterService.routeAndAnswerSimple("BOI", "random question that is not simple"));
    }
}
