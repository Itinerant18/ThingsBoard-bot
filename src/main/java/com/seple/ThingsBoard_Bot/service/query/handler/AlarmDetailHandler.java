package com.seple.ThingsBoard_Bot.service.query.handler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.ActiveAlarm;
import com.seple.ThingsBoard_Bot.model.domain.AlertSummary;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.AlarmFetchService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * ALARM_DETAIL — answers severity-level alarm questions using data fetched from the
 * ThingsBoard Alarm REST API.
 *
 * <p>Handles questions like:
 * <ul>
 *   <li>"How many critical alarms are there?"</li>
 *   <li>"Show all major alarms"</li>
 *   <li>"Which branches have critical alarms?"</li>
 *   <li>"Severity breakdown of all open alarms"</li>
 *   <li>"What types of alarms do we have?"</li>
 * </ul>
 *
 * <p>Alarm data is fetched on-demand via {@link AlarmFetchService} (with TTL cache),
 * not during the background 60-second refresh cycle.
 */
@Component
public class AlarmDetailHandler implements AnswerHandler {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter
            .ofPattern("dd-MMM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AlarmFetchService alarmFetchService;
    private final AnswerSupport support;

    public AlarmDetailHandler(AlarmFetchService alarmFetchService, AnswerSupport support) {
        this.alarmFetchService = alarmFetchService;
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.ALARM_DETAIL;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        String question = query.getOriginalQuestion() == null ? ""
                : query.getOriginalQuestion().toUpperCase(java.util.Locale.ROOT);

        // Determine scope: single branch or fleet-wide
        BranchSnapshot targetBranch = query.getTargetBranch();
        List<BranchSnapshot> scope = (targetBranch != null) ? List.of(targetBranch) : snapshots;

        if (scope.isEmpty()) {
            return "No branch devices found in your scope.";
        }

        // Fetch alarm detail (cached, ~100ms on first call)
        alarmFetchService.enrichWithAlarmDetail(scope);

        // Determine requested severity filter (if any)
        String severityFilter = extractSeverityFilter(question);

        if (isSeverityBreakdown(question)) {
            return renderSeverityBreakdown(scope, targetBranch);
        }
        if (isTypeBreakdown(question)) {
            return renderTypeBreakdown(scope, targetBranch);
        }
        if (isAgeQuestion(question)) {
            return renderOldest(scope, targetBranch);
        }
        if (isRecentQuestion(question)) {
            return renderRecent(scope, targetBranch, question);
        }
        if (isBranchListing(question)) {
            return renderBranchesWithSeverity(scope, severityFilter);
        }
        // Default: per-branch or fleet alarm list with severity
        return renderAlarmList(scope, severityFilter, targetBranch);
    }

    // -------------------------------------------------------------------------
    // Renderers
    // -------------------------------------------------------------------------

    private String renderSeverityBreakdown(List<BranchSnapshot> scope, BranchSnapshot singleBranch) {
        int critical = 0, major = 0, minor = 0, warning = 0, total = 0;
        for (BranchSnapshot snap : scope) {
            AlertSummary a = snap.getAlerts();
            if (a == null) continue;
            critical += a.getCriticalCount();
            major    += a.getMajorCount();
            minor    += a.getMinorCount();
            warning  += a.getWarningCount();
            total    += a.getAlarmCount();
        }

        String scopeLabel = singleBranch != null ? support.branchName(singleBranch) : "All Branches";
        StringBuilder sb = new StringBuilder();
        sb.append("### 🚨 Alarm Severity Breakdown — ").append(scopeLabel).append("\n\n");
        sb.append("| Severity | Count |\n|----------|-------|\n");
        sb.append("| 🔴 Critical | ").append(critical).append(" |\n");
        sb.append("| 🟠 Major    | ").append(major).append(" |\n");
        sb.append("| 🟡 Minor    | ").append(minor).append(" |\n");
        sb.append("| 🔵 Warning  | ").append(warning).append(" |\n");
        sb.append("| **Total**   | **").append(total).append("** |\n");

        if (critical == 0 && major == 0 && minor == 0 && warning == 0 && total > 0) {
            sb.append("\n_Note: Severity data not available from ThingsBoard for these alarms. Total count is from telemetry._");
        }
        return sb.toString();
    }

    private String renderTypeBreakdown(List<BranchSnapshot> scope, BranchSnapshot singleBranch) {
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (BranchSnapshot snap : scope) {
            AlertSummary a = snap.getAlerts();
            if (a == null || a.getTopAlarms() == null) continue;
            for (ActiveAlarm alarm : a.getTopAlarms()) {
                typeCounts.merge(alarm.type(), 1, Integer::sum);
            }
        }

        String scopeLabel = singleBranch != null ? support.branchName(singleBranch) : "Fleet";
        StringBuilder sb = new StringBuilder();
        sb.append("### 📋 Alarm Types — ").append(scopeLabel).append("\n\n");

        if (typeCounts.isEmpty()) {
            sb.append("No active alarms with type data found.");
            return sb.toString();
        }

        sb.append("| Alarm Type | Count |\n|------------|-------|\n");
        typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sb.append("| ").append(e.getKey())
                        .append(" | ").append(e.getValue()).append(" |\n"));
        return sb.toString();
    }

