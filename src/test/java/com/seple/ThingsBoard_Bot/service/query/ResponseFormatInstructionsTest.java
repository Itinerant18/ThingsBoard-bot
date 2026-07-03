package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ResponseFormatInstructionsTest {

    @Test
    void nullFormatMeansNoInstruction() {
        assertEquals("", ResponseFormatInstructions.forFormat(null));
    }

    @Test
    void everyFormatHasADistinctNonBlankInstruction() {
        Set<String> seen = new HashSet<>();
        for (ResponseFormat format : ResponseFormat.values()) {
            String instruction = ResponseFormatInstructions.forFormat(format);
            assertFalse(instruction.isBlank(), format + " must produce an instruction");
            assertTrue(instruction.startsWith("\nFORMAT:"), format + " must be a FORMAT line");
            assertTrue(seen.add(instruction), format + " instruction must be distinct");
        }
    }

    @Test
    void tableAsksForMarkdownTable() {
        assertTrue(ResponseFormatInstructions.forFormat(ResponseFormat.TABLE).contains("markdown table"));
    }
}
