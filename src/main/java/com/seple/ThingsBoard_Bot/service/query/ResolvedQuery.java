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
    /** The zone or NBG name to filter by (e.g. "ZO HOWRAH"); null when not zone-scoped. */
    String zoneFilter;
    /** Fuzzy branch-name outcome when exact matching failed; null when not attempted. */
    BranchResolution branchResolution;
    /** Requested rendering from the extractor; null means handler default. */
    ResponseFormat responseFormat;
}
