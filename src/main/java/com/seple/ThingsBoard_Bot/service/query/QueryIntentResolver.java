package com.seple.ThingsBoard_Bot.service.query;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;

@Component
public class QueryIntentResolver {

    private final BranchAliasIndex branchAliasIndex;

    public QueryIntentResolver(BranchAliasIndex branchAliasIndex) {
        this.branchAliasIndex = branchAliasIndex;
    }

    public ResolvedQuery resolve(String question, List<BranchSnapshot> snapshots, String activeBranchAlias) {
        String normalizedQuestion = branchAliasIndex.normalize(question);
        String compactQuestion = branchAliasIndex.compact(question);
        Map<String, BranchSnapshot> aliasIndex = branchAliasIndex.build(snapshots);

        // Find if branch is in the question
        BranchSnapshot targetBranch = findBranchInQuestion(normalizedQuestion, compactQuestion, aliasIndex);
        boolean branchFromMemory = false;

        // NOTE: We deliberately do NOT fall back to the last active branch from memory. Silently
        // reusing the previous branch for an unknown/ambiguous branch name produced wrong-branch and
        // cross-branch answers. When the question names no resolvable branch and is not global, the
        // query is flagged ambiguous below so the caller asks the user to clarify which branch.

        QueryIntent intent = detectIntent(normalizedQuestion, targetBranch != null);
        boolean global = isGlobalQuestion(normalizedQuestion, targetBranch != null);

        // Option A: metric questions must be branch-specific.
        // If user asks a metric without a branch (including "all branch ..."),
        // force clarification instead of falling back to global overview.
        if (targetBranch == null && isBranchMetricIntent(intent)) {
            global = false;
        }

        // AMBIGUITY DETECTION: If no branch found anywhere, NOT explicitly global, and NOT a general conversation
        boolean ambiguous = targetBranch == null && !global && intent != QueryIntent.GENERAL_LLM;

        boolean deterministic = intent != QueryIntent.GENERAL_LLM;
        double confidence = targetBranch != null || global || ambiguous ? 0.95 : 0.55;

        return ResolvedQuery.builder()
                .intent(intent)
                .originalQuestion(question)
                .targetBranch(targetBranch)
                .targetSystem(detectSubsystem(normalizedQuestion))
                .targetAttribute(detectSubsystemAttribute(normalizedQuestion))
                .global(global)
                .ambiguous(ambiguous)
                .branchFromMemory(branchFromMemory)
                .deterministic(deterministic && (global || targetBranch != null))
                .confidence(confidence)
                .build();
    }

    private BranchSnapshot findBranchInQuestion(String normalizedQuestion, String compactQuestion, Map<String, BranchSnapshot> aliasIndex) {
        List<String> aliases = aliasIndex.keySet().stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();

        for (String alias : aliases) {
            if (!alias.isBlank() && !isWeakAlias(alias) && matchesExplicitAlias(normalizedQuestion, compactQuestion, alias)) {
                return aliasIndex.get(alias);
            }
        }
        return null;
    }

    private boolean matchesExplicitAlias(String normalizedQuestion, String compactQuestion, String alias) {
        String normalizedAlias = branchAliasIndex.normalize(alias);
        if (normalizedAlias.isBlank()) {
            return false;
        }

        if (normalizedAlias.contains(" ")) {
            return Pattern.compile("(^|\\s)" + Pattern.quote(normalizedAlias) + "($|\\s)").matcher(normalizedQuestion)
                    .find();
        }

        if (normalizedAlias.length() >= 4
                && Pattern.compile("(^|\\s)" + Pattern.quote(normalizedAlias) + "($|\\s)").matcher(normalizedQuestion)
                        .find()) {
            return true;
        }

        String compactAlias = branchAliasIndex.compact(alias);
        return compactAlias.length() >= 6 && compactQuestion.contains(compactAlias);
    }

    private boolean isWeakAlias(String alias) {
        String normalizedAlias = branchAliasIndex.normalize(alias);
        return normalizedAlias.length() < 4;
    }

