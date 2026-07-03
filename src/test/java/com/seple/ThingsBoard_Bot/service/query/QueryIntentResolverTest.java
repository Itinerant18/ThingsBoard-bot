package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.FullDataPayloadParser;
import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;
import com.seple.ThingsBoard_Bot.service.query.resolve.ManualAliasTable;
import com.seple.ThingsBoard_Bot.support.FixtureLoader;

class QueryIntentResolverTest {

    private QueryIntentResolver resolver;
    private List<BranchSnapshot> snapshots;

    @BeforeEach
    void setUp() throws Exception {
        BranchAliasIndex aliasIndex = new BranchAliasIndex();
        resolver = new QueryIntentResolver(aliasIndex,
                new FuzzyBranchResolver(aliasIndex, new ManualAliasTable(aliasIndex), 0.90, 0.75, 0.55),
                new com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService(
                        new org.springframework.core.io.ClassPathResource("glossary.json")));
        FullDataPayloadParser parser = new FullDataPayloadParser();
        BranchSnapshotMapper mapper = new BranchSnapshotMapper(
                new FieldPrecedenceResolver(new ValueNormalizer()), new ValueNormalizer());

        String json = FixtureLoader.load("fixtures/full_data_fixture.json");
        snapshots = parser.parse(json).branches().values().stream()
                .map(mapper::map)
                .collect(Collectors.toList());
    }

