package com.seple.ThingsBoard_Bot.service.query.resolve;

import java.util.List;

/**
 * Task 1.3 - Outcome of resolving a user-supplied branch name against the dictionary,
 * bucketed into the plan's confidence bands.
 *
 * @param status     which band the best match fell into
 * @param match      the winning entry ({@code RESOLVED} / {@code NEEDS_CONFIRMATION}), else null
 * @param candidates ranked candidates (best first); top-3 for {@code SUGGESTIONS}
 */
public record BranchResolution(Status status, BranchEntry match, List<ScoredCandidate> candidates) {

    public enum Status {
        /** Score above the silent threshold - proceed without asking. */
        RESOLVED,
        /** Mid band - halt and ask "Did you mean X?". */
        NEEDS_CONFIRMATION,
        /** Low band - present the top matches as options. */
        SUGGESTIONS,
        /** Nothing plausible found (or empty dictionary/input). */
        NO_MATCH
    }

    public record ScoredCandidate(BranchEntry entry, double score) {
    }

    public static BranchResolution noMatch() {
        return new BranchResolution(Status.NO_MATCH, null, List.of());
    }
}
