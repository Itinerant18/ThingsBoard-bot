package com.seple.ThingsBoard_Bot.controller;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import com.seple.ThingsBoard_Bot.service.AncestorPathService;
import com.seple.ThingsBoard_Bot.service.RabbitMQQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/hierarchy")
@RequiredArgsConstructor
public class HierarchyAdminController {

    private final HierarchyNodeRepository hierarchyNodeRepository;
    private final AncestorPathService ancestorPathService;
    private final RabbitMQQueueService rabbitMQQueueService;
    private final com.seple.ThingsBoard_Bot.service.RedisCacheService redisCacheService;

    private static String deviceIdOf(HierarchyNode node) {
        return node.getTbDeviceId() != null ? node.getTbDeviceId().toString() : node.getNodeId();
    }

    /**
     * Minimal RFC-4180-style CSV line parser (audit #24): honours double-quoted fields so commas
     * and escaped quotes ("") inside a quoted value don't split the row, unlike a naive split(",").
     */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    @PostMapping("/import")
    @Transactional
    public ResponseEntity<Map<String, Object>> importHierarchyCsv(
            @RequestParam("customerId") String customerId,
            @RequestParam("file") MultipartFile file) {

        log.info("[HIERARCHY-API] Importing hierarchy CSV for customer: {}", customerId);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "CSV file is empty"));
            }

            List<HierarchyNode> nodesToSave = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> parts = parseCsvLine(line);
                if (parts.size() < 7) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Invalid CSV format at line " + lineNumber + ". Expected at least 7 columns."
                    ));
                }

                String nodeId = parts.get(0).trim();
                String custId = parts.get(1).trim();
                String parentId = parts.get(2).trim().isEmpty() ? null : parts.get(2).trim();
                String nodeType = parts.get(3).trim();
                int nodeLevel;
                try {
                    nodeLevel = Integer.parseInt(parts.get(4).trim());
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Invalid node level '" + parts.get(4).trim() + "' at line " + lineNumber
                                    + " (expected an integer)."
                    ));
                }
                String displayName = parts.get(5).trim();
                boolean isLeaf = Boolean.parseBoolean(parts.get(6).trim());
                UUID tbDeviceId = null;
                if (parts.size() > 7 && !parts.get(7).trim().isEmpty()) {
                    try {
                        tbDeviceId = UUID.fromString(parts.get(7).trim());
                    } catch (IllegalArgumentException e) {
                        log.warn("[HIERARCHY-API] Invalid UUID '{}' at line {}", parts.get(7), lineNumber);
                    }
                }

                if (!customerId.equalsIgnoreCase(custId)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Customer ID mismatch at line " + lineNumber + ". Expected: " + customerId + ", Found: " + custId
                    ));
                }

                HierarchyNode node = HierarchyNode.builder()
                        .nodeId(nodeId)
                        .customerId(custId)
                        .parentId(parentId)
                        .nodeType(nodeType)
                        .nodeLevel(nodeLevel)
                        .displayName(displayName)
                        .isLeaf(isLeaf)
                        .tbDeviceId(tbDeviceId)
                        .build();

                nodesToSave.add(node);
            }

            // 1. Delete existing nodes for this customer
            List<HierarchyNode> existingNodes = hierarchyNodeRepository.findByCustomerId(customerId);

            // Capture the device ids of branches being removed so their stale Redis keys can be
            // purged (audit #16) — otherwise removed branches stay queryable in the chatbot.
            java.util.Set<String> oldLeafDeviceIds = existingNodes.stream()
                    .filter(n -> Boolean.TRUE.equals(n.getIsLeaf()))
                    .map(HierarchyAdminController::deviceIdOf)
                    .collect(java.util.stream.Collectors.toSet());

            hierarchyNodeRepository.deleteAll(existingNodes);

            // 2. Save all new nodes
            hierarchyNodeRepository.saveAll(nodesToSave);

            // 2b. Purge Redis ghost keys for branches that no longer exist in the new hierarchy.
            java.util.Set<String> newLeafDeviceIds = nodesToSave.stream()
                    .filter(n -> Boolean.TRUE.equals(n.getIsLeaf()))
                    .map(HierarchyAdminController::deviceIdOf)
                    .collect(java.util.stream.Collectors.toSet());
            int purged = 0;
            for (String removedDeviceId : oldLeafDeviceIds) {
                if (!newLeafDeviceIds.contains(removedDeviceId)) {
                    redisCacheService.deleteDevice(customerId, removedDeviceId);
                    purged++;
                }
            }
            if (purged > 0) {
                log.info("[HIERARCHY-API] Purged {} ghost device cache entries removed from hierarchy for {}. "
                        + "Run a replay to rebuild aggregate counters.", purged, customerId);
            }

            // 3. Trigger Ancestor Path computation
            ancestorPathService.computeForCustomer(customerId);

            // 4. Reload paths into Cache
            ancestorPathService.reloadForCustomer(customerId);

            // 5. Ensure RabbitMQ queue exists for this customer
            rabbitMQQueueService.declareQueueForCustomer(customerId);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "customerId", customerId,
                    "importedNodesCount", nodesToSave.size(),
                    "message", "Hierarchy imported and processed successfully"
            ));

        } catch (Exception e) {
            log.error("[HIERARCHY-API] CSV Import failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/node")
    @Transactional
    public ResponseEntity<Map<String, Object>> addOrUpdateNode(
            @RequestBody HierarchyNode node) {
        
        log.info("[HIERARCHY-API] Creating/updating hierarchy node: {}", node.getNodeId());

        if (node.getNodeId() == null || node.getCustomerId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "nodeId and customerId are required"));
        }

        try {
            hierarchyNodeRepository.save(node);
            
            // Recompute and reload paths
            ancestorPathService.computeForCustomer(node.getCustomerId());
            ancestorPathService.reloadForCustomer(node.getCustomerId());

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "nodeId", node.getNodeId(),
                    "message", "Node updated successfully and paths recomputed"
            ));
        } catch (Exception e) {
            log.error("[HIERARCHY-API] Node update failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }
}
