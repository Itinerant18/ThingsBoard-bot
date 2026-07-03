package com.seple.ThingsBoard_Bot.service.query.glossary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService.GlossaryEntry;

class GlossaryServiceTest {

    private final GlossaryService service = new GlossaryService(new ClassPathResource("glossary.json"));

    @Test
    void looksUpTermCaseInsensitively() {
        GlossaryEntry ias = service.lookup("ias");
        assertNotNull(ias);
        assertEquals("IAS", ias.term());
        assertEquals("Intrusion Alarm System", ias.fullName());
        assertFalse(ias.definition().isBlank());
    }

    @Test
    void looksUpByAliasAndFullName() {
        assertEquals("IAS", service.lookup("intrusion alarm system").term());
        assertEquals("TLS", service.lookup("time lock").term());
        assertEquals("NVR", service.lookup("Network Video Recorder").term());
    }

    @Test
    void unknownTermReturnsNull() {
        assertNull(service.lookup("flux capacitor"));
        assertNull(service.lookup(null));
        assertNull(service.lookup(""));
    }

    @Test
    void findsTermInQuestion() {
        assertEquals("stale", service.findTermInQuestion("What does stale mean?").term());
        assertEquals("IAS", service.findTermInQuestion("explain IAS to me").term());
        assertEquals("HDD error", service.findTermInQuestion("what is an HDD error?").term());
    }

    @Test
    void longestTermWinsAndWordBoundariesHold() {
        // "time lock system" must beat the shorter alias "time lock".
        assertEquals("TLS", service.findTermInQuestion("define time lock system").term());
        // "stale" must not fire inside "installed".
        assertNull(service.findTermInQuestion("when was the camera installed"));
    }

    @Test
    void containsKnownTermMatchesFindResult() {
        assertTrue(service.containsKnownTerm("meaning of uptime"));
        assertFalse(service.containsKnownTerm("meaning of life"));
    }
}