    private QueryIntent detectIntent(String normalizedQuestion, boolean hasTargetBranch) {
        String question = normalizedQuestion.toUpperCase(Locale.ROOT);
        if (question.contains("IMEI")) {
            return QueryIntent.DEVICE_IMEI;
        }
        if (isBatteryLowQuestion(question)) {
            return QueryIntent.BATTERY_LOW_STATUS;
        }
        if (question.contains("ACCESS CONTROL") && question.contains("USER")) {
            return QueryIntent.ACCESS_CONTROL_USER_COUNT;
        }
        if (question.contains("ACCESS CONTROL") && question.contains("DEVICE")
                && (question.contains("INFO") || question.contains("DETAIL"))) {
            return QueryIntent.ACCESS_CONTROL_DEVICE_INFO;
        }
        // CCTV inventory/specification (NVR/DVR vendor+model, HDD slot count, storage capacity,
        // resolution). "model"/"make" are CCTV-recorder attributes in this fleet. The remaining
        // attributes are gated behind a CCTV/NVR/DVR/camera context so words like "capacity" or
        // "resolution" don't misfire. Placed after access-control so that intent wins. NOTE: "SLOT"
        // (not bare "HDD") triggers this, so "cctv hdd info"/"cctv hdd error" still reach their own
        // intents below.
        boolean cctvContext = question.contains("NVR") || question.contains("DVR")
                || question.contains("CCTV") || question.contains("CAMERA");
        if (question.contains("MODEL") || question.contains("MAKE")
                || (cctvContext && (question.contains("SPEC") || question.contains("INVENTORY")
                        || question.contains("VENDOR") || question.contains("BRAND")
                        || question.contains("RESOLUTION") || question.contains("STORAGE")
                        || question.contains("CAPACITY") || question.contains("SLOT")))) {
            return QueryIntent.CCTV_DEVICE_INFO;
        }
        if (question.contains("DOOR")) {
            return QueryIntent.DOOR_STATUS;
        }
        if ((question.contains("HISTORY") || question.contains("HISTORICAL")) && (question.contains("DISCONNECT") || question.contains("DISCONNECTS")) && question.contains("CAMERA")) {
            return QueryIntent.CAMERA_DISCONNECT_HISTORY;
        }
        if ((question.contains("DISCONNECT") || question.contains("DISCONNECTS") || question.contains("DISCONNECTED"))
                && (question.contains("CAMERA") || question.contains("CCTV"))) {
            return QueryIntent.CAMERA_DISCONNECT_HISTORY;
        }
        if (question.startsWith("WHY") || (question.contains("WHY") && question.contains("FAULT"))) {
            return QueryIntent.FAULT_REASON;
        }
        if (question.contains("FAULT DEVICE") || (question.contains("FAULTY") && question.contains("DEVICE"))) {
            return QueryIntent.FAULT_DEVICES;
        }
        if (question.contains("OFFLINE DEVICE") || (question.contains("OFFLINE") && question.contains("DEVICE"))) {
            return QueryIntent.OFFLINE_DEVICES;
        }
        if (question.contains("CONNECTED DEVICE") || (question.contains("ALL DEVICES") && question.contains("CONNECTED"))) {
            return QueryIntent.CONNECTED_DEVICES;
        }
        if ((question.contains("ACTIVE DEVICE") || (question.contains("ACTIVE") && question.contains("DEVICE")))
                && !question.contains("INACTIVE")) {
            return QueryIntent.ACTIVE_DEVICES;
        }
        if (question.contains("NETWORK")) {
            return QueryIntent.NETWORK_STATUS;
        }
        if ((question.contains("CCTV") || question.contains("CAMERA")) && question.contains("HDD")
                && (question.contains("ERROR") || question.contains("FAULT"))) {
            return QueryIntent.CCTV_HDD_ERROR_STATUS;
        }
        if (isSubsystemFaultQuestion(question)) {
            return QueryIntent.SUBSYSTEM_FAULT_STATUS;
        }
        if (isSubsystemAlarmQuestion(question)) {
            return QueryIntent.SUBSYSTEM_ALARM_STATUS;
        }
        // A sub-device named together with a specific field (power/system/log/health status) is a
        // field-level subsystem query — answer it from that field's value (e.g. cctv_powerStatus),
        // not from the gateway-level CCTV/IAS roll-up. Must precede the CCTV branch below.
        if (detectSubsystemAttribute(question) != null && containsSubsystemKeyword(question)) {
            return QueryIntent.SUBSYSTEM_STATUS;
        }
        if ((question.contains("CCTV") || question.contains("CAMERA")) && question.contains("HDD")
                && (question.contains("INFO") || question.contains("DETAIL"))) {
            return QueryIntent.CCTV_HDD_INFO;
        }
        if ((question.contains("CCTV") || question.contains("CAMERA")) && question.contains("RECORD")) {
            return QueryIntent.CCTV_RECORDING_INFO;
        }
        if (question.contains("HOW MANY") && question.contains("CAMERA")) {
            return QueryIntent.CCTV_STATUS;
        }
        if (question.contains("BATTERY") && question.contains("VOLT")) {
            return QueryIntent.BATTERY_VOLTAGE;
        }
        // "AC" must be a whole word — contains("AC") mis-fires on FACADE, HVAC, etc. (audit #20).
        if (containsWord(question, "AC") && question.contains("VOLT")) {
            return QueryIntent.AC_VOLTAGE;
        }
        if (question.contains("SYSTEM CURRENT")) {
            return QueryIntent.SYSTEM_CURRENT;
        }
        if (question.contains("BATTERY")
                && (question.contains("HEALTH") || question.contains("HEALTHY") || question.contains("CONDITION") || question.contains("STATUS"))
                && !question.contains("VOLT") && !question.contains("LOW")) {
            return QueryIntent.BATTERY_HEALTH;
        }
        if (question.contains("CAMERA") || question.contains("CCTV")) {
            return QueryIntent.CCTV_STATUS;
        }
        if (question.contains("ERROR")) {
            return QueryIntent.ERROR_STATUS;
        }
        if (question.contains("ALARM")) {
            return QueryIntent.ALARM_STATUS;
        }
        if (containsSubsystemKeyword(question)) {
            return QueryIntent.SUBSYSTEM_STATUS;
        }
        if (question.contains("POWER") && (question.contains("STATUS") || question.contains("ON") || question.contains("OFF"))
                && !question.contains("BATTERY") && !question.contains("AC") && !question.contains("VOLT")) {
            return QueryIntent.POWER_STATUS;
        }
        // Keep branch/device global-status prompts global (e.g. "status of all devices in all branches"),
        // but metric prompts are already handled above and Option A forces clarification.
        if (isGlobalQuestion(question, hasTargetBranch)) {
            return QueryIntent.GLOBAL_OVERVIEW;
        }
        if ((question.contains("GATEWAY") || question.contains("STATUS") || question.contains("WORKING PROPERLY")
                || question.contains("ONLINE") || question.contains("OFFLINE"))
                && !question.contains("POWER") && !question.contains("UPS") && !question.contains("DOOR")) {
            return QueryIntent.GATEWAY_STATUS;
        }
        return QueryIntent.GENERAL_LLM;
    }

