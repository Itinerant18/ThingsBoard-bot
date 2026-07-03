package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;
import com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService;
import com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService.GlossaryEntry;

/**
 * GLOSSARY, CONCEPT_EXPLAIN (Phase 3, Task 3.2). Answers strictly from {@code glossary.json} -
 * an unknown term gets an honest "no definition" instead of an invented one, per the
 * zero-fabrication release gate. CONCEPT_EXPLAIN renders the same definition plus related
 * terms; richer long-form content is a glossary-file upgrade, not a code change.
 */
@Component
public class GlossaryHandler implements AnswerHandler {

    private final GlossaryService glossaryService;

    public GlossaryHandler(GlossaryService glossaryService) {
        this.glossaryService = glossaryService;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.GLOSSARY || intent == QueryIntent.CONCEPT_EXPLAIN;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        GlossaryEntry entry = glossaryService.findTermInQuestion(query.getOriginalQuestion());
        if (entry == null) {
            return "I don't have a definition for that term. I can explain the monitoring vocabulary "
                    + "I work with - for example IAS, FAS, TLS, ACS, NBG, ZO, offline, stale, heartbeat, "
                    + "uptime, tamper, or HDD error.";
        }

        StringBuilder answer = new StringBuilder("**");
        answer.append(entry.term());
        if (entry.fullName() != null && !entry.fullName().isBlank()) {
            answer.append(" (").append(entry.fullName()).append(")");
        }
        answer.append(":** ").append(entry.definition());

        if (query.getIntent() == QueryIntent.CONCEPT_EXPLAIN && !entry.related().isEmpty()) {
            answer.append("\n\nRelated terms you can ask about: ").append(String.join(", ", entry.related()));
        }
        return answer.toString();
    }
}
