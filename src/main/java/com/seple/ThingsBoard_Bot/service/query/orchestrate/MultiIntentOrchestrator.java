package com.seple.ThingsBoard_Bot.service.query.orchestrate;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.config.ExtractorConfig;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.DeterministicAnswerService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;
import com.seple.ThingsBoard_Bot.service.query.extract.ExtractedIntent;
import com.seple.ThingsBoard_Bot.service.query.extract.ExtractionResult;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchDictionary;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchResolution;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Phase 2, Task 2.2 - executes the extractor's intent list against the existing deterministic
 * handlers. Per intent: resolve every entity through the Phase 1 fuzzy bands, build a
 * {@link ResolvedQuery}, dispatch to {@link DeterministicAnswerService}, then combine sections.
 *
 * <p>Safety behavior:
 * <ul>
 *   <li>intents below the configured confidence gate are dropped</li>
 *   <li>an entity in the confirmation/suggestion band short-circuits into a clarification -
 *       no partial answer for a possibly-wrong branch is ever produced</li>
 *   <li>an entity below the match floor produces "I couldn't find a branch named X" rather
 *       than a fabricated answer</li>
 *   <li>if nothing survives, {@code UNANSWERED} lets the caller fall back to the LLM path</li>
 * </ul>
 *
 * Intents run sequentially: handlers execute against in-memory snapshots, so per-intent cost
 * is microseconds and parallelism would buy nothing.
 */
@Slf4j
@Service
public class MultiIntentOrchestrator {

    private static final String SECTION_SEPARATOR = "\n\n---\n\n";

    private final FuzzyBranchResolver fuzzyBranchResolver;
    private final BranchAliasIndex branchAliasIndex;
    private final DeterministicAnswerService deterministicAnswerService;
    private final ExtractorConfig config;

    public MultiIntentOrchestrator(FuzzyBranchResolver fuzzyBranchResolver, BranchAliasIndex branchAliasIndex,
            DeterministicAnswerService deterministicAnswerService, ExtractorConfig config) {
        this.fuzzyBranchResolver = fuzzyBranchResolver;
        this.branchAliasIndex = branchAliasIndex;
        this.deterministicAnswerService = deterministicAnswerService;
        this.config = config;
    }

    public OrchestrationResult orchestrate(ExtractionResult extraction, String question,
            List<BranchSnapshot> snapshots, String customerId) {
        if (extraction == null || extraction.isEmpty()) {
            return OrchestrationResult.unanswered();
        }

        List<ExtractedIntent> executable = new ArrayList<>();
        for (ExtractedIntent intent : extraction.intents()) {
            if (intent.confidence() < config.getConfidenceGate()) {
                log.info("[ORCH] dropping {} (confidence {} below gate {})", intent.intent(),
                        intent.confidence(), config.getConfidenceGate());
                continue;
            }
            executable.add(intent);
        }
        if (executable.isEmpty()) {
            return OrchestrationResult.unanswered();
        }

        BranchDictionary dictionary = BranchDictionary.fromSnapshots(snapshots, customerId, branchAliasIndex);
        StringJoiner sections = new StringJoiner(SECTION_SEPARATOR);
        int answered = 0;

        for (ExtractedIntent intent : executable) {
            IntentOutcome outcome = execute(intent, question, snapshots, dictionary, customerId);
            if (outcome.clarification != null) {
                // A possibly-wrong branch must halt the whole reply, not ride along beside
                // other sections where it could be mistaken for a confirmed answer.
                return OrchestrationResult.clarification(outcome.clarification);
            }
            if (outcome.answer != null) {
                sections.add(outcome.answer);
                answered++;
            }
        }

        return answered > 0 ? OrchestrationResult.answered(sections.toString())
                : OrchestrationResult.unanswered();
    }

    private IntentOutcome execute(ExtractedIntent intent, String question, List<BranchSnapshot> snapshots,
            BranchDictionary dictionary, String customerId) {
        BranchSnapshot targetBranch = null;

        for (String entity : intent.entities()) {
            BranchResolution resolution = fuzzyBranchResolver.resolve(entity, dictionary);
            switch (resolution.status()) {
                case RESOLVED -> {
                    BranchSnapshot snapshot = findSnapshot(snapshots, resolution.match().technicalId());
                    if (snapshot != null && targetBranch == null) {
                        targetBranch = snapshot;
                    }
                }
                case NEEDS_CONFIRMATION -> {
                    return IntentOutcome.clarify("Did you mean **" + resolution.match().displayName()
                            + "**? Please confirm the branch name and I'll fetch the data.");
                }
                case SUGGESTIONS -> {
                    StringJoiner names = new StringJoiner(", ");
                    resolution.candidates().forEach(c -> names.add(c.entry().displayName()));
                    return IntentOutcome.clarify("I couldn't find a branch named \"" + entity
                            + "\". Did you mean one of these?\n\n" + names);
                }
                case NO_MATCH -> {
                    return IntentOutcome.clarify("I couldn't find a branch named \"" + entity
                            + "\" in your accessible branches. Please check the name and try again.");
                }
            }
        }

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(intent.intent())
                .originalQuestion(question)
                .targetBranch(targetBranch)
                .global(intent.intent() == QueryIntent.GLOBAL_OVERVIEW)
                .deterministic(true)
                .confidence(intent.confidence())
                .responseFormat(intent.format())
                .build();

        String answer = deterministicAnswerService.answer(query, snapshots, customerId);
        if (answer == null) {
            log.info("[ORCH] intent {} produced no deterministic answer", intent.intent());
        }
        return IntentOutcome.answer(answer);
    }

    private BranchSnapshot findSnapshot(List<BranchSnapshot> snapshots, String technicalId) {
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() != null && technicalId.equals(snapshot.getIdentity().getTechnicalId())) {
                return snapshot;
            }
        }
        return null;
    }

    private record IntentOutcome(String answer, String clarification) {
        static IntentOutcome answer(String answer) {
            return new IntentOutcome(answer, null);
        }

        static IntentOutcome clarify(String clarification) {
            return new IntentOutcome(null, clarification);
        }
    }
}
