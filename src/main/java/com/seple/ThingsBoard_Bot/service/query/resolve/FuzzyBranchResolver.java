package com.seple.ThingsBoard_Bot.service.query.resolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchResolution.ScoredCandidate;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchResolution.Status;

/**
 * Task 1.2/1.3 - Fuzzy branch-name matching with confidence bands. Tries the exact
 * dictionary lookup first (including manual shorthands), then Jaro-Winkler scores every
 * entry's variants and buckets the best match:
 *
 * <ul>
 *   <li>score &gt; silent threshold (default 0.90) - {@code RESOLVED}, proceed silently</li>
 *   <li>confirm threshold (default 0.75) to silent - {@code NEEDS_CONFIRMATION}, ask the user</li>
 *   <li>below confirm but above the floor - {@code SUGGESTIONS}, top 3 options</li>
 *   <li>below the floor (default 0.55) or empty input/dictionary - {@code NO_MATCH}</li>
 * </ul>
 *
 * Thresholds live in configuration so Phase 5 calibration can tune them without code changes.
 */
@Service
public class FuzzyBranchResolver {

    private static final int MAX_SUGGESTIONS = 3;

    private final JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();
    private final BranchAliasIndex aliasIndex;
    private final ManualAliasTable manualAliasTable;
    private final double silentThreshold;
    private final double confirmThreshold;
    private final double suggestionFloor;

    public FuzzyBranchResolver(BranchAliasIndex aliasIndex, ManualAliasTable manualAliasTable,
            @Value("${iotchatbot.fuzzy.silent-threshold:0.90}") double silentThreshold,
            @Value("${iotchatbot.fuzzy.confirm-threshold:0.75}") double confirmThreshold,
            @Value("${iotchatbot.fuzzy.suggestion-floor:0.55}") double suggestionFloor) {
        this.aliasIndex = aliasIndex;
        this.manualAliasTable = manualAliasTable;
        this.silentThreshold = silentThreshold;
        this.confirmThreshold = confirmThreshold;
        this.suggestionFloor = suggestionFloor;
    }

    public BranchResolution resolve(String rawName, BranchDictionary dictionary) {
        if (rawName == null || rawName.isBlank() || dictionary == null || dictionary.isEmpty()) {
            return BranchResolution.noMatch();
        }

        String canonical = manualAliasTable.canonicalize(rawName, aliasIndex);
        String compact = canonical.replace(" ", "");

        BranchEntry exact = dictionary.findExact(canonical);
        if (exact == null) {
            exact = dictionary.findExact(compact);
        }
        if (exact != null) {
            return new BranchResolution(Status.RESOLVED, exact, List.of(new ScoredCandidate(exact, 1.0)));
        }

        List<ScoredCandidate> ranked = new ArrayList<>();
        for (BranchEntry entry : dictionary.entries()) {
            double best = 0.0;
            for (String variant : entry.variants()) {
                best = Math.max(best, similarity.apply(canonical, variant));
                best = Math.max(best, similarity.apply(compact, variant));
            }
            if (best >= suggestionFloor) {
                ranked.add(new ScoredCandidate(entry, best));
            }
        }
        ranked.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());

        if (ranked.isEmpty()) {
            return BranchResolution.noMatch();
        }

        ScoredCandidate top = ranked.get(0);
        List<ScoredCandidate> suggestions = ranked.subList(0, Math.min(MAX_SUGGESTIONS, ranked.size()));
        if (top.score() > silentThreshold) {
            return new BranchResolution(Status.RESOLVED, top.entry(), List.copyOf(suggestions));
        }
        if (top.score() >= confirmThreshold) {
            return new BranchResolution(Status.NEEDS_CONFIRMATION, top.entry(), List.copyOf(suggestions));
        }
        return new BranchResolution(Status.SUGGESTIONS, null, List.copyOf(suggestions));
    }
}
