package com.seple.ThingsBoard_Bot.service.query;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchDictionary;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchResolution;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;

@Component
public class QueryIntentResolver {

    private final BranchAliasIndex branchAliasIndex;
    private final FuzzyBranchResolver fuzzyBranchResolver;
    private final com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService glossaryService;

    public QueryIntentResolver(BranchAliasIndex branchAliasIndex, FuzzyBranchResolver fuzzyBranchResolver,
            com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService glossaryService) {
        this.branchAliasIndex = branchAliasIndex;
        this.fuzzyBranchResolver = fuzzyBranchResolver;
        this.glossaryService = glossaryService;
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
        if (isPotentialMultiIntent(normalizedQuestion) && !isFleetScopedIntent(intent) && !intent.isKnowledge()) {
            intent = QueryIntent.GENERAL_LLM;
        }

        // Fleet-scoped intents (hierarchy navigation, category health, ranking, filters) operate on
        // the whole scoped set but are NOT a global overview and must not be hijacked by it or flagged
        // ambiguous for clarification.
        boolean fleetScoped = isFleetScopedIntent(intent);
        boolean global = !fleetScoped && isGlobalQuestion(normalizedQuestion, targetBranch != null);

        // Option A: metric questions must be branch-specific UNLESS explicitly global.
        // If user asks a metric without a branch and without global markers,
        // force clarification instead of falling back to global overview.
        if (targetBranch == null && isBranchMetricIntent(intent) && !hasGlobalMarkers(normalizedQuestion)) {
            global = false;
        }

        // AMBIGUITY DETECTION: If no branch found anywhere, NOT explicitly global, NOT fleet-scoped,
        // NOT a general conversation, and NOT a knowledge intent (glossary/capability questions are
        // inherently branchless - asking "which branch?" for "what does stale mean" is nonsense).
        boolean ambiguous = targetBranch == null && !global && !fleetScoped
                && intent != QueryIntent.GENERAL_LLM && !intent.isKnowledge();

        // FUZZY FALLBACK: exact matching found no branch - try typo-tolerant resolution before
        // asking the user to clarify. A high-confidence hit resolves silently; mid/low bands stay
        // ambiguous but carry the resolution so the caller can ask "Did you mean X?" instead of a
        // generic clarification.
        BranchResolution branchResolution = null;
        if (ambiguous) {
            branchResolution = fuzzyResolve(normalizedQuestion, snapshots);
            if (branchResolution != null && branchResolution.status() == BranchResolution.Status.RESOLVED) {
                targetBranch = findSnapshotByTechnicalId(snapshots, branchResolution.match().technicalId());
                if (targetBranch != null) {
                    ambiguous = false;
                    intent = detectIntent(normalizedQuestion, true);
                }
            }
        }

        // ZONE FILTER EXTRACTION: when the intent is ZONE_OVERVIEW, extract the
        // zone/NBG name from the question so the handler can filter snapshots,
        // and clear targetBranch so this is treated as a zone-wide query.
        String zoneFilter = null;
        if (intent == QueryIntent.ZONE_OVERVIEW) {
            targetBranch = null;
            zoneFilter = extractZoneNameFromQuestion(normalizedQuestion.toUpperCase(Locale.ROOT));
        }

        boolean deterministic = intent != QueryIntent.GENERAL_LLM;
        double confidence = targetBranch != null || global || ambiguous || fleetScoped ? 0.95 : 0.55;

        return ResolvedQuery.builder()
                .intent(intent)
                .originalQuestion(question)
                .targetBranch(targetBranch)
                .targetSystem(detectSubsystem(normalizedQuestion))
                .targetAttribute(detectSubsystemAttribute(normalizedQuestion))
                .global(global)
                .ambiguous(ambiguous)
                .branchFromMemory(branchFromMemory)
                .deterministic(deterministic && (global || targetBranch != null || fleetScoped || intent.isKnowledge()))
                .confidence(confidence)
                .zoneFilter(zoneFilter)
                .branchResolution(branchResolution)
                .build();
    }

