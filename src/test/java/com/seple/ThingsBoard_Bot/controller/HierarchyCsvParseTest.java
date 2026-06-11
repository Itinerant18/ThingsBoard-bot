package com.seple.ThingsBoard_Bot.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class HierarchyCsvParseTest {

    @Test
    void parsesPlainRow() {
        List<String> f = HierarchyAdminController.parseCsvLine("BR1,BOI,ZO1,branch,3,Liluah,true");
        assertEquals(List.of("BR1", "BOI", "ZO1", "branch", "3", "Liluah", "true"), f);
    }

    @Test
    void honoursQuotedCommas() {
        List<String> f = HierarchyAdminController.parseCsvLine("BR1,BOI,ZO1,branch,3,\"Liluah, Howrah\",true");
        assertEquals(7, f.size());
        assertEquals("Liluah, Howrah", f.get(5));
    }

    @Test
    void honoursEscapedQuotes() {
        List<String> f = HierarchyAdminController.parseCsvLine("BR1,BOI,ZO1,branch,3,\"The \"\"Main\"\" Branch\",true");
        assertEquals("The \"Main\" Branch", f.get(5));
    }
}
