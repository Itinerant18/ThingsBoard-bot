package com.seple.ThingsBoard_Bot.service.query;

import java.util.List;
import java.util.Locale;

/**
 * Capability boundary: questions whose data this bot never has. Rather than let them reach the LLM
 * (which would confabulate an answer), the resolver routes them to {@code OUT_OF_SCOPE} and
 * {@code OutOfScopeHandler} returns the matching "not tracked, here's why" message.
 *
 * <p>Each family's keywords are deliberately specific so they never steal an answerable question —
 * e.g. plain "TAT" (an active alarm's age, which <i>is</i> answerable) is not matched here; only
 * MTTR/MTTA/"time to resolve"/SLA-breach phrasings are.
 *
 * <p>ponytail: a static keyword→message table shared by the resolver (to route) and the handler (to
 * render). Add a family only when the question set actually contains it.
 */
public final class NotTracked {

    private NotTracked() {}

    private record Family(String message, List<String> keywords) {}

    private static final List<Family> FAMILIES = List.of(
            new Family(
                    "S-Vault storage isn't monitored by this bot. I track device health, CCTV recording "
                    + "days/compliance, alarms, power, and the branch hierarchy — but not S-Vault disk usage, "
                    + "retention, or streaming bandwidth.",
                    List.of("S-VAULT", "SVAULT", "S VAULT")),
            new Family(
                    "Platform/infrastructure self-metrics aren't tracked. I monitor the field devices, not "
                    + "this system itself — so server load, data-ingestion rate, API latency, pipeline backlog, "
                    + "maintenance windows, and failover events aren't available.",
                    List.of("SERVER LOAD", "INGESTION RATE", "DATA INGESTION", "API RESPONSE TIME",
                            "PIPELINE BACKLOG", "DATA PIPELINE", "MAINTENANCE WINDOW", "FAILOVER",
                            "MONITORING SERVER", "STREAMING BANDWIDTH", "NETWORK BANDWIDTH")),
            new Family(
                    "There's no FGMO/region roll-up level in the monitored hierarchy. It goes NBG → Zonal "
                    + "Office (ZO) → Branch, so I can answer by zone or by branch, but not by FGMO/region.",
                    List.of("FGMO")),
            new Family(
                    "Branch master-data (address, manager, contact details) isn't part of the monitoring "
                    + "data. I can report a branch's device health, alarms, and location coordinates, but not "
                    + "its postal address or staff contacts.",
                    List.of("BRANCH ADDRESS", "POSTAL ADDRESS", "ADDRESS OF THE BRANCH",
                            "ADDRESS OF BRANCH", "BRANCH MANAGER", "MANAGER OF THE BRANCH",
                            "CONTACT NUMBER", "CONTACT DETAIL", "PHONE NUMBER")),
            new Family(
                    "Resolve/acknowledge durations (MTTR/MTTA) and SLA-breach timing aren't computable — "
                    + "alarms carry only a created-time and their current status, with no acknowledge/clear "
                    + "timestamp. I can tell you an active alarm's age (time since it was raised) and its "
                    + "severity instead.",
                    List.of("MTTR", "MTTA", "MEAN TIME TO", "TIME TO RESOLVE", "TIME TO ACKNOWLEDGE",
                            "SLA BREACH", "BREACH THE SLA", "BREACHING SLA", "SLA THRESHOLD")));

    /** True when the question hits a not-tracked family. */
    public static boolean matches(String question) {
        return messageFor(question) != null;
    }

    /** The specific refusal message for the question, or {@code null} if none applies. */
    public static String messageFor(String question) {
        if (question == null) {
            return null;
        }
        String q = question.toUpperCase(Locale.ROOT);
        for (Family family : FAMILIES) {
            for (String kw : family.keywords()) {
                if (q.contains(kw)) {
                    return family.message();
                }
            }
        }
        return null;
    }
}