    @Test
    void shouldResolveBranchBatteryQuestionDeterministically() {
        ResolvedQuery resolved = resolver.resolve("What is Tarakeshwar battery voltage?", snapshots, null);

        assertEquals(QueryIntent.BATTERY_VOLTAGE, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-TARAKESHWAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveTypoBranchNameThroughFuzzyFallback() {
        // "Tarakeswar" (missing H) has no exact alias variant - fuzzy fallback must recover it.
        ResolvedQuery resolved = resolver.resolve("What is Tarakeswar battery voltage?", snapshots, null);

        assertEquals(QueryIntent.BATTERY_VOLTAGE, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-TARAKESHWAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
        assertEquals(false, resolved.isAmbiguous());
        assertNotNull(resolved.getBranchResolution());
    }

    @Test
    void unknownBranchMetricQuestionStaysAmbiguous() {
        ResolvedQuery resolved = resolver.resolve("What is Rampurhat battery voltage?", snapshots, null);

        assertEquals(true, resolved.isAmbiguous());
        assertEquals(null, resolved.getTargetBranch());
    }

    @Test
    void glossaryQuestionDetectedAndNotAmbiguous() {
        ResolvedQuery resolved = resolver.resolve("What does stale mean?", snapshots, null);

        assertEquals(QueryIntent.GLOSSARY, resolved.getIntent());
        assertEquals(false, resolved.isAmbiguous());
        assertEquals(true, resolved.isDeterministic());
    }

    @Test
    void glossaryDetectionRequiresKnownTerm() {
        // Definitional phrasing but no glossary term - must not become GLOSSARY.
        ResolvedQuery resolved = resolver.resolve("What does Rampurhat mean?", snapshots, null);
        assertEquals(true, resolved.getIntent() != QueryIntent.GLOSSARY);
    }

    @Test
    void explainPhraseBecomesConceptExplain() {
        ResolvedQuery resolved = resolver.resolve("Explain heartbeat", snapshots, null);
        assertEquals(QueryIntent.CONCEPT_EXPLAIN, resolved.getIntent());
    }

    @Test
    void howToAndTroubleshootingAndNavigationDetected() {
        assertEquals(QueryIntent.HOW_TO,
                resolver.resolve("How do I add a new device?", snapshots, null).getIntent());
        assertEquals(QueryIntent.TROUBLESHOOTING,
                resolver.resolve("How do I fix the camera?", snapshots, null).getIntent());
        assertEquals(QueryIntent.NAVIGATION,
                resolver.resolve("Where can I see the alarms?", snapshots, null).getIntent());
    }

    @Test
    void dataQuestionsNotStolenByKnowledgeDetection() {
        // Branch named -> knowledge detection is skipped entirely.
        ResolvedQuery battery = resolver.resolve("What is Tarakeshwar battery voltage?", snapshots, null);
        assertEquals(QueryIntent.BATTERY_VOLTAGE, battery.getIntent());
        // "stale" keyword with a branch stays a staleness/data question.
        ResolvedQuery stale = resolver.resolve("Is Tarakeshwar data stale?", snapshots, null);
        assertEquals(QueryIntent.LAST_REPORTED, stale.getIntent());
    }

    @Test
    void shouldResolveCameraQuestionToCctvIntent() {
        ResolvedQuery resolved = resolver.resolve("How many cameras are online in Tarakeshwar?", snapshots, null);

        assertEquals(QueryIntent.CCTV_STATUS, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
    }

    @Test
    void shouldResolveBallyBazarAliasToSubsystemQuestion() {
        ResolvedQuery resolved = resolver.resolve("What is Bally Bazar IAS status?", snapshots, null);

        assertEquals(QueryIntent.SUBSYSTEM_STATUS, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
        assertEquals("ias", resolved.getTargetSystem());
    }

    @Test
    void shouldResolveFaultReasonIntent() {
        ResolvedQuery resolved = resolver.resolve("Why is Trendz fault?", snapshots, null);

        assertEquals(QueryIntent.FAULT_REASON, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("Trendz_Testing_Device", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveCameraDisconnectHistoryIntent() {
        ResolvedQuery resolved = resolver.resolve("Show historical camera disconnects for Chandannagar", snapshots, null);

        assertEquals(QueryIntent.CAMERA_DISCONNECT_HISTORY, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-CHANDANNAGAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldNotConfuseCurrentStatusWithSystemCurrent() {
        ResolvedQuery resolved = resolver.resolve("What is the current status of the Branch Chandannagar Gateway?", snapshots, null);

        assertEquals(QueryIntent.GATEWAY_STATUS, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-CHANDANNAGAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveCurrentlyActiveDevicesWithoutFallingIntoCurrentMetric() {
        ResolvedQuery resolved = resolver.resolve("Which devices are currently active on the Branch Bally Bazar Gateway?", snapshots, null);

        assertEquals(QueryIntent.ACTIVE_DEVICES, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveDisconnectStatusWithoutHistoryKeyword() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch Chandannagar CCTV disconnect status right now?", snapshots, null);

        assertEquals(QueryIntent.CAMERA_DISCONNECT_HISTORY, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-CHANDANNAGAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveBatteryLowStatus() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch Tarakeshwar Gateway battery low status right now?", snapshots, null);

        assertEquals(QueryIntent.BATTERY_LOW_STATUS, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-TARAKESHWAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveDoorStatusToSubsystemContext() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch Tarakeshwar Time Lock door status right now?", snapshots, null);

        assertEquals(QueryIntent.DOOR_STATUS, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("timeLock", resolved.getTargetSystem());
    }

    @Test
    void shouldKeepAllBranchesQuestionGlobal() {
        ResolvedQuery resolved = resolver.resolve("What is the status of all devices in all branches right now?", snapshots, null);

        assertEquals(QueryIntent.GLOBAL_OVERVIEW, resolved.getIntent());
        assertEquals(true, resolved.isGlobal());
        assertEquals(null, resolved.getTargetBranch());
    }

    @Test
    void shouldKeepShowAllBranchDevicesQuestionGlobal() {
        ResolvedQuery resolved = resolver.resolve("Show status of all branch devices", snapshots, "BOI-TARAKESHWAR");

        assertEquals(QueryIntent.GLOBAL_OVERVIEW, resolved.getIntent());
        assertEquals(true, resolved.isGlobal());
        assertEquals(null, resolved.getTargetBranch());
    }

    @Test
    void shouldAskBranchClarificationForAllBranchBatteryVoltage() {
        ResolvedQuery resolved = resolver.resolve("all Branch Battery Voltage?", snapshots, null);

        assertEquals(QueryIntent.BATTERY_VOLTAGE, resolved.getIntent());
        assertEquals(false, resolved.isGlobal());
        assertEquals(true, resolved.isAmbiguous());
        assertEquals(null, resolved.getTargetBranch());
    }

    @Test
    void shouldAskBranchClarificationForAllBranchAcVoltage() {
        ResolvedQuery resolved = resolver.resolve("all Branch AC Voltage?", snapshots, null);

        assertEquals(QueryIntent.AC_VOLTAGE, resolved.getIntent());
        assertEquals(false, resolved.isGlobal());
        assertEquals(true, resolved.isAmbiguous());
        assertEquals(null, resolved.getTargetBranch());
    }

    @Test
    void shouldResolveLowBatteryPhraseVariant() {
        ResolvedQuery resolved = resolver.resolve("Is there a low battery warning on the Branch liluah Gateway?", snapshots, null);

        assertEquals(QueryIntent.BATTERY_LOW_STATUS, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-LILUAH", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveCctvHddErrorQuestion() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni CCTV HDD Error right now?", snapshots, null);

        assertEquals(QueryIntent.CCTV_HDD_ERROR_STATUS, resolved.getIntent());
        assertEquals("cctv", resolved.getTargetSystem());
        assertNotNull(resolved.getTargetBranch());
    }

    @Test
    void shouldResolveSubsystemFaultQuestionForBas() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni BAS fault status right now?", snapshots, null);

        assertEquals(QueryIntent.SUBSYSTEM_FAULT_STATUS, resolved.getIntent());
        assertEquals("bas", resolved.getTargetSystem());
    }

    @Test
    void shouldResolveSubsystemAlarmQuestionForTimeLock() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni Time Lock alarm status right now?", snapshots, null);

        assertEquals(QueryIntent.SUBSYSTEM_ALARM_STATUS, resolved.getIntent());
        assertEquals("timeLock", resolved.getTargetSystem());
    }

    @Test
    void shouldResolveBatteryStatusToBatteryHealth() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni battery status right now?", snapshots, null);
        assertEquals(QueryIntent.BATTERY_HEALTH, resolved.getIntent());
    }

    @Test
    void shouldResolveGatewayPowerStatusToPowerStatus() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni Gateway power status right now?", snapshots, null);
        assertEquals(QueryIntent.POWER_STATUS, resolved.getIntent());
    }

    @Test
    void shouldResolveCctvPowerStatusToSubsystemFieldQuery() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni CCTV power status right now?", snapshots, null);
        assertEquals(QueryIntent.SUBSYSTEM_STATUS, resolved.getIntent());
        assertEquals("cctv", resolved.getTargetSystem());
        assertEquals("powerStatus", resolved.getTargetAttribute());
    }

    @Test
    void shouldResolveCctvSystemStatusToSubsystemFieldQuery() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni CCTV system status right now?", snapshots, null);
        assertEquals(QueryIntent.SUBSYSTEM_STATUS, resolved.getIntent());
        assertEquals("cctv", resolved.getTargetSystem());
        assertEquals("systemStatus", resolved.getTargetAttribute());
    }

    @Test
    void shouldResolveCctvLogStatusToSubsystemFieldQuery() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni CCTV log status right now?", snapshots, null);
        assertEquals(QueryIntent.SUBSYSTEM_STATUS, resolved.getIntent());
        assertEquals("cctv", resolved.getTargetSystem());
        assertEquals("logStatus", resolved.getTargetAttribute());
    }

    @Test
    void shouldResolveIasPowerStatusToSubsystemFieldQuery() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni IAS power status right now?", snapshots, null);
        assertEquals(QueryIntent.SUBSYSTEM_STATUS, resolved.getIntent());
        assertEquals("ias", resolved.getTargetSystem());
        assertEquals("powerStatus", resolved.getTargetAttribute());
    }

    @Test
    void shouldResolveIasSystemStatusToSubsystemFieldQuery() {
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni IAS system status right now?", snapshots, null);
        assertEquals(QueryIntent.SUBSYSTEM_STATUS, resolved.getIntent());
        assertEquals("ias", resolved.getTargetSystem());
        assertEquals("systemStatus", resolved.getTargetAttribute());
    }

    @Test
    void shouldKeepGatewayPowerStatusOutOfSubsystemFieldQuery() {
        // "Gateway" is not a subsystem keyword, so the gateway keeps its dedicated POWER_STATUS answer.
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni Gateway power status right now?", snapshots, null);
        assertEquals(QueryIntent.POWER_STATUS, resolved.getIntent());
    }

    @Test
    void shouldReportSubsystemRollupWhenNoFieldNamed() {
        // No power/system/log/health word -> roll-up status, not a field-level query.
        ResolvedQuery resolved = resolver.resolve("What is the Branch dankuni IAS status right now?", snapshots, null);
        assertEquals(QueryIntent.SUBSYSTEM_STATUS, resolved.getIntent());
        assertEquals("ias", resolved.getTargetSystem());
        assertEquals(null, resolved.getTargetAttribute());
    }

    @Test
    void shouldResolveImeiQuestion() {
        ResolvedQuery resolved = resolver.resolve("What is the IMEI of Bally Bazar?", snapshots, null);
        assertEquals(QueryIntent.DEVICE_IMEI, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveCctvModelQuestionToDeviceInfo() {
        ResolvedQuery resolved = resolver.resolve("Show model details for Bally Bazar", snapshots, null);
        assertEquals(QueryIntent.CCTV_DEVICE_INFO, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveCctvResolutionQuestionToDeviceInfo() {
        ResolvedQuery resolved = resolver.resolve("What CCTV resolution does Bally Bazar have?", snapshots, null);
        assertEquals(QueryIntent.CCTV_DEVICE_INFO, resolved.getIntent());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveCctvStorageQuestionToDeviceInfo() {
        ResolvedQuery resolved = resolver.resolve("Show CCTV storage capacity for Bally Bazar", snapshots, null);
        assertEquals(QueryIntent.CCTV_DEVICE_INFO, resolved.getIntent());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveCctvVendorQuestionToDeviceInfo() {
        ResolvedQuery resolved = resolver.resolve("Which vendor is the NVR at Bally Bazar?", snapshots, null);
        assertEquals(QueryIntent.CCTV_DEVICE_INFO, resolved.getIntent());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveGpsLocationQuestion() {
        ResolvedQuery resolved = resolver.resolve("What is the GPS location of Bally Bazar?", snapshots, null);
        assertEquals(QueryIntent.GPS_LOCATION, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveNetworkOperatorQuestion() {
        ResolvedQuery resolved = resolver.resolve("Which SIM operator does Bally Bazar use?", snapshots, null);
        assertEquals(QueryIntent.NETWORK_OPERATOR, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldKeepPlainNetworkQuestionAsNetworkStatus() {
        ResolvedQuery resolved = resolver.resolve("What is the network status of Bally Bazar?", snapshots, null);
        assertEquals(QueryIntent.NETWORK_STATUS, resolved.getIntent());
    }

    @Test
    void shouldResolveLastReportedQuestion() {
        ResolvedQuery resolved = resolver.resolve("When was the last update from Bally Bazar?", snapshots, null);
        assertEquals(QueryIntent.LAST_REPORTED, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldResolveNvrHddSlotsQuestionToDeviceInfo() {
        ResolvedQuery resolved = resolver.resolve("How many HDD slots does the NVR in Bally Bazar have?", snapshots, null);
        assertEquals(QueryIntent.CCTV_DEVICE_INFO, resolved.getIntent());
    }

    @Test
    void shouldKeepCctvHddInfoOutOfDeviceInfo() {
        // "cctv hdd info" must still reach CCTV_HDD_INFO, not the broadened inventory intent.
        ResolvedQuery resolved = resolver.resolve("Show CCTV HDD info for Bally Bazar", snapshots, null);
        assertEquals(QueryIntent.CCTV_HDD_INFO, resolved.getIntent());
    }

    // ---- Phase 3: hierarchy navigation ----

    @Test
    void shouldResolveListNbgsToHierarchyList() {
        ResolvedQuery resolved = resolver.resolve("List all NBGs", snapshots, null);
        assertEquals(QueryIntent.HIERARCHY_LIST_NODES, resolved.getIntent());
        assertEquals(false, resolved.isGlobal());
        assertEquals(false, resolved.isAmbiguous());
    }

    @Test
    void shouldResolveHowManyZonesToHierarchyList() {
        ResolvedQuery resolved = resolver.resolve("How many zones are there?", snapshots, null);
        assertEquals(QueryIntent.HIERARCHY_LIST_NODES, resolved.getIntent());
        assertEquals(false, resolved.isAmbiguous());
    }

    @Test
    void shouldResolveBranchesUnderZoneToHierarchyBranchesUnder() {
        ResolvedQuery resolved = resolver.resolve("Show branches under ZO HOWRAH", snapshots, null);
        assertEquals(QueryIntent.HIERARCHY_BRANCHES_UNDER, resolved.getIntent());
        assertEquals(false, resolved.isGlobal());
    }

    @Test
    void shouldResolveOwningNbgToHierarchyOwner() {
        ResolvedQuery resolved = resolver.resolve("Which NBG owns Bally Bazar?", snapshots, null);
        assertEquals(QueryIntent.HIERARCHY_OWNER, resolved.getIntent());
        assertNotNull(resolved.getTargetBranch());
        assertEquals("BOI-BALLYBAZAR", resolved.getTargetBranch().getIdentity().getTechnicalId());
    }

    @Test
    void shouldKeepListAllBranchesAsGlobalOverview() {
        // No NBG/zone keyword -> not a hierarchy question; stays the global branch overview.
        ResolvedQuery resolved = resolver.resolve("List all branches", snapshots, null);
        assertEquals(QueryIntent.GLOBAL_OVERVIEW, resolved.getIntent());
        assertEquals(true, resolved.isGlobal());
    }

    // ---- Phase 4 (H): category health ----

    @Test
    void shouldResolveGatewayHealthPercentageToCategoryHealth() {
        ResolvedQuery resolved = resolver.resolve("What percentage of Gateway devices are healthy?", snapshots, null);
        assertEquals(QueryIntent.CATEGORY_HEALTH, resolved.getIntent());
        assertEquals(false, resolved.isGlobal());
        assertEquals(false, resolved.isAmbiguous());
    }

    @Test
    void shouldResolveWhichCategoryMostInactiveToCategoryHealth() {
        ResolvedQuery resolved = resolver.resolve("Which category has the most inactive devices?", snapshots, null);
        assertEquals(QueryIntent.CATEGORY_HEALTH, resolved.getIntent());
    }

    @Test
    void shouldNotTreatBranchBatteryHealthAsCategoryHealth() {
        // A branch-scoped health question must stay BATTERY_HEALTH, not the fleet category breakdown.
        ResolvedQuery resolved = resolver.resolve("What is the battery health of Tarakeshwar?", snapshots, null);
        assertEquals(QueryIntent.BATTERY_HEALTH, resolved.getIntent());
    }

    // ---- Phase 4 (F): ranking & compare ----

    @Test
    void shouldResolveTopBranchesToRanking() {
        ResolvedQuery resolved = resolver.resolve("Show the top 5 branches by alarm count", snapshots, null);
        assertEquals(QueryIntent.BRANCH_RANKING, resolved.getIntent());
        assertEquals(false, resolved.isAmbiguous());
    }

    @Test
    void shouldResolveWhichBranchMostCamerasToRanking() {
        ResolvedQuery resolved = resolver.resolve("Which branch has the most online cameras?", snapshots, null);
        assertEquals(QueryIntent.BRANCH_RANKING, resolved.getIntent());
    }

    @Test
    void shouldResolveCompareToBranchCompare() {
        ResolvedQuery resolved = resolver.resolve("Compare Bally Bazar and Chandannagar", snapshots, null);
        assertEquals(QueryIntent.BRANCH_COMPARE, resolved.getIntent());
    }

    @Test
    void shouldNotRouteNbgHealthScoreRankingToBranchRanking() {
        // No "branch" + an unavailable metric -> not branch ranking; leaves the honest-decline path.
        ResolvedQuery resolved = resolver.resolve("Rank NBGs by health score", snapshots, null);
        assertNotEquals(QueryIntent.BRANCH_RANKING, resolved.getIntent());
    }

    // ---- Phase 4 (G): multi-condition filter ----

    @Test
    void shouldResolveWhereConditionToBranchFilter() {
        ResolvedQuery resolved = resolver.resolve("Show branches where Gateway is offline but IAS is online", snapshots, null);
        assertEquals(QueryIntent.BRANCH_FILTER, resolved.getIntent());
        assertEquals(false, resolved.isAmbiguous());
    }

    @Test
    void shouldResolveMissingSystemsToBranchFilter() {
        ResolvedQuery resolved = resolver.resolve("Show branches missing CCTV and ACS", snapshots, null);
        assertEquals(QueryIntent.BRANCH_FILTER, resolved.getIntent());
    }

    @Test
    void shouldResolveActiveSystemCountToBranchFilter() {
        ResolvedQuery resolved = resolver.resolve("Show branches with at least 3 active systems", snapshots, null);
        assertEquals(QueryIntent.BRANCH_FILTER, resolved.getIntent());
    }

    // ---- audit #20: "AC" must match as a whole word, not a substring ----

    @Test
    void acVoltageMatchesWholeWord() {
        ResolvedQuery resolved = resolver.resolve("What is the ac voltage of Tarakeshwar?", snapshots, null);
        assertEquals(QueryIntent.AC_VOLTAGE, resolved.getIntent());
    }

    @Test
    void acDoesNotMisfireOnEmbeddedSubstring() {
        // "facade" contains "ac" but must not resolve to AC_VOLTAGE.
        ResolvedQuery resolved = resolver.resolve("battery voltage near the facade", snapshots, null);
        assertEquals(QueryIntent.BATTERY_VOLTAGE, resolved.getIntent());
    }

    @Test
    void containsWordHelper() {
        org.junit.jupiter.api.Assertions.assertTrue(QueryIntentResolver.containsWord("AC VOLT", "AC"));
        org.junit.jupiter.api.Assertions.assertTrue(QueryIntentResolver.containsWord("AC-VOLT", "AC"));
        org.junit.jupiter.api.Assertions.assertFalse(QueryIntentResolver.containsWord("FACADE VOLT", "AC"));
        org.junit.jupiter.api.Assertions.assertFalse(QueryIntentResolver.containsWord("HVAC VOLT", "AC"));
    }
}
