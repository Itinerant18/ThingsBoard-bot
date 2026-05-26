package com.seple.ThingsBoard_Bot.service.normalization;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;

@Component
public class ValueNormalizer {

    public NormalizedState toState(String rawValue) {
        if (isNullOrCorrupt(rawValue)) {
            return NormalizedState.UNKNOWN;
        }

        String value = rawValue.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "online", "on", "healthy", "active", "true", "1", "yes", "clear", "normal" -> NormalizedState.ONLINE;
            case "offline", "off", "inactive", "false", "0", "disconnected" -> NormalizedState.OFFLINE;
            case "fault", "alarm", "error", "tamper", "triggered", "critical" -> NormalizedState.FAULT;
            case "n/a", "na", "not installed", "-" -> NormalizedState.NOT_INSTALLED;
            default -> NormalizedState.UNKNOWN;
        };
    }

    public Boolean toBoolean(String rawValue) {
        if (isNullOrCorrupt(rawValue)) {
            return null;
        }

        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "1", "yes", "on", "healthy", "online" -> true;
            case "false", "0", "no", "off", "offline", "fault", "inactive" -> false;
            default -> null;
        };
    }

    public Double toDouble(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String text = String.valueOf(rawValue).trim();
        if (isNullOrCorrupt(text) || "N/A".equalsIgnoreCase(text) || "na".equalsIgnoreCase(text) || "-".equals(text)) {
            return null;
        }

        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public Integer toInt(Object rawValue, int fallback) {
        if (rawValue == null) {
            return fallback;
        }
        String text = String.valueOf(rawValue).trim();
        if (isNullOrCorrupt(text) || "N/A".equalsIgnoreCase(text) || "na".equalsIgnoreCase(text) || "-".equals(text)) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean isNullOrCorrupt(String text) {
        if (text == null) {
            return true;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        return value.isBlank() 
                || "null".equals(value) 
                || value.startsWith("null") 
                || "not_found".equals(value) 
                || "not found".equals(value);
    }
}
