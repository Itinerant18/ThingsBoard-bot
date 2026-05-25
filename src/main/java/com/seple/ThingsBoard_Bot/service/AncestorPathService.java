package com.seple.ThingsBoard_Bot.service;

import com.seple.ThingsBoard_Bot.entity.BranchAncestorPath;
import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.BranchAncestorPathRepository;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AncestorPathService {

    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final BranchAncestorPathRepository branchAncestorPathRepository;
    private final AncestorPathCache ancestorPathCache;

    @Transactional
    public void computeForCustomer(String customerId) {
        log.info("[HIERARCHY] Computing ancestor paths for customer: {}", customerId);
        List<HierarchyNode> branches = hierarchyNodeRepository.findByCustomerIdAndIsLeaf(customerId, true);
        List<BranchAncestorPath> pathsToSave = new ArrayList<>();

        for (HierarchyNode branch : branches) {
            List<String> path = new ArrayList<>();
            String currentParentId = branch.getParentId();
            while (currentParentId != null && !currentParentId.isBlank()) {
                path.add(0, currentParentId); // Prepend to get HO-first order
                Optional<HierarchyNode> parentOpt = hierarchyNodeRepository.findById(currentParentId);
                if (parentOpt.isPresent()) {
                    currentParentId = parentOpt.get().getParentId();
                } else {
                    log.warn("[HIERARCHY] Parent node not found: {}", currentParentId);
                    break;
                }
            }

            String[] pathArray = path.toArray(new String[0]);
            BranchAncestorPath ancestorPath = BranchAncestorPath.builder()
                    .branchNodeId(branch.getNodeId())
                    .customerId(customerId)
                    .ancestorPath(pathArray)
                    .pathDepth(pathArray.length)
                    .build();
            pathsToSave.add(ancestorPath);
        }

        // Clear existing paths first to avoid constraints issues
        branchAncestorPathRepository.deleteByCustomerId(customerId);
        branchAncestorPathRepository.saveAll(pathsToSave);
        log.info("[HIERARCHY] Successfully computed and saved {} ancestor paths for customer: {}", pathsToSave.size(), customerId);
    }

    public void reloadForCustomer(String customerId) {
        log.info("[HIERARCHY] Reloading ancestor path cache for customer: {}", customerId);
        ancestorPathCache.invalidateAll(customerId);
        List<BranchAncestorPath> paths = branchAncestorPathRepository.findByCustomerId(customerId);
        for (BranchAncestorPath path : paths) {
            ancestorPathCache.cacheAncestors(customerId, path.getBranchNodeId(), path.getAncestorList());
        }
        log.info("[HIERARCHY] Successfully reloaded {} ancestor paths into Redis for customer: {}", paths.size(), customerId);
    }
}