    /**
     * Whole-word containment, e.g. {@code containsWord("FACADE VOLT","AC")} is false but
     * {@code containsWord("AC VOLT","AC")} is true. NOTE: intent detection is still first-match-wins,
     * so a multi-metric question ("battery AND ac voltage") resolves to the first match only; full
     * multi-intent handling is a deliberate future enhancement.
     */
    static boolean containsWord(String haystack, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }

    private boolean isBatteryLowQuestion(String question) {
        return question.contains("BATTERY LOW")
                || question.contains("LOW BATTERY")
                || (question.contains("BATTERY") && question.contains("LOW") && question.contains("WARNING"));
    }

    private boolean isSubsystemFaultQuestion(String question) {
        if (!question.contains("FAULT") && !question.contains("ERROR")) {
            return false;
        }
        return containsSubsystemKeyword(question)
                || question.contains("FIRE ALARM SYSTEM")
                || question.contains("INTRUSION ALARM SYSTEM")
                || question.contains("ACCESS CONTROL SYSTEM")
                || question.contains("TIME LOCK");
    }

    private boolean isSubsystemAlarmQuestion(String question) {
        if (!question.contains("ALARM")) {
            return false;
        }
        // Do not collapse "fire alarm system" into branch-level alarm count intent.
        return containsSubsystemKeyword(question)
                || question.contains("FIRE ALARM SYSTEM")
                || question.contains("INTRUSION ALARM SYSTEM")
                || question.contains("ACCESS CONTROL SYSTEM")
                || question.contains("TIME LOCK");
    }

    private boolean isGlobalQuestion(String normalizedQuestion, boolean hasTargetBranch) {
        String question = normalizedQuestion.toLowerCase(Locale.ROOT);
        if (hasTargetBranch) {
            return false;
        }
        return hasGlobalMarkers(question);
    }

