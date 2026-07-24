package com.seple.ThingsBoard_Bot.model.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertSummary {
    // --- Existing fields (telemetry-derived, always present) ---
    private int alarmCount;
    private int errorCount;
    private boolean nvrOff;
    private boolean hddError;
    private boolean cameraDisconnect;
    private boolean cameraTamper;
    private boolean intrusionActivate;
    private boolean fireActivate;
    private boolean powerOff;
    private boolean batteryLow;

    // --- Severity-level counts (from ThingsBoard Alarm API, populated on demand) ---
    @Builder.Default
    private int criticalCount = 0;
    @Builder.Default
    private int majorCount = 0;
    @Builder.Default
    private int minorCount = 0;
    @Builder.Default
    private int warningCount = 0;

    /** Up to 10 active alarms with full detail (type, severity, timestamp). Null when not yet fetched. */
    private List<ActiveAlarm> topAlarms;

    /** True when alarm detail has been loaded from the ThingsBoard Alarm API. */
    @Builder.Default
    private boolean alarmDetailLoaded = false;
}
