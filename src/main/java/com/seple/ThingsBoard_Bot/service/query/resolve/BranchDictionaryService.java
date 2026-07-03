package com.seple.ThingsBoard_Bot.service.query.resolve;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;

import lombok.RequiredArgsConstructor;

/**
 * Task 1.1 - Local canonical branch dictionary. Caches branch (leaf) hierarchy nodes from
 * PostgreSQL into memory per customer, indexed by exact name, normalized name and compact
 * shorthand variants, and refreshes on a fixed schedule. Lookups never hit the database.
 */
@Service
@RequiredArgsConstructor
public class BranchDictionaryService {

    private static final Logger log = LoggerFactory.getLogger(BranchDictionaryService.class);

    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final BranchAliasIndex branchAliasIndex;
    private final ManualAliasTable manualAliasTable;

    private final Map<String, BranchDictionary> dictionariesByCustomer = new ConcurrentHashMap<>();

    /**
     * Returns the cached dictionary for the customer, loading it on first access.
     * {@code "ALL"} returns a dictionary spanning every customer (admin scope).
     */
    public BranchDictionary getDictionary(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return BranchDictionary.empty();
        }
        return dictionariesByCustomer.computeIfAbsent(customerId, this::load);
    }

    @Scheduled(fixedDelayString = "${iotchatbot.branch-dictionary.refresh-seconds:300}000")
    public void refresh() {
        for (String customerId : dictionariesByCustomer.keySet()) {
            try {
                dictionariesByCustomer.put(customerId, load(customerId));
            } catch (Exception e) {
                log.warn("Branch dictionary refresh failed for customer {} - keeping stale copy", customerId, e);
            }
        }
    }

    private BranchDictionary load(String customerId) {
        List<HierarchyNode> leaves = "ALL".equals(customerId)
                ? hierarchyNodeRepository.findAll().stream().filter(n -> Boolean.TRUE.equals(n.getIsLeaf())).toList()
                : hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true);
        BranchDictionary dictionary = BranchDictionary.fromHierarchyNodes(leaves, branchAliasIndex)
                .withManualAliases(manualAliasTable.mappings());
        log.info("Branch dictionary loaded for customer {}: {} branches", customerId, dictionary.entries().size());
        return dictionary;
    }
}
