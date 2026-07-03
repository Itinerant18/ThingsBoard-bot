package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;
import com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService;

class GlossaryHandlerTest {

    private final GlossaryHandler handler = new GlossaryHandler(
            new GlossaryService(new ClassPathResource("glossary.json")));

    private ResolvedQuery query(QueryIntent intent, String question) {
        return ResolvedQuery.builder().intent(intent).originalQuestion(question).build();
    }

    @Test
    void supportsOnlyGlossaryIntents() {
        assertTrue(handler.supports(QueryIntent.GLOSSARY));
        assertTrue(handler.supports(QueryIntent.CONCEPT_EXPLAIN));
        assertFalse(handler.supports(QueryIntent.BATTERY_VOLTAGE));
    }

    @Test
    void answersKnownTermFromGlossaryOnly() {
        String answer = handler.handle(query(QueryIntent.GLOSSARY, "What does stale mean?"), List.of(), "BOI");
        assertTrue(answer.contains("**stale:**"));
        assertTrue(answer.toLowerCase().contains("telemetry"));
    }

    @Test
    void rendersFullNameForAbbreviations() {
        String answer = handler.handle(query(QueryIntent.GLOSSARY, "what is an IAS?"), List.of(), "BOI");
        assertTrue(answer.contains("**IAS (Intrusion Alarm System):**"));
    }

    @Test
    void conceptExplainAppendsRelatedTerms() {
        String glossary = handler.handle(query(QueryIntent.GLOSSARY, "what does heartbeat mean"), List.of(), "BOI");
        String explain = handler.handle(query(QueryIntent.CONCEPT_EXPLAIN, "explain heartbeat"), List.of(), "BOI");
        assertFalse(glossary.contains("Related terms"));
        assertTrue(explain.contains("Related terms"));
    }

    @Test
    void unknownTermGetsHonestDecline() {
        String answer = handler.handle(query(QueryIntent.GLOSSARY, "what does frobnicate mean?"), List.of(), "BOI");
        assertTrue(answer.contains("don't have a definition"));
    }
}
