package com.seple.ThingsBoard_Bot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.client.ThingsBoardClient;
import com.seple.ThingsBoard_Bot.entity.Customer;
import com.seple.ThingsBoard_Bot.repository.CustomerRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-populates the {@code customers} mapping table from ThingsBoard so a new tenant does not
 * require manual DB intervention (audit finding #26).
 *
 * <p>Safe by construction: matching is exact/explicit via {@link CustomerMatcher} (never substring
 * guessing), and an unmatched customer is logged and skipped rather than mapped to the wrong
 * tenant. Runs once on the {@code chat} node only, and only when
 * {@code iotchatbot.customers.sync-enabled=true} (default off, so dev/tests don't call TB on boot).
 *
 * <p>This pairs with the fail-closed resolution in finding #1 — the sync reduces how often a
 * mapping is missing, but a missing/ambiguous mapping still fails closed rather than defaulting.
 */
@Slf4j
@Component
@Profile("chat")
@RequiredArgsConstructor
public class CustomerSyncRunner {

    private final ThingsBoardClient thingsBoardClient;
    private final CustomerRepository customerRepository;
    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final Environment env;

    @EventListener(ApplicationReadyEvent.class)
    public void syncCustomersOnStartup() {
        if (!Boolean.parseBoolean(env.getProperty("iotchatbot.customers.sync-enabled", "false"))) {
            log.debug("[CUSTOMER-SYNC] disabled (iotchatbot.customers.sync-enabled=false) — skipping.");
            return;
        }

        Map<String, String> explicit = parseExplicitMappings(
                env.getProperty("iotchatbot.customers.title-mappings", ""));

        try {
            log.info("🔄 [CUSTOMER-SYNC] Synchronizing customer mappings from ThingsBoard...");
            List<Map<String, String>> tbCustomers = thingsBoardClient.getTenantCustomers();
            List<String> dbCustomerIds = hierarchyNodeRepository.findDistinctCustomerIds();

            int mapped = 0;
            int skipped = 0;
            for (Map<String, String> tbCust : tbCustomers) {
                String tbUuid = tbCust.get("id");
                String tbTitle = tbCust.get("title");
                if (tbUuid == null || tbTitle == null) {
                    continue;
                }

                String prefix = CustomerMatcher.match(tbTitle, dbCustomerIds, explicit);
                if (prefix == null) {
                    log.warn("[CUSTOMER-SYNC] No confident mapping for TB customer '{}' (uuid {}); skipping "
                            + "(no guessing). Add an explicit iotchatbot.customers.title-mappings entry if needed.",
                            tbTitle, tbUuid);
                    skipped++;
                    continue;
                }

                Customer customer = customerRepository.findByTbCustomerId(tbUuid).orElseGet(Customer::new);
                if (customer.getCreatedAt() == null) {
                    customer.setCreatedAt(java.time.Instant.now());
                }
                customer.setCustomerId(prefix);
                customer.setTbCustomerId(tbUuid);
                customer.setName(tbTitle);
                customer.setDisplayName(tbTitle);
                customerRepository.save(customer);
                log.info("✅ [CUSTOMER-SYNC] Mapped '{}' -> {} (uuid {})", tbTitle, prefix, tbUuid);
                mapped++;
            }
            log.info("[CUSTOMER-SYNC] Done. mapped={}, skipped={}", mapped, skipped);
        } catch (Exception e) {
            log.error("❌ [CUSTOMER-SYNC] Failed: {}", e.getMessage(), e);
        }
    }

    /** Parses {@code "Bank of India=BOI,SEPL=SEPL"} into normalized-title -> prefix. */
    private Map<String, String> parseExplicitMappings(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(CustomerMatcher.normalize(pair.substring(0, eq)), pair.substring(eq + 1).trim());
            }
        }
        return map;
    }
}
