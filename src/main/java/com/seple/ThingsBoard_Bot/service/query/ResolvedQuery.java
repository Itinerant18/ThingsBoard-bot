package com.seple.ThingsBoard_Bot.service.query;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchResolution;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ResolvedQuery {
    QueryIntent intent;
    String originalQuestion;
    BranchSnapshot targetBranch;
    String targetSystem;
    String targetAttribute;
    boolean global;
    boolean ambiguous;
    boolean branchFromMemory;
    boolean deterministic;
    double confidence;
    /** Fuzzy branch-name outcome when exact matching failed; null when not attempted. */
    BranchResolution branchResolution;
}
