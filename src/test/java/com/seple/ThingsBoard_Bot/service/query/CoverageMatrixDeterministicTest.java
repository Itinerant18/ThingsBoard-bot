package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.*;

/**
 * JUnit tests asserting that deterministic answers match the expected answers 
 * or their key clauses in docs/coverage-matrix.md.
 * Grouped by intent. GENERAL_LLM rows are skipped as manual-only.
 */
class CoverageMatrixDeterministicTest {

    private DeterministicAnswerService answerService;

    @BeforeEach
    void setUp() {
        answerService = new DeterministicAnswerService(new AnswerTemplateService());
    }

    private BranchSnapshot createBaseBranch(String display, String techId) {
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder()
                        .branchName(display)
                        .technicalId(techId)
                        .build())
                .gateway(GatewayStatus.builder().state(NormalizedState.ONLINE).build())
                .power(PowerStatus.builder()
                        .batteryVoltage(13.6)
                        .acVoltage(220.0)
                        .systemCurrent(2.0)
                        .batteryLow(false)
                        .mainsOn(true)
                        .build())
                .subsystems(BranchSubsystems.builder()
                        .cctv(SubsystemStatus.builder().systemName("CCTV DVR").installed(true).state(NormalizedState.ONLINE).build())
                        .ias(SubsystemStatus.builder().systemName("IAS Panel").installed(true).state(NormalizedState.ONLINE).build())
                        .bas(SubsystemStatus.builder().systemName("BAS Panel").installed(true).state(NormalizedState.ONLINE).build())
                        .fas(SubsystemStatus.builder().systemName("FAS Panel").installed(true).state(NormalizedState.ONLINE).build())
                        .timeLock(SubsystemStatus.builder().systemName("Time Lock").installed(true).state(NormalizedState.ONLINE).build())
                        .accessControl(SubsystemStatus.builder().systemName("Access Control Controller").installed(true).state(NormalizedState.ONLINE).build())
                        .build())
                .cctv(CctvStatus.builder()
                        .state(NormalizedState.ONLINE)
                        .cameraCount(8)
                        .onlineCameraCount(8)
                        .hasDisconnect(false)
                        .hddStatus("HEALTHY")
                        .build())
                .alerts(AlertSummary.builder()
                        .alarmCount(0)
                        .errorCount(0)
                        .build())
                .rawData(new HashMap<>())
                .build();
    }

    private ResolvedQuery createQuery(QueryIntent intent, BranchSnapshot targetBranch) {
        return ResolvedQuery.builder()
                .intent(intent)
                .targetBranch(targetBranch)
                .deterministic(true)
                .confidence(1.0)
                .build();
    }

    private ResolvedQuery createQueryWithSystem(QueryIntent intent, BranchSnapshot targetBranch, String targetSystem) {
        return ResolvedQuery.builder()
                .intent(intent)
                .targetBranch(targetBranch)
                .targetSystem(targetSystem)
                .deterministic(true)
                .confidence(1.0)
                .build();
    }

    // ==========================================
    // GATEWAY_STATUS
    // ==========================================

    @Test
    void testGatewayStatus_Online() {
        BranchSnapshot branch = createBaseBranch("BRANCH BALLY BAZAR", "BOI-BALLYBAZAR");
        branch.getGateway().setState(NormalizedState.ONLINE);

        ResolvedQuery query = createQuery(QueryIntent.GATEWAY_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Gateway status"));
        assertTrue(answer.contains("ONLINE"));
    }

    @Test
    void testGatewayStatus_Offline() {
        BranchSnapshot branch = createBaseBranch("BRANCH BALLY BAZAR", "BOI-BALLYBAZAR");
        branch.getGateway().setState(NormalizedState.OFFLINE);

        ResolvedQuery query = createQuery(QueryIntent.GATEWAY_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Gateway status"));
        assertTrue(answer.contains("OFFLINE"));
    }

    // ==========================================
    // BATTERY_VOLTAGE
    // ==========================================

    @Test
    void testBatteryVoltage_Present() {
        BranchSnapshot branch = createBaseBranch("BRANCH CHANDANNAGAR", "BOI-CHANDANNAGAR");
        branch.getPower().setBatteryVoltage(13.6);

        ResolvedQuery query = createQuery(QueryIntent.BATTERY_VOLTAGE, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Battery Voltage Reading"));
        assertTrue(answer.contains("13.6V DC"));
    }

    @Test
    void testBatteryVoltage_Null() {
        BranchSnapshot branch = createBaseBranch("BRANCH CHANDANNAGAR", "BOI-CHANDANNAGAR");
        branch.getPower().setBatteryVoltage(null);

        ResolvedQuery query = createQuery(QueryIntent.BATTERY_VOLTAGE, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Battery Voltage Reading"));
        assertTrue(answer.contains("N/A"));
    }

    // ==========================================
    // AC_VOLTAGE
    // ==========================================

    @Test
    void testAcVoltage_Present() {
        BranchSnapshot branch = createBaseBranch("BRANCH LILUAH", "BOI-LILUAH");
        branch.getPower().setAcVoltage(220.0);

        ResolvedQuery query = createQuery(QueryIntent.AC_VOLTAGE, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("AC Input Voltage"));
        assertTrue(answer.contains("220V AC"));
    }

    @Test
    void testAcVoltage_Null() {
        BranchSnapshot branch = createBaseBranch("BRANCH LILUAH", "BOI-LILUAH");
        branch.getPower().setAcVoltage(null);

        ResolvedQuery query = createQuery(QueryIntent.AC_VOLTAGE, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("AC Input Voltage"));
        assertTrue(answer.contains("N/A"));
    }

    // ==========================================
    // SYSTEM_CURRENT
    // ==========================================

    @Test
    void testSystemCurrent_Present() {
        BranchSnapshot branch = createBaseBranch("BRANCH TARAKESHWAR", "BOI-TARAKESHWAR");
        branch.getPower().setSystemCurrent(2.0);

        ResolvedQuery query = createQuery(QueryIntent.SYSTEM_CURRENT, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("System Current"));
        assertTrue(answer.contains("2 Amp"));
    }

    // ==========================================
    // BATTERY_LOW_STATUS
    // ==========================================

    @Test
    void testBatteryLowStatus_Normal() {
        BranchSnapshot branch = createBaseBranch("BRANCH TARAKESHWAR", "BOI-TARAKESHWAR");
        branch.getRawData().put("BATTERY LOW", "false");

        ResolvedQuery query = createQuery(QueryIntent.BATTERY_LOW_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Battery Low Status"));
        assertTrue(answer.contains("NORMAL"));
    }

    @Test
    void testBatteryLowStatus_Warning() {
        BranchSnapshot branch = createBaseBranch("BRANCH TARAKESHWAR", "BOI-TARAKESHWAR");
        branch.getRawData().put("BATTERY LOW", "true");

        ResolvedQuery query = createQuery(QueryIntent.BATTERY_LOW_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Battery Low Status"));
        assertTrue(answer.contains("WARNING ACTIVE"));
    }

    // ==========================================
    // BATTERY_HEALTH
    // ==========================================

    @Test
    void testBatteryHealth_Healthy() {
        BranchSnapshot branch = createBaseBranch("BRANCH LILUAH", "BOI-LILUAH");
        branch.getPower().setBatteryVoltage(13.6);

        ResolvedQuery query = createQuery(QueryIntent.BATTERY_HEALTH, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Battery Status"));
        assertTrue(answer.contains("HEALTHY"));
        assertTrue(answer.contains("13.6V DC"));
    }

    @Test
    void testBatteryHealth_Null() {
        BranchSnapshot branch = createBaseBranch("BRANCH LILUAH", "BOI-LILUAH");
        branch.getPower().setBatteryVoltage(null);

        ResolvedQuery query = createQuery(QueryIntent.BATTERY_HEALTH, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Battery Status"));
        assertTrue(answer.contains("not available"));
    }

    // ==========================================
    // ACTIVE_DEVICES
    // ==========================================

    @Test
    void testActiveDevices() {
        BranchSnapshot branch = createBaseBranch("BRANCH BALLY BAZAR", "BOI-BALLYBAZAR");
        // By default, createBaseBranch sets all 6 subsystems to ONLINE.
        // Let's set some to OFFLINE to reduce the count of active devices.
        branch.getSubsystems().getBas().setState(NormalizedState.OFFLINE);
        branch.getSubsystems().getTimeLock().setState(NormalizedState.OFFLINE);

        ResolvedQuery query = createQuery(QueryIntent.ACTIVE_DEVICES, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Active Devices (4)"));
        assertTrue(answer.contains("CCTV DVR"));
        assertTrue(answer.contains("IAS Panel"));
        assertTrue(answer.contains("FAS Panel"));
        assertTrue(answer.contains("Access Control Controller"));
        assertFalse(answer.contains("BAS Panel"));
        assertFalse(answer.contains("Time Lock"));
    }

    // ==========================================
    // FAULT_DEVICES
    // ==========================================

    @Test
    void testFaultDevices_Active() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getSubsystems().getBas().setState(NormalizedState.FAULT);

        ResolvedQuery query = createQuery(QueryIntent.FAULT_DEVICES, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Fault Devices (1)"));
        assertTrue(answer.contains("BAS Panel"));
    }

    @Test
    void testFaultDevices_BranchLevel() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        // No subsystem in FAULT state, but raw telemetry has a fault indicator
        branch.getRawData().put("ticketStatus_FAS_FAULT", "true");

        ResolvedQuery query = createQuery(QueryIntent.FAULT_DEVICES, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("no specific fault device"));
        assertTrue(answer.contains("Fire Alarm Fault"));
    }

    @Test
    void testFaultDevices_None() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");

        ResolvedQuery query = createQuery(QueryIntent.FAULT_DEVICES, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("no fault devices are currently identified"));
    }

    // ==========================================
    // OFFLINE_DEVICES
    // ==========================================

    @Test
    void testOfflineDevices_Active() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getSubsystems().getTimeLock().setState(NormalizedState.OFFLINE);

        ResolvedQuery query = createQuery(QueryIntent.OFFLINE_DEVICES, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Offline Devices (1)"));
        assertTrue(answer.contains("Time Lock"));
    }

    @Test
    void testOfflineDevices_None() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");

        ResolvedQuery query = createQuery(QueryIntent.OFFLINE_DEVICES, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("no offline devices are currently identified"));
    }

    // ==========================================
    // CONNECTED_DEVICES
    // ==========================================

    @Test
    void testConnectedDevices() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        // By default, createBaseBranch sets all 6 subsystems to installed=true.

        ResolvedQuery query = createQuery(QueryIntent.CONNECTED_DEVICES, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Connected Devices (6)"));
        assertTrue(answer.contains("CCTV DVR"));
        assertTrue(answer.contains("IAS Panel"));
        assertTrue(answer.contains("BAS Panel"));
        assertTrue(answer.contains("FAS Panel"));
        assertTrue(answer.contains("Time Lock"));
        assertTrue(answer.contains("Access Control Controller"));
    }

    // ==========================================
    // POWER_STATUS
    // ==========================================

    @Test
    void testPowerStatus_MainsOn() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getPower().setMainsOn(true);
        branch.getPower().setAcVoltage(220.0);
        branch.getPower().setBatteryVoltage(13.6);

        ResolvedQuery query = createQuery(QueryIntent.POWER_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Power Status is ON"));
        assertTrue(answer.contains("AC Mains: 220V AC"));
        assertTrue(answer.contains("Battery Backup: 13.6V DC"));
    }

    @Test
    void testPowerStatus_MainsOff() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getPower().setMainsOn(false);
        branch.getPower().setAcVoltage(0.0);
        branch.getPower().setBatteryVoltage(12.5);

        ResolvedQuery query = createQuery(QueryIntent.POWER_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Power Status is OFF"));
        assertTrue(answer.contains("AC Mains: Offline"));
        assertTrue(answer.contains("Battery Backup: 12.5V DC"));
    }

    // ==========================================
    // NETWORK_STATUS
    // ==========================================

    @Test
    void testNetworkStatus_OnWithOperator() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("NETWORK", "true");
        branch.getRawData().put("networkOperator", "Airtel");

        ResolvedQuery query = createQuery(QueryIntent.NETWORK_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Network Status is ON"));
        assertTrue(answer.contains("Network Operator: Airtel"));
    }

    @Test
    void testNetworkStatus_Offline() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("NETWORK", "false");

        ResolvedQuery query = createQuery(QueryIntent.NETWORK_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Network Status is OFFLINE"));
    }

    // ==========================================
    // ALARM_STATUS
    // ==========================================

    @Test
    void testAlarmStatus() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getAlerts().setAlarmCount(2);

        ResolvedQuery query = createQuery(QueryIntent.ALARM_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Alarm Count is 2"));
    }

    // ==========================================
    // ERROR_STATUS
    // ==========================================

    @Test
    void testErrorStatus() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getAlerts().setErrorCount(1);

        ResolvedQuery query = createQuery(QueryIntent.ERROR_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Error Count is 1"));
    }

    // ==========================================
    // CCTV_STATUS
    // ==========================================

    @Test
    void testCctvStatus() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getCctv().setOnlineCameraCount(8);
        branch.getCctv().setCameraCount(8);

        ResolvedQuery query = createQuery(QueryIntent.CCTV_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("CCTV Camera Status is 8 of 8 cameras ONLINE"));
    }

    // ==========================================
    // CCTV_HDD_ERROR_STATUS
    // ==========================================

    @Test
    void testCctvHddErrorStatus_Healthy() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("HDD ERROR", "false");

        ResolvedQuery query = createQuery(QueryIntent.CCTV_HDD_ERROR_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("CCTV HDD Error Status is NORMAL"));
    }

    @Test
    void testCctvHddErrorStatus_Error() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("HDD ERROR", "true");

        ResolvedQuery query = createQuery(QueryIntent.CCTV_HDD_ERROR_STATUS, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("CCTV HDD Error Status is ACTIVE"));
    }

    // ==========================================
    // CCTV_HDD_INFO
    // ==========================================

    @Test
    void testCctvHddInfo() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        String hddJson = "[{\"HDDSlots\":\"1\",\"HDDStatus\":\"HEALTHY\",\"HDDcapacity\":\"4\",\"HDDfreeSpace\":\"1\"}]";
        branch.getRawData().put("rock_HddINFO", hddJson);

        ResolvedQuery query = createQuery(QueryIntent.CCTV_HDD_INFO, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("CCTV HDD Information is: Slot 1: HEALTHY, Capacity 4 TB, Free 1 TB"));
    }

    // ==========================================
    // CCTV_RECORDING_INFO
    // ==========================================

    @Test
    void testCctvRecordingInfo() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        // total_duration is recorded days (same unit as total_recording_days): ch1 compliant, ch2 zero.
        String recordingJson = "[{\"channel_no\":\"1\",\"total_duration\":120},{\"channel_no\":\"2\",\"total_duration\":0}]";
        branch.getRawData().put("rock_VIDEOdETAILS", recordingJson);

        ResolvedQuery query = createQuery(QueryIntent.CCTV_RECORDING_INFO, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("2 camera(s)"), answer);
        assertTrue(answer.contains("1 compliant"), answer);
        assertTrue(answer.contains("0 days") && answer.contains("channel(s) 2"), answer);
    }

    // ==========================================
    // CAMERA_DISCONNECT_HISTORY
    // ==========================================

    @Test
    void testCameraDisconnectHistory() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("cameraDisconnectCH3_history", "[{\"event\":\"offline\"}]");

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.CAMERA_DISCONNECT_HISTORY)
                .targetBranch(branch)
                .originalQuestion("cctv disconnect history")
                .deterministic(true)
                .build();

        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Historical camera disconnects found: Channel 3"));
    }

    // ==========================================
    // SUBSYSTEM_STATUS
    // ==========================================

    @Test
    void testSubsystemStatus() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getSubsystems().getIas().setState(NormalizedState.ONLINE);

        ResolvedQuery query = createQueryWithSystem(QueryIntent.SUBSYSTEM_STATUS, branch, "ias");
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("IAS Panel Status is ONLINE"));
    }

    // ==========================================
    // SUBSYSTEM_FAULT_STATUS
    // ==========================================

    @Test
    void testSubsystemFaultStatus() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("intrusion_alarm_system_fault", "true");

        ResolvedQuery query = createQueryWithSystem(QueryIntent.SUBSYSTEM_FAULT_STATUS, branch, "ias");
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("IAS Panel Fault Status is ACTIVE"));
    }

    // ==========================================
    // SUBSYSTEM_ALARM_STATUS
    // ==========================================

    @Test
    void testSubsystemAlarmStatus() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("ticketStatus_IAS_ACTIVATE", "true");

        ResolvedQuery query = createQueryWithSystem(QueryIntent.SUBSYSTEM_ALARM_STATUS, branch, "ias");
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("IAS Panel Alarm Status is ACTIVE"));
    }

    // ==========================================
    // DOOR_STATUS
    // ==========================================

    @Test
    void testDoorStatus() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("accessControlDoor", "open");

        ResolvedQuery query = createQueryWithSystem(QueryIntent.DOOR_STATUS, branch, "accessControl");
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Access Control Door Status is OPEN"));
    }

    // ==========================================
    // ACCESS_CONTROL_USER_COUNT
    // ==========================================

    @Test
    void testAccessControlUserCount() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("registeredUsers", 142);

        ResolvedQuery query = createQuery(QueryIntent.ACCESS_CONTROL_USER_COUNT, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Access Control User Count is 142"));
    }

    // ==========================================
    // ACCESS_CONTROL_DEVICE_INFO
    // ==========================================

    @Test
    void testAccessControlDeviceInfo() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("accessControlModel", "BioModel");
        branch.getRawData().put("accessControlFirmware", "v1.2");
        branch.getRawData().put("accessControlIp", "192.168.1.100");

        ResolvedQuery query = createQuery(QueryIntent.ACCESS_CONTROL_DEVICE_INFO, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("Access Control Device Info is:"));
        assertTrue(answer.contains("Model: BioModel"));
        assertTrue(answer.contains("Firmware: v1.2"));
        assertTrue(answer.contains("IP: 192.168.1.100"));
    }

    // ==========================================
    // FAULT_REASON
    // ==========================================

    @Test
    void testFaultReason() {
        BranchSnapshot branch = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branch.getRawData().put("ticketStatus_HDD_ERROR", "true");

        ResolvedQuery query = createQuery(QueryIntent.FAULT_REASON, branch);
        String answer = answerService.answer(query, List.of(branch));

        assertNotNull(answer);
        assertTrue(answer.contains("fault indication because"));
        assertTrue(answer.contains("HDD error indicator is active"));
    }

    // ==========================================
    // GLOBAL_OVERVIEW
    // ==========================================

    @Test
    void testGlobalOverview() {
        BranchSnapshot branchA = createBaseBranch("BRANCH A", "BOI-BRANCHA");
        branchA.getGateway().setState(NormalizedState.ONLINE);
        
        BranchSnapshot branchB = createBaseBranch("BRANCH B", "BOI-BRANCHB");
        branchB.getGateway().setState(NormalizedState.OFFLINE);

        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.GLOBAL_OVERVIEW)
                .global(true)
                .deterministic(true)
                .build();

        String answer = answerService.answer(query, List.of(branchA, branchB));

        assertNotNull(answer);
        assertTrue(answer.contains("**Total:"));
    }
}