    private static final java.util.Set<String> SKIP_FUZZY_WORDS = java.util.Set.of(
            "ACTIVE", "INACTIVE", "ONLINE", "OFFLINE", "STATUS", "WORKING", "DEVICE", "DEVICES",
            "BRANCH", "BRANCHES", "BRANCHS", "CAMERA", "CAMERAS", "BATTERY", "ALARM", "ALARMS", "SYSTEM",
            "HEALTH", "FAULT", "ERROR", "TOTAL", "COUNT", "MANY", "THERE", "THEIR", "HERE", "ANY", "ARE", "IS",
            "PLEASE", "SHOW", "LIST", "GIVE", "WHAT", "WHICH", "WHERE", "WHEN", "WITH", "FROM"
    );

    private boolean isWindowAllSkipWords(String window) {
        String[] parts = window.toUpperCase(java.util.Locale.ROOT).split("\\s+");
        for (String part : parts) {
            if (!SKIP_FUZZY_WORDS.contains(part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scores every 1-3 word window of the question against the branch dictionary and returns
     * the best fuzzy resolution, or null when nothing clears the suggestion floor. The
     * dictionary is built from the caller's snapshots, so customer scoping is inherited.
     */
    private BranchResolution fuzzyResolve(String normalizedQuestion, List<BranchSnapshot> snapshots) {
        BranchDictionary dictionary = BranchDictionary.fromSnapshots(snapshots, null, branchAliasIndex);
        if (dictionary.isEmpty()) {
            return null;
        }
        String[] words = normalizedQuestion.split("\\s+");
        BranchResolution best = null;
        double bestScore = 0.0;
        for (int start = 0; start < words.length; start++) {
            for (int len = 1; len <= 3 && start + len <= words.length; len++) {
                String window = String.join(" ", java.util.Arrays.copyOfRange(words, start, start + len));
                if (window.length() < 2 || isWindowAllSkipWords(window)) {
                    continue;
                }
                BranchResolution candidate = fuzzyBranchResolver.resolve(window, dictionary);
                if (candidate.status() == BranchResolution.Status.NO_MATCH || candidate.candidates().isEmpty()) {
                    continue;
                }
                double score = candidate.candidates().get(0).score();
                if (best == null || score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    /**
     * Phase 3 keyword fast path for knowledge intents - works in every extractor mode.
     * Definitional phrasings require the candidate term to actually exist in the glossary so
     * "what is Tarakeshwar battery voltage" (branch missed for any reason) never lands here.
     * Returns null when the question is not a knowledge question.
     */
    private QueryIntent detectKnowledgeIntent(String question) {
        boolean definitional = question.contains("WHAT DOES") && question.contains("MEAN")
                || question.contains("MEANING OF")
                || question.contains("DEFINITION OF")
                || question.startsWith("DEFINE ")
                || question.startsWith("WHAT IS A ") || question.startsWith("WHAT IS AN ");
        if (definitional && glossaryService.containsKnownTerm(question)) {
            return QueryIntent.GLOSSARY;
        }
        if (question.startsWith("EXPLAIN ") && glossaryService.containsKnownTerm(question)) {
            return QueryIntent.CONCEPT_EXPLAIN;
        }

        boolean howPhrasing = question.contains("HOW DO I") || question.contains("HOW CAN I")
                || question.startsWith("HOW TO ");
        if (howPhrasing) {
            boolean repairPhrasing = question.contains("FIX") || question.contains("RESOLVE")
                    || question.contains("REPAIR") || question.contains("RESTORE")
                    || question.contains("TROUBLESHOOT");
            return repairPhrasing ? QueryIntent.TROUBLESHOOTING : QueryIntent.HOW_TO;
        }

        // Narrow on purpose: "WHERE IS <branch>" stays a GPS/location question; only the
        // "where can/do I ..." UI-seeking phrasing is navigation.
        if (question.contains("WHERE CAN I") || question.contains("WHERE DO I") || isNavigationQuestion(question)) {
            return QueryIntent.NAVIGATION;
        }
        return null;
    }

    private boolean isNavigationQuestion(String question) {
        String clean = question.toUpperCase(Locale.ROOT);
        
        // Verbs indicating request to find/navigate/see
        boolean hasNavVerb = clean.contains("NAVIGATE") || clean.contains("GO TO") 
                || clean.contains("FIND") || clean.contains("OPEN") 
                || containsWord(clean, "VIEW") || clean.contains("ACCESS") 
                || clean.contains("GUIDE") || clean.contains("WHERE IS THE")
                || clean.contains("WHERE DO I FIND") || clean.contains("WHERE CAN I FIND")
                || clean.contains("HOW TO GO") || clean.contains("HOW DO I GO")
                || clean.contains("HOW CAN I GO") || clean.contains("HOW TO SEE")
                || clean.contains("HOW DO I SEE") || clean.contains("HOW CAN I SEE")
                || clean.contains("FILND"); // catch common typo like "filnd"

        // Nouns indicating UI structures
        boolean hasNavNoun = clean.contains("PAGE") || clean.contains("SCREEN") 
                || clean.contains("TAB") || clean.contains("DASHBOARD") 
                || clean.contains("LIST") || clean.contains("REPORT") 
                || clean.contains("MENU") || clean.contains("SIDEBAR");
                
        // Core screen names
        boolean hasScreenName = clean.contains("DEVICE") || clean.contains("BRANCH") 
                || clean.contains("HEALTH") || clean.contains("MAP") 
                || clean.contains("VAULT") || clean.contains("INSIGHT") 
                || clean.contains("LOG") || clean.contains("TAT")
                || clean.contains("UPTIME") || clean.contains("RECORDING")
                || clean.contains("ALARM") || clean.contains("ERROR");

        return hasNavVerb || (clean.contains("WHERE IS") && (hasNavNoun || hasScreenName));
    }

    private BranchSnapshot findSnapshotByTechnicalId(List<BranchSnapshot> snapshots, String technicalId) {
        if (technicalId == null) {
            return null;
        }
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() != null && technicalId.equals(snapshot.getIdentity().getTechnicalId())) {
                return snapshot;
            }
        }
        return null;
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

    private static final java.util.Set<String> RESERVED_ALIAS_WORDS = java.util.Set.of(
            "ACTIVE", "INACTIVE", "ONLINE", "OFFLINE", "STATUS", "WORKING", "DEVICE", "DEVICES",
            "BRANCH", "BRANCHES", "BRANCHS", "CAMERA", "CAMERAS", "BATTERY", "ALARM", "ALARMS", "SYSTEM",
            "HEALTH", "FAULT", "ERROR", "TOTAL", "COUNT", "GATEWAY", "TEST"
    );

    private boolean isWeakAlias(String alias) {
        String normalizedAlias = branchAliasIndex.normalize(alias);
        if (normalizedAlias.length() < 4) {
            return true;
        }
        return RESERVED_ALIAS_WORDS.contains(normalizedAlias);
    }

    private QueryIntent detectIntent(String normalizedQuestion, boolean hasTargetBranch) {
        String question = normalizedQuestion.toUpperCase(Locale.ROOT);
        // Knowledge intents first (Phase 3): definitional and capability phrasings are checked
        // before any data-keyword branch, otherwise "what does STALE mean" would hit the
        // LAST_REPORTED keyword and "what does IMEI mean" would hit DEVICE_IMEI. Only fires when
        // no branch is named, so data questions are never stolen.
        if (!hasTargetBranch) {
            QueryIntent knowledgeIntent = detectKnowledgeIntent(question);
            if (knowledgeIntent != null) {
                return knowledgeIntent;
            }
        }
        // Data quality questions ("what % have IMEI", "data completeness", "data quality")
        if (isDataQualityQuestion(question)) {
            return QueryIntent.DATA_QUALITY;
        }
        // Instant briefing / triage ("what should I look at first", "morning brief", "top risks")
        if (isInstantBriefingQuestion(question)) {
            return QueryIntent.INSTANT_BRIEFING;
        }
        if (question.contains("IMEI")) {
            return QueryIntent.DEVICE_IMEI;
        }
        // Hierarchy navigation (NBG/zone listing, drill-down, owning node). Detected early so the
        // NBG/zone keywords aren't mistaken for device-status questions. Returns null when the
        // question isn't structural, so normal intent detection continues.
        QueryIntent hierarchyIntent = detectHierarchyIntent(question);
        if (hierarchyIntent != null) {
            return hierarchyIntent;
        }
        // Fleet-wide per-subsystem overview: "CCTV/IAS/FAS/BAS/TLS/ACS status of all branches". An
        // explicit fleet marker plus a subsystem keyword is a per-subsystem roll-up across every
        // branch, not a single-branch question — it must win over an accidental fuzzy branch match
        // (e.g. "BAS" ~ branch "BASTA") and over the single-branch CCTV/subsystem keyword returns
        // below. The subsystem is carried on ResolvedQuery.targetSystem (set via detectSubsystem).
        if (isFleetSubsystemOverview(question)) {
            return QueryIntent.GLOBAL_OVERVIEW;
        }
        // Category (device-type) health breakdown across the fleet -- only when no single branch is
        // the focus, so it doesn't steal a per-branch gateway/battery question.
        if (!hasTargetBranch && isCategoryHealthQuestion(question)) {
            return QueryIntent.CATEGORY_HEALTH;
        }
        // Cross-branch comparison ("compare A and B") and ranking ("top 5 branches by alarms").
        if (isCompareQuestion(question)) {
            return QueryIntent.BRANCH_COMPARE;
        }
        if (isRankingQuestion(question)) {
            return QueryIntent.BRANCH_RANKING;
        }
        if (isFilterQuestion(question)) {
            return QueryIntent.BRANCH_FILTER;
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
        // GPS / location lookup. Uses full words (LATITUDE/LONGITUDE, not bare LAT/LONG) and specific
        // phrases so it doesn't misfire on substrings like "related" or "location" inside other words.
        if (question.contains("GPS") || question.contains("COORDINATE") || question.contains("LOCATION")
                || question.contains("LATITUDE") || question.contains("LONGITUDE")
                || question.contains("WHERE IS")) {
            return QueryIntent.GPS_LOCATION;
        }
        // Last-reported / staleness ("when was the last update", "last reported", "stale"). Uses
        // specific phrases so it doesn't collide with report-generation or alarm-recency questions.
        if (question.contains("LAST UPDATE") || question.contains("LAST UPDATED")
                || question.contains("LAST REPORT") || question.contains("LAST SEEN")
                || question.contains("LAST ACTIVITY") || question.contains("STALE")) {
            return QueryIntent.LAST_REPORTED;
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
        if (question.contains("OFFLINE") || question.contains("INACTIVE") || question.contains("DOWN") || question.contains("UNHEALTHY")) {
            return QueryIntent.OFFLINE_DEVICES;
        }
        if (question.contains("CONNECTED DEVICE") || (question.contains("ALL DEVICES") && question.contains("CONNECTED"))) {
            return QueryIntent.CONNECTED_DEVICES;
        }
        if ((question.contains("ACTIVE DEVICE") || question.contains("ACTIVE BRANCH") || (question.contains("ACTIVE") && (question.contains("DEVICE") || question.contains("BRANCH"))))
                && !question.contains("INACTIVE")) {
            return QueryIntent.ACTIVE_DEVICES;
        }
        // Network operator / SIM ("which SIM", "service provider", "carrier"). Must precede the
        // generic NETWORK -> NETWORK_STATUS check so an operator question isn't answered as on/off.
        if (question.contains("OPERATOR") || question.contains("CARRIER")
                || question.contains("SERVICE PROVIDER") || question.contains("NETWORK PROVIDER")
                || containsWord(question, "SIM")) {
            return QueryIntent.NETWORK_OPERATOR;
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
        // Severity / detail alarm questions go to ALARM_DETAIL; plain "alarm" goes to ALARM_STATUS.
        if (isAlarmDetailQuestion(question)) {
            return QueryIntent.ALARM_DETAIL;
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
                || question.contains("overview")
                || question.contains("summary")
                || question.contains("every branch")
                || question.contains("each branch")
                || question.contains("every")
                || question.contains("across")
                || question.contains("fleet")
                || question.contains("head office")
                || question.contains("headoffice")
                || question.contains("what branch")
                || question.contains("what device")
                || question.contains("which branch")
                || question.contains("how many branch")
                || question.contains("how many device")
                || question.contains("inactive branch")
                || question.contains("active branch")
                || question.contains("any inactive")
                || question.contains("any active")
                || question.contains("any offline")
                || question.contains("any online")
                || question.contains("any branch")
                || question.contains("are there branch")
                || question.contains("are their branch")
                || question.contains("are there any")
                || question.contains("are their any")
                || isGlobalCountWithStatus(question);
    }

    /**
     * A fleet-wide count/list combined with a connectivity word, e.g. "how many of my branches are
     * offline" or "count of online branches". These are global overview questions; they must be
     * answered by counting live snapshots (consistent with the global overview), not from a
     * single-branch context. Mirrors the router's classification of the same shape.
     */
    private boolean isGlobalCountWithStatus(String question) {
        boolean countOrList = question.contains("how many") || question.contains("count")
                || question.contains("are there") || question.contains("are their");
        boolean statusWord = question.contains("offline") || question.contains("online")
                || question.contains("inactive") || question.contains("active")
                || question.contains("connected") || question.contains("disconnected");
        return countOrList && statusWord;
    }

    private boolean containsSubsystemKeyword(String normalizedQuestion) {
        return detectSubsystem(normalizedQuestion) != null;
    }

    private boolean isHierarchyIntent(QueryIntent intent) {
        return intent == QueryIntent.HIERARCHY_LIST_NODES
                || intent == QueryIntent.HIERARCHY_BRANCHES_UNDER
                || intent == QueryIntent.HIERARCHY_OWNER;
    }

    /** Intents that operate on the whole scoped snapshot set rather than one branch or the global overview. */
    private boolean isFleetScopedIntent(QueryIntent intent) {
        return isHierarchyIntent(intent) || intent == QueryIntent.CATEGORY_HEALTH
                || intent == QueryIntent.BRANCH_RANKING || intent == QueryIntent.BRANCH_COMPARE
                || intent == QueryIntent.BRANCH_FILTER || intent == QueryIntent.ZONE_OVERVIEW
                || intent == QueryIntent.DATA_QUALITY || intent == QueryIntent.INSTANT_BRIEFING;
    }

    /**
     * Multi-condition branch filter ("branches where Gateway is offline but IAS is online", "branches
     * missing CCTV and ACS", "branches with at least 3 active systems", "branches where all systems
     * are N/A"). Gated on the plural "branches" plus a where/missing/all-systems/count cue.
     */
    private boolean isFilterQuestion(String question) {
        if (!question.contains("BRANCHES")) {
            return false;
        }
        if (question.contains("ALL SYSTEMS") || question.contains("WHERE") || question.contains("MISSING")) {
            return true;
        }
        boolean countCue = question.contains("AT LEAST") || question.contains("OR MORE")
                || question.contains("MORE THAN") || question.contains("MINIMUM");
        return countCue && question.contains("SYSTEM");
    }

    /**
     * Cross-branch ranking ("top 5 branches by alarms", "which branch has the most cameras"). Requires
     * a recognizable rank metric; the handler still returns null (-> honest decline) if the metric
     * isn't one it can compute, so e.g. "rank NBGs by health score" is not falsely answered.
     */
    private boolean isRankingQuestion(String question) {
        // For zone/NBG questions: only block ranking when there is no rankable metric keyword.
        // A question like "rank NBGs by health score" has no computable metric -> block.
        // But "top 5 branches by alarms in ZO Howrah" IS rankable -> allow.
        if (question.contains("NBG") || question.contains("ZONE") || containsWord(question, "ZO")) {
            boolean hasRankMetric = question.contains("ALARM") || question.contains("ERROR")
                    || question.contains("BATTERY") || question.contains("CAMERA")
                    || question.contains("CCTV") || question.contains("FAULT");
            if (!hasRankMetric) return false;
        }
        // NOTE: the alias normalizer strips the singular word "branch", so don't gate on it here.
        boolean rankVerb = question.contains("TOP ") || question.contains("BOTTOM ") || question.contains("RANK");
        boolean superlative = (question.contains("WHICH") || question.contains("WHAT"))
                && (question.contains("MOST") || question.contains("HIGHEST")
                        || question.contains("LEAST") || question.contains("LOWEST"));
        return rankVerb || superlative;
    }

    private boolean isCompareQuestion(String question) {
        return question.contains("COMPARE") || question.contains(" VS ") || question.contains("VERSUS");
    }

    /**
     * Category (device-type) health breakdown, e.g. "what % of Gateways are healthy", "which category
     * has the most inactive devices", "show category-wise health". Gated on a device-category word so
     * it doesn't fire on unrelated percentage/health questions.
     */
    private boolean isCategoryHealthQuestion(String question) {
        if (question.contains("CATEGORY")) {
            return true;
        }
        boolean percent = question.contains("PERCENT") || question.contains("%");
        if (!percent) {
            return false;
        }
        boolean categoryWord = question.contains("GATEWAY") || question.contains("CCTV")
                || question.contains("CAMERA") || question.contains("IAS") || question.contains("BAS")
                || question.contains("FAS") || question.contains("TLS") || question.contains("ACS");
        // "what percentage of Gateway devices are healthy/inactive" -> category-word percentage; or a
        // bare "what percentage are healthy" -> treat as a fleet category-health breakdown.
        return categoryWord || question.contains("HEALTH");
    }

    /**
     * Hierarchy intent for a question, or null when it isn't structural. Triggers only on an explicit
     * NBG/zone mention combined with a navigation verb, so device-status questions that merely name a
     * region (e.g. "active devices in NBG East") are left to the normal device intents.
     */
    private QueryIntent detectHierarchyIntent(String question) {
        boolean mentionsNbg = question.contains("NBG");
        boolean mentionsZone = question.contains("ZONE") || question.contains("ZONAL")
                || question.contains("ZONES") || containsWord(question, "ZO");
        if (!mentionsNbg && !mentionsZone) {
            return null;
        }
        // "which NBG/zone owns X" / "X belongs to which zone".
        if (question.contains("OWN") || question.contains("BELONG")) {
            return QueryIntent.HIERARCHY_OWNER;
        }
        // Drill-down: "branches under/in/within <node>".
        if (question.contains("BRANCH")
                && (question.contains("UNDER") || question.contains("WITHIN") || question.contains("IN "))) {
            return QueryIntent.HIERARCHY_BRANCHES_UNDER;
        }
        // Listing/counting NBGs or zones. NOTE: ranking ("rank NBGs by health score") is deliberately
        // NOT handled here -- this handler has only names, not scores, so ranking falls to the
        // honest-decline LLM path.
        if (question.contains("LIST") || question.contains("HOW MANY") || question.contains("SHOW ALL")
                || question.contains("ALL NBG") || question.contains("ALL ZONE")) {
            return QueryIntent.HIERARCHY_LIST_NODES;
        }
        // ZONE-SCOPED DATA QUERY: The question mentions a zone/NBG and also contains
        // data keywords (status, gateway, offline, CCTV, FAS, alarm, etc.) but doesn't
        // match any structural navigation verb above. This is a zone-filtered data
        // question like "gateway status for ZO Howrah" or "offline branches in ZO Barasat".
        if (hasDataKeyword(question)) {
            return QueryIntent.ZONE_OVERVIEW;
        }
        return null;
    }

    /**
     * Returns true when the question contains keywords that indicate a data/metric
     * question rather than a structural hierarchy question.
     */
    private boolean hasDataKeyword(String question) {
        return question.contains("STATUS") || question.contains("GATEWAY")
                || question.contains("OFFLINE") || question.contains("ONLINE")
                || question.contains("INACTIVE") || question.contains("ACTIVE")
                || question.contains("CCTV") || question.contains("CAMERA")
                || question.contains("FAS") || question.contains("IAS")
                || question.contains("BAS") || question.contains("ALARM")
                || question.contains("POWER") || question.contains("BATTERY")
                || question.contains("HEALTH") || question.contains("FAULT")
                || question.contains("ERROR") || question.contains("DOOR")
                || question.contains("NETWORK") || question.contains("ACS")
                || question.contains("ACCESS CONTROL") || question.contains("TIME LOCK");
    }

    /**
     * Extracts the zone or NBG name from the question. Looks for "ZO <name>",
     * "ZONE <name>", or "NBG <name>" patterns.
     */
    private String extractZoneNameFromQuestion(String upperQuestion) {
        // Strip trailing punctuation from question
        String cleanQuestion = upperQuestion.replaceAll("[\\?\\!\\.,;:_]", " ");
        String[] prefixes = { "ZO ", "ZONE ", "NBG " };
        for (String prefix : prefixes) {
            int idx = cleanQuestion.indexOf(prefix);
            if (idx < 0) continue;
            String afterPrefix = cleanQuestion.substring(idx + prefix.length()).trim();
            String[] words = afterPrefix.split("\\s+");
            StringBuilder name = new StringBuilder();
            for (String word : words) {
                String cleanWord = word.replaceAll("[^A-Z0-9/]", "");
                if (cleanWord.isEmpty() || isDataOrStopWord(cleanWord)) break;
                if (!name.isEmpty()) name.append(" ");
                name.append(cleanWord);
            }
            if (!name.isEmpty()) {
                return prefix.trim() + " " + name.toString().trim();
            }
        }
        return null;
    }

    private boolean isDataOrStopWord(String word) {
        return java.util.Set.of(
                "STATUS", "GATEWAY", "OFFLINE", "ONLINE", "INACTIVE", "ACTIVE",
                "CCTV", "CAMERA", "FAS", "IAS", "BAS", "ALARM", "POWER",
                "BATTERY", "HEALTH", "FAULT", "ERROR", "DOOR", "NETWORK",
                "ACS", "ACCESS", "TIME", "BRANCHES", "BRANCH", "DEVICE",
                "DEVICES", "ALL", "ANY", "HOW", "MANY", "WHAT", "WHICH",
                "SHOW", "LIST", "ARE", "IS", "THE", "FOR", "OF", "IN",
                "CURRENT", "OVERVIEW", "SUMMARY", "REPORT",
                // Bug 4c: these words were being included in the extracted zone name
                "RELATED", "EVERY", "EACH", "WITHIN", "UNDER", "ABOUT",
                "PLEASE", "GIVE", "ME", "TELL", "AND", "OR", "FROM", "TO"
        ).contains(word);
    }

    /**
     * A fleet marker ("all"/"every branch"/"overview") combined with a named subsystem. Gateway is
     * intentionally excluded (detectSubsystem returns null for it) so plain "gateway status of all
     * branches" keeps flowing to the existing gateway global overview.
     */
    private boolean isFleetSubsystemOverview(String question) {
        boolean hasSubsystem = detectSubsystem(question) != null;
        if (!hasSubsystem) {
            return false;
        }
        boolean fleetMarker = containsWord(question, "ALL")
                || question.contains("OVERVIEW")
                || question.contains("EVERY BRANCH")
                || question.contains("EACH BRANCH")
                || question.contains("HOW MANY")
                || question.contains("COUNT")
                || question.contains("ANY");
        boolean statusOrCount = question.contains("OFFLINE") || question.contains("ONLINE")
                || question.contains("INACTIVE") || question.contains("ACTIVE")
                || question.contains("STATUS") || question.contains("SUMMARY")
                || question.contains("DISCONNECT");
        return fleetMarker && statusOrCount;
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
        if (normalizedQuestion.contains("TIME LOCK") || containsWord(normalizedQuestion, "TLS")) {
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
                    CCTV_DEVICE_INFO,
                    LAST_REPORTED -> true;
            default -> false;
        };
    }

    private boolean isPotentialMultiIntent(String normalizedQuestion) {
        String question = normalizedQuestion.toLowerCase(Locale.ROOT);
        if (!question.contains("and") && !question.contains("plus") && !question.contains(",") && !question.contains("with")) {
            return false;
        }

        // Check if multiple distinct subsystems are requested (e.g. IAS and FAS)
        if (countDistinctSubsystems(question) >= 2) {
            return true;
        }

        int categoriesMatched = 0;
        if (containsAnyKeyword(question, "battery", "volt", "power", "mains", "ups", "current", "ac")) categoriesMatched++;
        if (containsAnyKeyword(question, "cctv", "camera", "nvr", "dvr", "recording", "disconnect", "hdd")) categoriesMatched++;
        if (containsAnyKeyword(question, "alarm", "error", "ias", "bas", "fas", "fire", "intrusion", "tamper", "door")) categoriesMatched++;
        if (containsAnyKeyword(question, "access control", "acs", "biometric", "user")) categoriesMatched++;

        return categoriesMatched >= 2;
    }

    private int countDistinctSubsystems(String question) {
        int count = 0;
        if (question.contains("ias") || question.contains("intrusion")) count++;
        if (question.contains("bas")) count++;
        if (question.contains("fas") || question.contains("fire")) count++;
        if (question.contains("time lock") || question.contains("timelock")) count++;
        if (question.contains("access control") || question.contains("acs")) count++;
        if (question.contains("cctv") || question.contains("camera")) count++;
        return count;
    }

    private boolean containsAnyKeyword(String question, String... keywords) {
        for (String keyword : keywords) {
            if (question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * "What % have IMEI", "data quality", "data completeness", "how many devices have GPS".
     * Must not fire on "what does data quality mean" (caught earlier by the glossary check).
     */
    private boolean isDataQualityQuestion(String question) {
        if (question.contains("DATA QUALITY") || question.contains("DATA COMPLETENESS")) {
            return true;
        }
        boolean pctOrCount = question.contains("%") || question.contains("PERCENT")
                || question.contains("HOW MANY") || question.contains("RATIO");
        boolean qualityField = question.contains("IMEI") || question.contains("GPS")
                || question.contains("NETWORK");
        boolean qualityVerb = question.contains("HAVE") || question.contains("MISSING")
                || question.contains("AVAILABLE") || question.contains("MAPPED")
                || question.contains("REPORTED");
        return pctOrCount && qualityField && qualityVerb;
    }

    /**
     * "What should I look at first", "morning brief", "one-line summary", "priority triage",
     * "top risk items", "critical issues first".
     */
    private boolean isInstantBriefingQuestion(String question) {
        return question.contains("LOOK AT FIRST")
                || question.contains("MORNING BRIEF")
                || question.contains("ONE LINE SUMMARY") || question.contains("ONE-LINE SUMMARY")
                || question.contains("QUICK STATUS")
                || question.contains("PRIORITY TRIAGE") || question.contains("TRIAGE")
                || question.contains("TOP RISK") || question.contains("RISK ITEM")
                || question.contains("CRITICAL ISSUE") && question.contains("FIRST")
                || question.contains("WHAT SHOULD I") && question.contains("FIRST")
                || question.contains("INSTANT BRIEF");
    }

    /**
     * Severity-level alarm detail questions that should go to {@link QueryIntent#ALARM_DETAIL}
     * rather than the simple {@link QueryIntent#ALARM_STATUS} count.
     * Examples: "critical alarms", "major alarms", "severity breakdown", "alarm types".
     */
    private boolean isAlarmDetailQuestion(String question) {
        boolean hasSeverity = question.contains("CRITICAL") || question.contains("MAJOR")
                || question.contains("MINOR") || question.contains("WARNING");
        boolean hasAlarmContext = question.contains("ALARM");
        if (hasSeverity && hasAlarmContext) return true;

        boolean hasBreakdown = question.contains("SEVERITY") || question.contains("BREAKDOWN")
                || question.contains("DISTRIBUTION") || question.contains("WHAT TYPE")
                || question.contains("WHAT KIND") || question.contains("ALARM TYPE");
        return hasBreakdown && hasAlarmContext;
    }
}
