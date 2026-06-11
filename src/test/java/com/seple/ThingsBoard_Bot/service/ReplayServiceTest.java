package com.seple.ThingsBoard_Bot.service;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.entity.DeviceEvent;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.DeviceEventRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;

class ReplayServiceTest {

    private DeviceEventRepository deviceEventRepository;
    private HierarchyNodeRepository hierarchyNodeRepository;
    private AncestorPathService ancestorPathService;
    private RedisCacheService redisCacheService;
    private LuaScriptService luaScriptService;
    private AncestorPathCache ancestorPathCache;
    private ReplayService replayService;

    @BeforeEach
    void setUp() {
        deviceEventRepository = mock(DeviceEventRepository.class);
        hierarchyNodeRepository = mock(HierarchyNodeRepository.class);
        ancestorPathService = mock(AncestorPathService.class);
        redisCacheService = mock(RedisCacheService.class);
        luaScriptService = mock(LuaScriptService.class);
        ancestorPathCache = mock(AncestorPathCache.class);
        
        replayService = new ReplayService(
                deviceEventRepository,
                hierarchyNodeRepository,
                ancestorPathService,
                redisCacheService,
                luaScriptService,
                ancestorPathCache
        );
    }

    @Test
    void shouldExecuteReplayChronologically() {
        String customerId = "BOI";
        Instant start = Instant.now().minusSeconds(100);
        Instant end = Instant.now();

        HierarchyNode branch = new HierarchyNode();
        branch.setNodeId("BOI_BR042");
        branch.setDisplayName("Bally Bazar Branch");
        branch.setIsLeaf(true);
        branch.setTbDeviceId(UUID.randomUUID());

        DeviceEvent event = new DeviceEvent();
        event.setCustomerId(customerId);
        event.setBranchNodeId("BOI_BR042");
        event.setField("gateway_status");
        event.setNewValue("online");
        event.setPrevValue("offline");
        event.setEventTime(start.plusSeconds(10));

        when(redisCacheService.tryAcquireReplayLock(customerId)).thenReturn(true);
        when(hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true)).thenReturn(List.of(branch));
        when(deviceEventRepository.streamByCustomerIdAndTimeRange(customerId, start, end)).thenReturn(List.of(event));
        when(ancestorPathCache.getAncestors(customerId, "BOI_BR042")).thenReturn(List.of("HO_BOI", "ZO_KOLKATA"));

        replayService.replayForCustomer(customerId, start, end);

        verify(redisCacheService).clearCustomerCache(customerId);
        verify(ancestorPathService).reloadForCustomer(customerId);
        verify(redisCacheService).writeBulkData(
                eq(customerId),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap(),
                anyMap()
        );
    }
}
