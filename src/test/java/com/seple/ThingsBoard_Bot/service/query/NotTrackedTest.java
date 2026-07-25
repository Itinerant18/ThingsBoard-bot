package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Capability-boundary matcher: refuses only genuinely-absent data, never answerable questions. */
class NotTrackedTest {

    @Test
    void refusesAbsentDataFamilies() {
        assertTrue(NotTracked.matches("what is the current storage status of S-Vault"));
        assertTrue(NotTracked.matches("what is the current load on the central monitoring server"));
        assertTrue(NotTracked.matches("which FGMO region has the most branches"));
        assertTrue(NotTracked.matches("what is the current MTTR for active alarms"));
        assertTrue(NotTracked.matches("what is the branch address of LILUAH"));
    }

    @Test
    void doesNotStealAnswerableQuestions() {
        // Active-alarm age (TAT since raised) IS answerable — must not be refused.
        assertNull(NotTracked.messageFor("what is the current TAT for the active alarm at BALLYBAZAR"));
        // IP address of a device is answerable via CCTV device info — must not be refused.
        assertNull(NotTracked.messageFor("what is the current IP address and status of device X"));
        assertNull(NotTracked.messageFor("how many cameras are online"));
        assertNull(NotTracked.messageFor("what is the disk utilization of the gateway"));
    }

    @Test
    void messageNamesTheReason() {
        String msg = NotTracked.messageFor("current disk utilization across all S-Vault nodes");
        assertNotNull(msg);
        assertTrue(msg.contains("S-Vault"), msg);
    }
}
