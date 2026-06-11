package com.seple.ThingsBoard_Bot.util;

/**
 * Single source of truth for online/offline status-counter delta logic on the Java side
 * (audit finding #25) — previously duplicated in LuaScriptService and ReplayService.
 *
 * <p>MUST stay in sync with {@code scripts/update_counters.lua}, which implements the same rules
 * inside Redis. Any change here should be mirrored there.
 */
public final class StatusDeltaUtil {

    private StatusDeltaUtil() {
    }

    public static boolean isStatusField(String field) {
        return field != null && (field.contains("status") || field.contains("Status")
                || field.equals("gateway_status") || field.equals("online"));
    }

    public static boolean isOnlineState(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.equals("online") || lower.equals("on") || lower.equals("1") || lower.equals("true");
    }

    /** +1 on an offline→online transition of a status field, -1 on online→offline, else 0. */
    public static long calculateDelta(String field, String prevValue, String newValue) {
        if (isStatusField(field)) {
            if (isOnlineState(newValue) && !isOnlineState(prevValue)) {
                return 1;
            }
            if (!isOnlineState(newValue) && isOnlineState(prevValue)) {
                return -1;
            }
        }
        return 0;
    }
}
