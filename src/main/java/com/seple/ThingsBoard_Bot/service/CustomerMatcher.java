package com.seple.ThingsBoard_Bot.service;

import java.util.List;
import java.util.Map;

/**
 * Maps a ThingsBoard customer title to an internal customer prefix (audit finding #26).
 *
 * <p>Deliberately conservative: an explicit configured mapping wins, otherwise the title must match
 * exactly ONE internal prefix (normalized). Zero or multiple candidates return {@code null} — the
 * caller skips rather than guesses. This avoids the bidirectional-substring matching that could
 * silently map one bank to another (a persisted cross-tenant exposure).
 */
public final class CustomerMatcher {

    private CustomerMatcher() {
    }

    /** Uppercase, strip non-alphanumerics, for tolerant-but-exact comparison. */
    public static String normalize(String value) {
        return value == null ? "" : value.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /**
     * @param tbTitle               the ThingsBoard customer title
     * @param dbCustomerIds         internal customer prefixes that exist in the hierarchy
     * @param explicitNormalizedMap normalized-title -> prefix overrides (highest priority)
     * @return the matched internal prefix, or {@code null} if there is no confident, unique match
     */
    public static String match(String tbTitle, List<String> dbCustomerIds,
                               Map<String, String> explicitNormalizedMap) {
        String norm = normalize(tbTitle);
        if (norm.isEmpty() || dbCustomerIds == null) {
            return null;
        }

        // 1. Explicit override — only honoured if the target prefix actually exists in the hierarchy.
        if (explicitNormalizedMap != null) {
            String explicit = explicitNormalizedMap.get(norm);
            if (explicit != null) {
                for (String dbId : dbCustomerIds) {
                    if (normalize(dbId).equals(normalize(explicit))) {
                        return dbId;
                    }
                }
                return null;
            }
        }

        // 2. Exact normalized equality, requiring a UNIQUE match. No substring fallback.
        String found = null;
        int matches = 0;
        for (String dbId : dbCustomerIds) {
            if (normalize(dbId).equals(norm)) {
                found = dbId;
                matches++;
            }
        }
        return matches == 1 ? found : null;
    }
}
