package com.seple.ThingsBoard_Bot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the status-delta semantics shared by LuaScriptService and ReplayService, and which
 * scripts/update_counters.lua mirrors (audit #25).
 */
class StatusDeltaUtilTest {

    @Test
    void onlineOfflineTransitions() {
        assertEquals(1, StatusDeltaUtil.calculateDelta("gateway_status", "offline", "online"));
        assertEquals(-1, StatusDeltaUtil.calculateDelta("gateway_status", "online", "offline"));
        assertEquals(0, StatusDeltaUtil.calculateDelta("gateway_status", "online", "online"));
        assertEquals(0, StatusDeltaUtil.calculateDelta("gateway_status", "offline", "offline"));
    }

    @Test
    void nonStatusFieldNeverCounts() {
        assertEquals(0, StatusDeltaUtil.calculateDelta("battery_voltage", "12", "online"));
    }

    @Test
    void onlineStateSynonyms() {
        assertTrue(StatusDeltaUtil.isOnlineState("ONLINE"));
        assertTrue(StatusDeltaUtil.isOnlineState("on"));
        assertTrue(StatusDeltaUtil.isOnlineState("1"));
        assertTrue(StatusDeltaUtil.isOnlineState("true"));
        assertFalse(StatusDeltaUtil.isOnlineState("offline"));
        assertFalse(StatusDeltaUtil.isOnlineState(null));
    }

    @Test
    void statusFieldDetection() {
        assertTrue(StatusDeltaUtil.isStatusField("gateway_status"));
        assertTrue(StatusDeltaUtil.isStatusField("cctvStatus"));
        assertTrue(StatusDeltaUtil.isStatusField("online"));
        assertFalse(StatusDeltaUtil.isStatusField("battery_voltage"));
        assertFalse(StatusDeltaUtil.isStatusField(null));
    }
}
