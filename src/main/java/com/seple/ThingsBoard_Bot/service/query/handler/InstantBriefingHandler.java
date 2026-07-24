package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * INSTANT_BRIEFING — answers "what should I look at first?" and "give me a one-line summary".
 * Produces a concise priority-ranked risk assessment of the fleet.
 *
 * <p>Risk scoring: each branch gets a score based on:
 * <ul>
 *   <li>+10 if gateway offline</li>
 *   <li>+5 per alarm count</li>
 *   <li>+8 if power off (mains off + battery low)</li>
 *   <li>+3 if mains off (but battery OK)</li>
 *   <li>+2 per faulty subsystem</li>
 * </ul>
 * Branches with score > 0 are sorted descending and the top 10 are shown.
 */
@Component
public class InstantBriefingHandler implements AnswerHandler {

    private final AnswerSupport support;

    public InstantBriefingHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.INSTANT_BRIEFING;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "No branch devices found in your scope.";
        }

        String question = query.getOriginalQuestion() == null ? ""
                : query.getOriginalQuestion().toUpperCase(java.util.Locale.ROOT);
        boolean wantsOneLiner = question.contains("ONE LINE") || question.contains("ONE-LINE")
                || question.contains("BRIEF") || question.contains("SUMMARY LINE")
                || question.contains("QUICK STATUS");

        // Compute fleet-wide counts
        int total = snapshots.size();
        int online = 0, offline = 0, withAlarms = 0, mainsOff = 0, batteryLow = 0;
        for (BranchSnapshot snap : snapshots) {
            if (snap.getGateway() != null && snap.getGateway().getState() == NormalizedState.ONLINE) {
                online++;
            } else {
                offline++;
            }
            if (snap.getAlerts() != null && snap.getAlerts().getAlarmCount() > 0) withAlarms++;
            if (snap.getPower() != null) {
                if (Boolean.FALSE.equals(snap.getPower().getMainsOn())) mainsOff++;
                if (Boolean.TRUE.equals(snap.getPower().getBatteryLow())) batteryLow++;
            }
        }

        if (wantsOneLiner) {
            return String.format("**Fleet Status:** %d/%d branches online, %d with alarms, %d on battery, %d battery-low.",
                    online, total, withAlarms, mainsOff, batteryLow);
        }

        // Priority triage mode
        List<RankedBranch> ranked = new ArrayList<>();
        for (BranchSnapshot snap : snapshots) {
            int score = computeRiskScore(snap);
            if (score > 0) {
                ranked.add(new RankedBranch(support.branchName(snap), score, buildRiskReasons(snap)));
            }
        }
        ranked.sort(Comparator.comparingInt(RankedBranch::score).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("### 🚨 Priority Triage — Top Issues to Address\n\n");
        sb.append("**Fleet Overview:** ").append(online).append("/").append(total)
                .append(" online, ").append(withAlarms).append(" with alarms, ")
                .append(mainsOff).append(" on battery, ").append(batteryLow).append(" battery-low.\n\n");

        if (ranked.isEmpty()) {
            sb.append("✅ **No critical issues detected.** All branches appear healthy.");
            return sb.toString();
        }

        int show = Math.min(ranked.size(), 10);
        sb.append("| # | Branch | Risk Score | Issues |\n");
        sb.append("|---|--------|-----------|--------|\n");
        for (int i = 0; i < show; i++) {
            RankedBranch r = ranked.get(i);
            sb.append("| ").append(i + 1).append(" | ").append(r.name())
                    .append(" | ").append(r.score())
                    .append(" | ").append(String.join(", ", r.reasons())).append(" |\n");
        }
        if (ranked.size() > show) {
            sb.append("\n_...and ").append(ranked.size() - show).append(" more branches with issues._\n");
        }
        return sb.toString();
    }

    private int computeRiskScore(BranchSnapshot snap) {
        int score = 0;
        // Gateway offline
        if (snap.getGateway() != null && snap.getGateway().getState() != NormalizedState.ONLINE) {
            score += 10;
        }
        // Alarms
        if (snap.getAlerts() != null) {
            score += snap.getAlerts().getAlarmCount() * 5;
        }
        // Power issues
        if (snap.getPower() != null) {
            boolean mainsOff = Boolean.FALSE.equals(snap.getPower().getMainsOn());
            boolean battLow = Boolean.TRUE.equals(snap.getPower().getBatteryLow());
            if (mainsOff && battLow) {
                score += 8; // critical: no mains + battery dying
            } else if (mainsOff) {
                score += 3; // warning: on battery
            } else if (battLow) {
                score += 4; // low battery but mains on
            }
        }
        // Faulty subsystems
        if (snap.getSubsystems() != null) {
            Map<String, com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus> systems = Map.of(
                    "CCTV", orEmpty(snap.getSubsystems().getCctv()),
                    "IAS", orEmpty(snap.getSubsystems().getIas()),
                    "BAS", orEmpty(snap.getSubsystems().getBas()),
                    "FAS", orEmpty(snap.getSubsystems().getFas()),
                    "TLS", orEmpty(snap.getSubsystems().getTimeLock()),
                    "ACS", orEmpty(snap.getSubsystems().getAccessControl()));
            for (var entry : systems.entrySet()) {
                if (entry.getValue().isInstalled()
                        && (entry.getValue().getState() == NormalizedState.FAULT
                                || entry.getValue().getState() == NormalizedState.OFFLINE)) {
                    score += 2;
                }
            }
        }
        return score;
    }

    private List<String> buildRiskReasons(BranchSnapshot snap) {
        List<String> reasons = new ArrayList<>();
        if (snap.getGateway() != null && snap.getGateway().getState() != NormalizedState.ONLINE) {
            reasons.add("🔴 Offline");
        }
        if (snap.getAlerts() != null && snap.getAlerts().getAlarmCount() > 0) {
            reasons.add("⚠️ " + snap.getAlerts().getAlarmCount() + " alarm(s)");
        }
        if (snap.getPower() != null) {
            if (Boolean.FALSE.equals(snap.getPower().getMainsOn())) reasons.add("⚡ Mains Off");
            if (Boolean.TRUE.equals(snap.getPower().getBatteryLow())) reasons.add("🪫 Battery Low");
        }
        List<String> faultySystems = support.systemsByState(snap, NormalizedState.FAULT);
        if (!faultySystems.isEmpty()) {
            reasons.add("🔧 Faulty: " + String.join(", ", faultySystems));
        }
        return reasons;
    }

    private static com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus orEmpty(
            com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus status) {
        return status != null ? status
                : com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus.builder()
                        .installed(false).state(NormalizedState.UNKNOWN).build();
    }

    private record RankedBranch(String name, int score, List<String> reasons) {
    }
}
