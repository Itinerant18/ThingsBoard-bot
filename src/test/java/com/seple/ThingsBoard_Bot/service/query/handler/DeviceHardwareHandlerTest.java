package com.seple.ThingsBoard_Bot.service.query.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchIdentity;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.HardwareHealth;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** DEVICE_HARDWARE: gateway host metrics (cpu/mem/disk/temp) and firmware version. */
class DeviceHardwareHandlerTest {

    private final DeviceHardwareHandler handler = new DeviceHardwareHandler(new AnswerSupport());

    private BranchSnapshot branch(String name) {
        return BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName(name).build())
                .hardware(HardwareHealth.builder().cpu(7.8).memory(17.1).disk(77.4).temperature(57.4).build())
                .rawData(Map.of("target_sw_version", "7", "sw_state", "INITIATED"))
                .build();
    }

    @Test
    void singleBranchReportsHardwareMetrics() {
        BranchSnapshot b = branch("BRANCH A");
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.DEVICE_HARDWARE).targetBranch(b)
                .originalQuestion("what is the CPU and disk of BRANCH A").build();

        String answer = handler.handle(q, List.of(b), "BOI");

        assertTrue(answer.contains("77.4%"), answer);   // disk
        assertTrue(answer.contains("57.4"), answer);     // temperature
        assertTrue(answer.contains("CPU"), answer);
    }

    @Test
    void firmwareQuestionReportsVersion() {
        BranchSnapshot b = branch("BRANCH A");
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.DEVICE_HARDWARE).targetBranch(b)
                .originalQuestion("current firmware version of BRANCH A").build();

        String answer = handler.handle(q, List.of(b), "BOI");

        assertTrue(answer.contains("v7"), answer);
        assertTrue(answer.contains("INITIATED"), answer); // OTA state
    }

    @Test
    void fleetHardwareSummarisesAndRanksDisk() {
        BranchSnapshot a = branch("BRANCH A"); // disk 77.4
        BranchSnapshot b = BranchSnapshot.builder()
                .identity(BranchIdentity.builder().branchName("BRANCH B").build())
                .hardware(HardwareHealth.builder().disk(90.0).build())
                .rawData(Map.of()).build();
        ResolvedQuery q = ResolvedQuery.builder()
                .intent(QueryIntent.DEVICE_HARDWARE)
                .originalQuestion("hardware health across all devices").build();

        String answer = handler.handle(q, List.of(a, b), "BOI");

        assertTrue(answer.contains("Avg disk"), answer);
        assertTrue(answer.contains("Highest disk"), answer);
        assertTrue(answer.contains("90.0%"), answer); // top disk branch surfaced
    }
}