    private boolean hasGlobalMarkers(String normalizedQuestion) {
        String question = normalizedQuestion.toLowerCase(Locale.ROOT);
        return question.contains("all")
                || question.contains("list")
                || question.contains("total")
                || question.contains("what branch")
                || question.contains("what device")
                || question.contains("which branch")
                || question.contains("how many branch")
                || question.contains("how many device")
                || question.contains("inactive branch")
                || question.contains("active branch")
                || question.contains("any inactive")
                || question.contains("any active")
                || question.contains("any branch")
                || question.contains("are there branch")
                || isGlobalCountWithStatus(question);
    }

    /**
     * A fleet-wide count/list combined with a connectivity word, e.g. "how many of my branches are
     * offline" or "count of online branches". These are global overview questions; they must be
     * answered by counting live snapshots (consistent with the global overview), not from a
     * single-branch context. Mirrors the router's classification of the same shape.
     */
    private boolean isGlobalCountWithStatus(String question) {
        boolean countOrList = question.contains("how many") || question.contains("count");
        boolean statusWord = question.contains("offline") || question.contains("online")
                || question.contains("inactive") || question.contains("active")
                || question.contains("connected") || question.contains("disconnected");
        return countOrList && statusWord;
    }

    private boolean containsSubsystemKeyword(String normalizedQuestion) {
        return detectSubsystem(normalizedQuestion) != null;
    }

    private String detectSubsystem(String normalizedQuestion) {
        if (normalizedQuestion.contains("IAS") || normalizedQuestion.contains("INTRUSION")) {
            return "ias";
        }
        if (normalizedQuestion.contains("BAS")) {
            return "bas";
        }
        if (normalizedQuestion.contains("FAS") || normalizedQuestion.contains("FIRE")) {
            return "fas";
        }
        if (normalizedQuestion.contains("TIME LOCK")) {
            return "timeLock";
        }
        if (normalizedQuestion.contains("ACCESS CONTROL") || normalizedQuestion.contains("ACS")) {
            return "accessControl";
        }
        if (normalizedQuestion.contains("CCTV") || normalizedQuestion.contains("CAMERA")) {
            return "cctv";
        }
        return null;
    }

    /**
     * The specific sub-device field a question asks about — power / system / log / health status.
     * Returns the SubsystemStatus field key (powerStatus|systemStatus|logStatus|healthStatus) or null
     * when no specific field is named (in which case the subsystem roll-up status is reported instead).
     */
    private String detectSubsystemAttribute(String normalizedQuestion) {
        String question = normalizedQuestion.toUpperCase(Locale.ROOT);
        if (question.contains("LOG STATUS") || (question.contains("LOG") && question.contains("STATUS"))) {
            return "logStatus";
        }
        if (question.contains("POWER STATUS") || (question.contains("POWER") && question.contains("STATUS"))) {
            return "powerStatus";
        }
        if (question.contains("SYSTEM STATUS") || (question.contains("SYSTEM") && question.contains("STATUS"))) {
            return "systemStatus";
        }
        if (question.contains("HEALTH STATUS")
                || ((question.contains("HEALTH") || question.contains("HEALTHY")) && question.contains("STATUS"))) {
            return "healthStatus";
        }
        return null;
    }

    private boolean isBranchMetricIntent(QueryIntent intent) {
        return switch (intent) {
            case BATTERY_VOLTAGE,
                    BATTERY_LOW_STATUS,
                    BATTERY_HEALTH,
                    POWER_STATUS,
                    AC_VOLTAGE,
                    SYSTEM_CURRENT,
                    NETWORK_STATUS,
                    CCTV_STATUS,
                    CCTV_HDD_ERROR_STATUS,
                    CCTV_HDD_INFO,
                    CCTV_RECORDING_INFO,
                    CAMERA_DISCONNECT_HISTORY,
                    ALARM_STATUS,
                    ERROR_STATUS,
                    SUBSYSTEM_FAULT_STATUS,
                    SUBSYSTEM_ALARM_STATUS,
                    SUBSYSTEM_STATUS,
                    ACTIVE_DEVICES,
                    CONNECTED_DEVICES,
                    OFFLINE_DEVICES,
                    FAULT_DEVICES,
                    DOOR_STATUS,
                    ACCESS_CONTROL_USER_COUNT,
                    ACCESS_CONTROL_DEVICE_INFO,
                    DEVICE_IMEI,
                    CCTV_DEVICE_INFO -> true;
            default -> false;
        };
    }
}
