package com.seple.ThingsBoard_Bot.service.query;

import com.seple.ThingsBoard_Bot.entity.HierarchyNode;
import com.seple.ThingsBoard_Bot.repository.HierarchyNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NodeNameResolver {

    private final HierarchyNodeRepository hierarchyNodeRepository;

    public Optional<HierarchyNode> resolveNodeInQuestion(String customerId, String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String upperQuestion = question.toUpperCase();
        List<HierarchyNode> nodes = hierarchyNodeRepository.findByCustomerId(customerId);
        
        // Filter out leaf nodes (branches) and sort by display name length descending
        // to match longer names first (e.g. "Kolkata Zone" before "Kolkata")
        List<HierarchyNode> sortedNodes = nodes.stream()
                .filter(node -> !node.getIsLeaf())
                .sorted((n1, n2) -> Integer.compare(n2.getDisplayName().length(), n1.getDisplayName().length()))
                .toList();

        for (HierarchyNode node : sortedNodes) {
            String nodeName = node.getDisplayName().toUpperCase();
            if (upperQuestion.contains(nodeName) || upperQuestion.contains(node.getNodeId().toUpperCase())) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
}