    private String renderBranchesWithSeverity(List<BranchSnapshot> scope, String severityFilter) {
        StringBuilder sb = new StringBuilder();
        String label = severityFilter != null ? severityFilter : "any";
        sb.append("### 🏢 Branches with ").append(label.toUpperCase()).append(" Alarms\n\n");

        List<String> found = new ArrayList<>();
        for (BranchSnapshot snap : scope) {
            AlertSummary a = snap.getAlerts();
            if (a == null) continue;
            boolean match = severityFilter == null
                    ? a.getAlarmCount() > 0
                    : matchesSeverity(a, severityFilter);
            if (match) {
                int count = severityCount(a, severityFilter);
                found.add("- **" + support.branchName(snap) + "** — "
                        + (severityFilter != null ? count : a.getAlarmCount()) + " alarm(s)");
            }
        }

        if (found.isEmpty()) {
            sb.append("✅ No branches found with ").append(label).append(" alarms.");
        } else {
            sb.append("**").append(found.size()).append(" branch(es):**\n\n");
            found.forEach(line -> sb.append(line).append("\n"));
        }
        return sb.toString();
    }

    /** Oldest / longest-active alarms — ranked by createdTime, each with its age (now - created). */
    private String renderOldest(List<BranchSnapshot> scope, BranchSnapshot singleBranch) {
        List<ActiveAlarm> alarms = flatten(scope).stream()
                .filter(a -> a.createdTime() > 0)
                .sorted(java.util.Comparator.comparingLong(ActiveAlarm::createdTime))
                .toList();
        String scopeLabel = singleBranch != null ? support.branchName(singleBranch) : "Fleet";
        if (alarms.isEmpty()) {
            return "✅ No active alarms with a timestamp found for " + scopeLabel + ".";
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("### ⏱️ Oldest Active Alarms (among fetched) — ")
                .append(scopeLabel).append("\n\n");
        sb.append("| # | Type | Severity | Branch | Age | Raised |\n");
        sb.append("|---|------|----------|--------|-----|--------|\n");
        int i = 1;
        for (ActiveAlarm a : alarms) {
            String branch = a.deviceName() != null ? a.deviceName() : a.deviceId();
            sb.append("| ").append(i++).append(" | ").append(a.type())
              .append(" | ").append(a.severityLabel()).append(" | ").append(branch)
              .append(" | ").append(age(now - a.createdTime()))
              .append(" | ").append(TS_FMT.format(Instant.ofEpochMilli(a.createdTime()))).append(" |\n");
            if (i > 10) break;
        }
        return sb.toString();
    }

    /** Alarms raised within a recent window (last hour, or last 24h). */
    private String renderRecent(List<BranchSnapshot> scope, BranchSnapshot singleBranch, String question) {
        boolean hourly = question.contains("HOUR");
        long windowMs = hourly ? 60L * 60 * 1000 : 24L * 60 * 60 * 1000;
        long cutoff = System.currentTimeMillis() - windowMs;
        String windowLabel = hourly ? "last hour" : "last 24 hours";

        List<ActiveAlarm> recent = flatten(scope).stream()
                .filter(a -> a.createdTime() >= cutoff)
                .sorted(java.util.Comparator.comparingLong(ActiveAlarm::createdTime).reversed())
                .toList();
        String scopeLabel = singleBranch != null ? support.branchName(singleBranch) : "Fleet";
        StringBuilder sb = new StringBuilder("### 🕒 Alarms in the ").append(windowLabel)
                .append(" — ").append(scopeLabel).append("\n\n");
        if (recent.isEmpty()) {
            sb.append("✅ No alarms were raised in the ").append(windowLabel).append(".");
            return sb.toString();
        }
        sb.append("**").append(recent.size()).append(" alarm(s):**\n\n");
        sb.append("| Type | Severity | Branch | Raised |\n|------|----------|--------|--------|\n");
        int i = 0;
        for (ActiveAlarm a : recent) {
            String branch = a.deviceName() != null ? a.deviceName() : a.deviceId();
            sb.append("| ").append(a.type()).append(" | ").append(a.severityLabel())
              .append(" | ").append(branch)
              .append(" | ").append(TS_FMT.format(Instant.ofEpochMilli(a.createdTime()))).append(" |\n");
            if (++i >= 20) break;
        }
        return sb.toString();
    }

    private List<ActiveAlarm> flatten(List<BranchSnapshot> scope) {
        List<ActiveAlarm> all = new ArrayList<>();
        for (BranchSnapshot snap : scope) {
            AlertSummary a = snap.getAlerts();
            if (a != null && a.getTopAlarms() != null) {
                all.addAll(a.getTopAlarms());
            }
        }
        return all;
    }

    /** Human-readable duration from millis, e.g. "3d 4h", "5h 12m", "8m". */
    private static String age(long ms) {
        if (ms < 0) ms = 0;
        long mins = ms / 60000;
        long days = mins / 1440;
        long hours = (mins % 1440) / 60;
        long m = mins % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + m + "m";
        return m + "m";
    }

    private String renderAlarmList(List<BranchSnapshot> scope, String severityFilter, BranchSnapshot singleBranch) {
        List<ActiveAlarm> alarms = new ArrayList<>();
        for (BranchSnapshot snap : scope) {
            AlertSummary a = snap.getAlerts();
            if (a == null || a.getTopAlarms() == null) continue;
            for (ActiveAlarm alarm : a.getTopAlarms()) {
                if (severityFilter == null || severityFilter.equalsIgnoreCase(alarm.severity())) {
                    alarms.add(alarm);
                }
            }
        }

        String scopeLabel = singleBranch != null ? support.branchName(singleBranch) : "Fleet";
        StringBuilder sb = new StringBuilder();
        String sevLabel = severityFilter != null ? " (" + severityFilter.toUpperCase() + ")" : "";
        sb.append("### 🚨 Active Alarms").append(sevLabel).append(" — ").append(scopeLabel).append("\n\n");

        if (alarms.isEmpty()) {
            // Fall back to telemetry-based count
            int telemetryTotal = scope.stream()
                    .mapToInt(s -> s.getAlerts() != null ? s.getAlerts().getAlarmCount() : 0)
                    .sum();
            if (telemetryTotal > 0) {
                sb.append("**").append(telemetryTotal).append(" alarm(s)** reported by telemetry,")
                  .append(" but detailed alarm data (type/severity) is not available from ThingsBoard API.\n");
            } else {
                sb.append("✅ No active alarms found.");
            }
            return sb.toString();
        }

        sb.append("| # | Type | Severity | Branch | Time |\n");
        sb.append("|---|------|----------|--------|------|\n");
        int i = 1;
        for (ActiveAlarm alarm : alarms) {
            String ts = alarm.createdTime() > 0
                    ? TS_FMT.format(Instant.ofEpochMilli(alarm.createdTime()))
                    : "N/A";
            // Find branch name from device name or fall back to deviceId
            String branchName = alarm.deviceName() != null ? alarm.deviceName() : alarm.deviceId();
            sb.append("| ").append(i++).append(" | ").append(alarm.type())
              .append(" | ").append(alarm.severityLabel())
              .append(" | ").append(branchName)
              .append(" | ").append(ts).append(" |\n");
            if (i > 20) {
                sb.append("\n_...and ").append(alarms.size() - 20).append(" more alarms._\n");
                break;
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractSeverityFilter(String question) {
        if (question.contains("CRITICAL")) return "CRITICAL";
        if (question.contains("MAJOR")) return "MAJOR";
        if (question.contains("MINOR")) return "MINOR";
        if (question.contains("WARNING")) return "WARNING";
        return null;
    }

    private boolean isSeverityBreakdown(String question) {
        return question.contains("SEVERITY") || question.contains("BREAKDOWN")
                || question.contains("DISTRIBUTION") || question.contains("MIX");
    }

    private boolean isTypeBreakdown(String question) {
        return (question.contains("TYPE") || question.contains("WHAT KIND") || question.contains("WHAT TYPE"))
                && question.contains("ALARM");
    }

    private boolean isBranchListing(String question) {
        return question.contains("WHICH BRANCH") || question.contains("WHAT BRANCH")
                || question.contains("BRANCHES WITH") || question.contains("BRANCHES HAVE");
    }

    private boolean isAgeQuestion(String question) {
        return question.contains("OLDEST") || question.contains("LONGEST")
                || question.contains("HOW LONG");
    }

    private boolean isRecentQuestion(String question) {
        return question.contains("LAST HOUR") || question.contains("PAST HOUR")
                || question.contains("LAST 24") || question.contains("PAST 24")
                || question.contains("TRIGGERED IN") || question.contains("WERE TRIGGERED");
    }

    private boolean matchesSeverity(AlertSummary a, String severityFilter) {
        return switch (severityFilter.toUpperCase()) {
            case "CRITICAL" -> a.getCriticalCount() > 0;
            case "MAJOR"    -> a.getMajorCount() > 0;
            case "MINOR"    -> a.getMinorCount() > 0;
            case "WARNING"  -> a.getWarningCount() > 0;
            default -> a.getAlarmCount() > 0;
        };
    }

    private int severityCount(AlertSummary a, String severityFilter) {
        if (severityFilter == null) return a.getAlarmCount();
        return switch (severityFilter.toUpperCase()) {
            case "CRITICAL" -> a.getCriticalCount();
            case "MAJOR"    -> a.getMajorCount();
            case "MINOR"    -> a.getMinorCount();
            case "WARNING"  -> a.getWarningCount();
            default -> a.getAlarmCount();
        };
    }
}
