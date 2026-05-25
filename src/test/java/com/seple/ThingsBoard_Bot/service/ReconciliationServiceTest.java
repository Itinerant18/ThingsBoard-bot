package com.seple.ThingsBoard_Bot.service;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.seple.ThingsBoard_Bot.entity.DeviceEvent;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.CustomerRepository;
import com.seple.ThingsBoard_Bot.repository.DeviceEventRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;

class ReconciliationServiceTest {

    private CustomerRepository customerRepository;
    private HierarchyNodeRepository hierarchyNodeRepository;
    private DeviceEventRepository deviceEventRepository;
    private RedisCacheService redisCacheService;
    private ReplayService replayService;
    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        hierarchyNodeRepository = mock(HierarchyNodeRepository.class);
        deviceEventRepository = mock(DeviceEventRepository.class);
        redisCacheService = mock(RedisCacheService.class);
        replayService = mock(ReplayService.class);
        
        reconciliationService = new ReconciliationService(
                customerRepository,
                hierarchyNodeRepository,
                deviceEventRepository,
                redisCacheService,
                replayService
        );
        ReflectionTestUtils.setField(reconciliationService, "autoRepair", true);
    }

    @Test
    void shouldTriggerReplayWhenDriftIsDetected() {
        String customerId = "BOI";

        HierarchyNode branch1 = new HierarchyNode();
        branch1.setNodeId("BOI_BR042");
        branch1.setIsLeaf(true);

        HierarchyNode branch2 = new HierarchyNode();
        branch2.setNodeId("BOI_BR043");
        branch2.setIsLeaf(true);

        DeviceEvent event1 = new DeviceEvent();
        event1.setBranchNodeId("BOI_BR042");
        event1.setNewValue("online");

        DeviceEvent event2 = new DeviceEvent();
        event2.setBranchNodeId("BOI_BR043");
        event2.setNewValue("offline");

        when(hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true)).thenReturn(List.of(branch1, branch2));
        when(deviceEventRepository.findLatestEventsForEachBranch(customerId, "gateway_status")).thenReturn(List.of(event1, event2));

        // Redis has 2 online, 0 offline -> Mismatch! (True counts: 1 online, 1 offline)
        when(redisCacheService.getGlobalCounters(customerId)).thenReturn(Map.of(
                "total_online", "2",
                "total_offline", "0",
                "total_branches", "2"
        ));

        reconciliationService.reconcileCustomer(customerId);

        verify(replayService).replayForCustomer(eq(customerId), any(Instant.class), any(Instant.class));
    }

    @Test
    void shouldNotTriggerReplayWhenNoDrift() {
        String customerId = "BOI";

        HierarchyNode branch1 = new HierarchyNode();
        branch1.setNodeId("BOI_BR042");
        branch1.setIsLeaf(true);

        DeviceEvent event1 = new DeviceEvent();
        event1.setBranchNodeId("BOI_BR042");
        event1.setNewValue("online");

        when(hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true)).thenReturn(List.of(branch1));
        when(deviceEventRepository.findLatestEventsForEachBranch(customerId, "gateway_status")).thenReturn(List.of(event1));

        // Redis matches true database state (1 online, 0 offline)
        when(redisCacheService.getGlobalCounters(customerId)).thenReturn(Map.of(
                "total_online", "1",
                "total_offline", "0",
                "total_branches", "1"
        ));

        reconciliationService.reconcileCustomer(customerId);

        verify(replayService, never()).replayForCustomer(any(), any(), any());
    }
}
