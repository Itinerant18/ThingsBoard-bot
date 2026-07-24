package com.seple.ThingsBoard_Bot.model.domain;

/**
 * A single active alarm fetched from the ThingsBoard Alarm REST API.
 *
 * <p>Severity values as returned by ThingsBoard: CRITICAL, MAJOR, MINOR, WARNING, INDETERMINATE.
 */
public record ActiveAlarm(
        String alarmId,
        String type,
        String severity,
        String status,
        long createdTime,
        String deviceName,
        String deviceId
) {

    /** True when this alarm is still open (not cleared or acknowledged-and-cleared). */
    public boolean isOpen() {
        return status != null && (status.equalsIgnoreCase("ACTIVE_UNACK")
                || status.equalsIgnoreCase("ACTIVE_ACK"));
    }

    /** Human-readable severity label with emoji for chat output. */
    public String severityLabel() {
        if (severity == null) return "UNKNOWN";
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "🔴 CRITICAL";
            case "MAJOR"    -> "🟠 MAJOR";
            case "MINOR"    -> "🟡 MINOR";
            case "WARNING"  -> "🔵 WARNING";
            default         -> severity.toUpperCase();
        };
    }
}
