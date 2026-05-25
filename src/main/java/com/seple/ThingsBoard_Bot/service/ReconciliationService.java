package com.seple.ThingsBoard_Bot.service;

import com.seple.ThingsBoard_Bot.entity.Customer;
import com.seple.ThingsBoard_Bot.entity.DeviceEvent;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.CustomerRepository;
import com.seple.ThingsBoard_Bot.repository.DeviceEventRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final CustomerRepository customerRepository;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final DeviceEventRepository deviceEventRepository;
    private final RedisCacheService redisCacheService;
    private final ReplayService replayService;

    @Value("${app.reconciliation.auto-repair:true}")
    private boolean autoRepair;

    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata") // Runs at 2:00 AM IST
    public void reconcileAllCustomers() {
        log.info("[RECONCILE] Starting scheduled drift reconciliation...");
        List<Customer> customers = customerRepository.findAll();
        for (Customer customer : customers) {
            try {
                reconcileCustomer(customer.getCustomerId());
            } catch (Exception e) {
                log.error("[RECONCILE] Failed to reconcile customer: {}", customer.getCustomerId(), e);
            }
        }
        log.info("[RECONCILE] Reconciliation complete.");
    }

    public void reconcileCustomer(String customerId) {
        log.info("[RECONCILE] Reconciling state for customer: {}", customerId);

        // 1. Get all leaf nodes (branches) for this customer
        List<HierarchyNode> branches = hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true);
        int totalBranches = branches.size();

        // 2. Fetch the latest gateway_status events for each branch from PostgreSQL
        List<DeviceEvent> latestEvents = deviceEventRepository.findLatestEventsForEachBranch(customerId, "gateway_status");

        long trueOnline = 0;
        for (DeviceEvent event : latestEvents) {
            if ("online".equalsIgnoreCase(event.getNewValue())) {
                trueOnline++;
            }
        }
        long trueOffline = totalBranches - trueOnline;

        // 3. Fetch counters from Redis
        Map<Object, Object> redisCounters = redisCacheService.getGlobalCounters(customerId);
        long redisOnline = getCounterValue(redisCounters, "total_online");
        long redisOffline = getCounterValue(redisCounters, "total_offline");
        long redisTotal = getCounterValue(redisCounters, "total_branches");

        log.info("[RECONCILE] Customer: {}. DB Status: Total={}, Online={}, Offline={}. Redis Status: Total={}, Online={}, Offline={}",
                customerId, totalBranches, trueOnline, trueOffline, redisTotal, redisOnline, redisOffline);

        // 4. Mismatch check
        boolean driftDetected = (trueOnline != redisOnline) || (trueOffline != redisOffline) || (totalBranches != redisTotal);

        if (driftDetected) {
            log.warn("⚠️ [RECONCILE] Drift detected for customer: {}! Rebuilding Redis state...", customerId);
            if (autoRepair) {
                // Auto repair by replaying events for the last 30 days to build exact state
                Instant startTime = Instant.now().minus(30, ChronoUnit.DAYS);
                Instant endTime = Instant.now();
                replayService.replayForCustomer(customerId, startTime, endTime);
                log.info("[RECONCILE] Auto-repair replay completed for customer: {}", customerId);
            }
        } else {
            log.info("✅ [RECONCILE] Redis cache matches PostgreSQL database for customer: {}", customerId);
        }
    }

    private long getCounterValue(Map<Object, Object> counters, String key) {
        if (counters == null || !counters.containsKey(key)) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(counters.get(key)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
