package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.FullDataPayloadParser;
import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;
import com.seple.ThingsBoard_Bot.support.FixtureLoader;

class DeterministicAnswerServiceTest {

    private DeterministicAnswerService answerService;
    private List<BranchSnapshot> snapshots;

    @BeforeEach
    void setUp() throws Exception {
        answerService = new DeterministicAnswerService(new AnswerTemplateService());
        FullDataPayloadParser parser = new FullDataPayloadParser();
        BranchSnapshotMapper mapper = new BranchSnapshotMapper(
                new FieldPrecedenceResolver(new ValueNormalizer()), new ValueNormalizer());

        String json = FixtureLoader.load("fixtures/full_data_fixture.json");
        snapshots = parser.parse(json).branches().values().stream()
                .map(mapper::map)
                .collect(Collectors.toList());
    }

    @Test
    void shouldGenerateGlobalOverviewWithoutLlm() {
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.GLOBAL_OVERVIEW)
                .originalQuestion("List all branches")
                .global(true)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("**Total:"));
        assertTrue(answer.contains("Online:"));
    }

    @Test
    void shouldGenerateCameraStatusFromStructuredSnapshot() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-LILUAH".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_STATUS)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("12"));
        assertTrue(answer.contains("ONLINE"));
    }

    @Test
    void shouldExplainTrendzFaultReason() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "Trendz_Testing_Device".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.FAULT_REASON)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("fault indication"));
        assertTrue(answer.contains("fire alarm fault indicator"));
    }

    @Test
    void shouldReturnNoHistoricalDisconnectsWhenHistoryArraysAreEmpty() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-CHANDANNAGAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CAMERA_DISCONNECT_HISTORY)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("No historical camera disconnects found"));
    }

    @Test
    void shouldUseValidCameraObjectsOnlyForTarakeshwarTotals() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-TARAKESHWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_STATUS)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("14 of 15"));
    }

    @Test
    void shouldReturnGatewayStatusForCurrentStatusQuestionInsteadOfSystemCurrent() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-CHANDANNAGAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.GATEWAY_STATUS)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("Gateway status"));
        assertTrue(answer.contains("ONLINE"));
    }

    @Test
    void shouldReturnActiveDevicesInsteadOfSystemCurrentForCurrentlyActivePrompt() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-BALLYBAZAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.ACTIVE_DEVICES)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("CCTV DVR"));
        assertTrue(answer.contains("IAS Panel"));
    }

    @Test
    void shouldReturnBatteryLowStatus() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-TARAKESHWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.BATTERY_LOW_STATUS)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("Battery Low Status"));
        assertTrue(answer.contains("NORMAL"));
    }

    @Test
    void shouldReturnOfflineDevicesForBhadreswar() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-BHADRESWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.OFFLINE_DEVICES)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("Offline Devices"));
        assertTrue(answer.contains("Time Lock"));
    }

    @Test
    void shouldReturnConnectedDevicesForTarakeshwar() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-TARAKESHWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CONNECTED_DEVICES)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("Connected Devices"));
        assertTrue(answer.contains("CCTV DVR"));
        assertTrue(answer.contains("Time Lock"));
    }

    @Test
    void shouldReturnGroundedNetworkStatus() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-TARAKESHWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.NETWORK_STATUS)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("the Network Status is ON"));
    }

    @Test
    void shouldReturnCctvHddInformation() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-CHANDANNAGAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_HDD_INFO)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("CCTV HDD Information"));
        assertTrue(answer.contains("Slot 1"));
        assertTrue(answer.contains("Slot 4"));
    }

    @Test
    void shouldReturnCctvRecordingInformation() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-CHANDANNAGAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_RECORDING_INFO)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("CCTV Recording Information"));
        assertTrue(answer.contains("12 channel(s)"));
    }

    @Test
    void shouldReturnTimeLockDoorStatus() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-TARAKESHWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.DOOR_STATUS)
                .targetBranch(target)
                .targetSystem("timeLock")
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("Time Lock Door Status"));
        assertTrue(answer.contains("CLOSE"));
    }

    @Test
    void shouldReturnUnavailableAccessControlUserCountInsteadOfStatusOnly() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "Trendz_Testing_Device".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.ACCESS_CONTROL_USER_COUNT)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("user count is not available"));
        assertTrue(answer.contains("ONLINE"));
    }

    @Test
    void shouldReturnUnavailableAccessControlDeviceInfoInsteadOfGenericStatus() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "Trendz_Testing_Device".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.ACCESS_CONTROL_DEVICE_INFO)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("device information is not available"));
        assertTrue(answer.contains("ONLINE"));
    }

    @Test
    void shouldReturnCctvHddErrorStatusInsteadOfCameraCount() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-DANKUNI".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_HDD_ERROR_STATUS)
                .targetBranch(target)
                .targetSystem("cctv")
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("CCTV HDD Error Status"));
        assertTrue(answer.contains("NORMAL"));
    }

    @Test
    void shouldReturnNotInstalledForTimeLockAlarmStatus() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-DANKUNI".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.SUBSYSTEM_ALARM_STATUS)
                .targetBranch(target)
                .targetSystem("timeLock")
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("Time Lock Alarm Status"));
        assertTrue(answer.contains("NOT INSTALLED"));
    }

    @Test
    void shouldReturnNotInstalledForAccessControlAlarmStatus() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-DANKUNI".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.SUBSYSTEM_ALARM_STATUS)
                .targetBranch(target)
                .targetSystem("accessControl")
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("Access Control Alarm Status"));
        assertTrue(answer.contains("NOT INSTALLED"));
    }

    @Test
    void shouldGroupOfflineCamerasByChannelRange() {
        AnswerTemplateService templateService = new AnswerTemplateService();
        BranchSnapshot branch = new BranchSnapshot();
        com.seple.ThingsBoard_Bot.model.domain.BranchIdentity identity = new com.seple.ThingsBoard_Bot.model.domain.BranchIdentity();
        identity.setBranchName("BRANCH BALLY BAZAR");
        branch.setIdentity(identity);
        
        java.util.Map<String, Object> rawData = new java.util.HashMap<>();
        rawData.put("rock_CAMERAdETAILS", "[" +
            "{\"channel_no\":\"1\",\"status\":\"Inactive\",\"Channel Name\":\"24080129_003352-VMDS\"}," +
            "{\"channel_no\":\"2\",\"status\":\"Inactive\",\"Channel Name\":\"YZWLY9ZSBX6MYX5TMD-LQ\"}," +
            "{\"channel_no\":\"5\",\"status\":\"Inactive\",\"Channel Name\":\"CP-UNC-VC21L5C-VMD-LQ\"}," +
            "{\"channel_no\":\"7\",\"status\":\"Inactive\",\"Channel Name\":\"CP-UNC-VC21L5C-VMD-LQ\"}," +
            "{\"channel_no\":\"8\",\"status\":\"Inactive\",\"Channel Name\":\"CP-UNC-VC21L5C-VMD-LQ\"}" +
        "]");
        branch.setRawData(rawData);

        String answer = templateService.renderCctvStatus(branch, 2, 7);
        assertTrue(answer.contains("Offline Cameras:"));
        assertTrue(answer.contains("Channel 1: 24080129_003352-VMDS"));
        assertTrue(answer.contains("Channel 2: YZWLY9ZSBX6MYX5TMD-LQ"));
        assertTrue(answer.contains("CP-UNC-VC21L5C-VMD-LQ (3 units: Channels 5, 7-8)"));
    }

    @Test
    void shouldGeneratePowerStatusFormat() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-TARAKESHWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.POWER_STATUS)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        // Expected format: **For Branch TARAKESHWAR, the Power Status is ON. AC Mains: 220V AC, Battery Backup: 13.6V DC.**
        assertTrue(answer.contains("For Branch TARAKESHWAR, the Power Status is"));
        assertTrue(answer.contains("AC Mains:"));
        assertTrue(answer.contains("Battery Backup:"));
    }

    @Test
    void shouldReturnDeviceImei() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-BALLYBAZAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.DEVICE_IMEI)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("device IMEI is"));
        assertTrue(answer.contains("358773400034245"));
    }

    @Test
    void shouldReportMissingImeiForSentinelValues() {
        // "0", "None", "null", "N/A" and an absent key all mean "not reported" -- never echo the sentinel.
        for (String sentinel : java.util.Arrays.asList("0", "None", "null", "N/A", "")) {
            BranchSnapshot branch = new BranchSnapshot();
            com.seple.ThingsBoard_Bot.model.domain.BranchIdentity identity =
                    new com.seple.ThingsBoard_Bot.model.domain.BranchIdentity();
            identity.setBranchName("BRANCH SAVEDI");
            branch.setIdentity(identity);
            java.util.Map<String, Object> raw = new java.util.HashMap<>();
            raw.put("imei_id_dev_id", sentinel);
            branch.setRawData(raw);

            ResolvedQuery query = ResolvedQuery.builder()
                    .intent(QueryIntent.DEVICE_IMEI)
                    .targetBranch(branch)
                    .deterministic(true)
                    .confidence(1.0)
                    .build();

            String answer = answerService.answer(query, List.of(branch));
            assertTrue(answer.contains("not reported (missing)"), "sentinel=[" + sentinel + "]");
        }
    }

    @Test
    void shouldReturnGpsCoordinatesWhenReported() {
        String answer = gpsAnswer("22.5726", "88.3639", null);
        assertTrue(answer.contains("22.5726, 88.3639"));
        assertTrue(answer.contains("latitude, longitude"));
    }

    @Test
    void shouldReportGpsDefaultWhenLatLonDefaultTrue() {
        String answer = gpsAnswer("20.5937", "78.9629", "true");
        assertTrue(answer.contains("default location"));
    }

    @Test
    void shouldReportGpsMissingWhenNoCoordinates() {
        String answer = gpsAnswer(null, null, null);
        assertTrue(answer.contains("not reported (missing)"));
    }

    @Test
    void shouldReturnLastReportedTimeWhenPresent() {
        BranchSnapshot branch = lastReportedBranch("2026-06-23T10:15:00Z", false);
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.LAST_REPORTED).targetBranch(branch).deterministic(true).confidence(1.0).build();
        String answer = answerService.answer(query, List.of(branch));
        assertTrue(answer.contains("last reported at 2026-06-23T10:15:00Z"));
        assertTrue(!answer.contains("stale"));
    }

    @Test
    void shouldFlagStaleLastReportedTime() {
        BranchSnapshot branch = lastReportedBranch("2026-06-10T08:00:00Z", true);
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.LAST_REPORTED).targetBranch(branch).deterministic(true).confidence(1.0).build();
        String answer = answerService.answer(query, List.of(branch));
        assertTrue(answer.contains("data is stale"));
    }

    @Test
    void shouldReportLastReportedUnavailableWhenAbsent() {
        BranchSnapshot branch = lastReportedBranch(null, false);
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.LAST_REPORTED).targetBranch(branch).deterministic(true).confidence(1.0).build();
        String answer = answerService.answer(query, List.of(branch));
        assertTrue(answer.contains("last-reported time is not available"));
    }

    @Test
    void shouldReturnNetworkOperatorFromStatusField() {
        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("system_status_statusbox_network", "AirTel");
        String answer = networkOperatorAnswer(raw);
        assertTrue(answer.contains("network operator (SIM) is AirTel"));
    }

    @Test
    void shouldReturnNetworkOperatorFromLogTypeDetailFallback() {
        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("log_type_operator_detail", "[{\"service_provider\":\"Jio\",\"operator_no\":40431}]");
        String answer = networkOperatorAnswer(raw);
        assertTrue(answer.contains("network operator (SIM) is Jio"));
    }

    @Test
    void shouldReportNetworkOperatorNotMapped() {
        String answer = networkOperatorAnswer(new java.util.HashMap<>());
        assertTrue(answer.contains("not mapped"));
    }

    private String networkOperatorAnswer(java.util.Map<String, Object> raw) {
        BranchSnapshot branch = new BranchSnapshot();
        com.seple.ThingsBoard_Bot.model.domain.BranchIdentity identity =
                new com.seple.ThingsBoard_Bot.model.domain.BranchIdentity();
        identity.setBranchName("BRANCH SERAMPORE");
        branch.setIdentity(identity);
        branch.setRawData(raw);
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.NETWORK_OPERATOR)
                .targetBranch(branch)
                .deterministic(true)
                .confidence(1.0)
                .build();
        return answerService.answer(query, List.of(branch));
    }

    private BranchSnapshot lastReportedBranch(String dataAsOf, boolean stale) {
        BranchSnapshot branch = new BranchSnapshot();
        com.seple.ThingsBoard_Bot.model.domain.BranchIdentity identity =
                new com.seple.ThingsBoard_Bot.model.domain.BranchIdentity();
        identity.setBranchName("BRANCH CHINSURAH");
        branch.setIdentity(identity);
        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        if (dataAsOf != null) {
            raw.put("data_as_of", dataAsOf);
            raw.put("data_stale", stale);
        }
        branch.setRawData(raw);
        return branch;
    }

    private String gpsAnswer(String lat, String lon, String latLonDefault) {
        BranchSnapshot branch = new BranchSnapshot();
        com.seple.ThingsBoard_Bot.model.domain.BranchIdentity identity =
                new com.seple.ThingsBoard_Bot.model.domain.BranchIdentity();
        identity.setBranchName("BRANCH CHINSURAH");
        branch.setIdentity(identity);
        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        if (lat != null) {
            raw.put("lat", lat);
        }
        if (lon != null) {
            raw.put("lon", lon);
        }
        if (latLonDefault != null) {
            raw.put("lat_lon_default", latLonDefault);
        }
        branch.setRawData(raw);

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.GPS_LOCATION)
                .targetBranch(branch)
                .deterministic(true)
                .confidence(1.0)
                .build();
        return answerService.answer(query, List.of(branch));
    }

    @Test
    void shouldReturnCctvDeviceInfoModel() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-BALLYBAZAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_DEVICE_INFO)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        assertTrue(answer.contains("CCTV Device Info"));
        assertTrue(answer.contains("DS-7716NI-K4"));
        assertTrue(answer.contains("HDD Slots: 4"));
    }

    @Test
    void shouldReturnCctvDeviceInfoVendorAndStorage() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-BALLYBAZAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_DEVICE_INFO)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        // Vendor inferred from the DS- model prefix (nvr_brand absent); storage from rock_capacity.
        assertTrue(answer.contains("Vendor: Hikvision"));
        assertTrue(answer.contains("Storage: 21.55 TB"));
    }

    @Test
    void shouldParseDahuaHddSchemaInHddInfo() {
        // Dahua/XVR uses HDDSlot/HDDCapacity/HDDFreeSpace (vs Hikvision HDDSlots/HDDcapacity/HDDfreeSpace).
        BranchSnapshot branch = new BranchSnapshot();
        com.seple.ThingsBoard_Bot.model.domain.BranchIdentity identity =
                new com.seple.ThingsBoard_Bot.model.domain.BranchIdentity();
        identity.setBranchName("BRANCH IMPHAL");
        branch.setIdentity(identity);

        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("rock_HddINFO",
                "[{\"HDDSlot\":\"1\",\"HDDStatus\":\"Idle\",\"HDDCapacity\":\"3.64\",\"HDDFreeSpace\":\"1.20\"}]");
        branch.setRawData(raw);

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CCTV_HDD_INFO)
                .targetBranch(branch)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, List.of(branch));

        assertTrue(answer.contains("CCTV HDD Information"));
        assertTrue(answer.contains("Slot 1"));
        assertTrue(answer.contains("Capacity 3.64 TB"));
        assertTrue(answer.contains("Free 1.20 TB"));
    }

    @Test
    void shouldGenerateBatteryHealthFormat() {
        BranchSnapshot target = snapshots.stream()
                .filter(snapshot -> "BOI-TARAKESHWAR".equals(snapshot.getIdentity().getTechnicalId()))
                .findFirst()
                .orElseThrow();

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.BATTERY_HEALTH)
                .targetBranch(target)
                .deterministic(true)
                .confidence(1.0)
                .build();

        String answer = answerService.answer(query, snapshots);

        // Expected format: **For Branch TARAKESHWAR, the Battery Status is HEALTHY. Voltage: 14V DC.**
        assertTrue(answer.contains("For Branch TARAKESHWAR, the Battery Status is HEALTHY. Voltage: 14V DC."));
    }
}
