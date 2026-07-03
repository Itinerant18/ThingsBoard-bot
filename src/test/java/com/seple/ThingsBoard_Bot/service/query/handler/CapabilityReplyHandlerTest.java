package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

class CapabilityReplyHandlerTest {

    private final CapabilityReplyHandler handler = new CapabilityReplyHandler();

    private String handle(QueryIntent intent) {
        return handler.handle(ResolvedQuery.builder().intent(intent).originalQuestion("q").build(),
                List.of(), "BOI");
    }

    @Test
    void supportsExactlyTheCapabilityIntents() {
        assertTrue(handler.supports(QueryIntent.HOW_TO));
        assertTrue(handler.supports(QueryIntent.NAVIGATION));
        assertTrue(handler.supports(QueryIntent.TROUBLESHOOTING));
        assertFalse(handler.supports(QueryIntent.GLOSSARY));
        assertFalse(handler.supports(QueryIntent.BATTERY_VOLTAGE));
        assertFalse(handler.supports(QueryIntent.OUT_OF_SCOPE));
    }

    @Test
    void howToStatesReadOnlyRoleWithExamples() {
        String reply = handle(QueryIntent.HOW_TO);
        assertTrue(reply.contains("read-only"));
        assertTrue(reply.contains("administrator"));
        assertTrue(reply.contains("battery voltage"));
    }

    @Test
    void navigationSaysAskDirectly() {
        String reply = handle(QueryIntent.NAVIGATION);
        assertTrue(reply.contains("I'm the interface"));
        assertTrue(reply.contains("offline"));
    }

    @Test
    void troubleshootingOffersFaultData() {
        String reply = handle(QueryIntent.TROUBLESHOOTING);
        assertTrue(reply.contains("can't perform repairs"));
        assertTrue(reply.contains("fault"));
    }
}
