package com.seple.ThingsBoard_Bot.service.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;
import com.seple.ThingsBoard_Bot.service.query.handler.AnswerSupport;
import com.seple.ThingsBoard_Bot.service.query.handler.SubsystemHandler;

/**
 * Field-level sub-device questions ("cctv power status", "ias system status") must answer from that
 * device's specific flattened serverAttribute (cctv_powerStatus, ias_systemStatus) verbatim — showing
 * "N/A" as-is — rather than from the device's roll-up status.
 */
class SubsystemFieldQueryTest {

    private SubsystemHandler handler;
    private BranchSnapshot branch;

    @BeforeEach
    void setUp() {
        BranchSnapshotMapper mapper = new BranchSnapshotMapper(
                new FieldPrecedenceResolver(new ValueNormalizer()), new ValueNormalizer());
        handler = new SubsystemHandler(new AnswerTemplateService(), new AnswerSupport());

        Map<String, Object> raw = new HashMap<>();
        raw.put("branchName", "CANARA-CHETLA");

        // IAS is installed with real per-field values.
        raw.put("ias_sts", "Online");
        raw.put("ias_powerStatus", "On");
        raw.put("ias_systemStatus", "Normal");
        raw.put("ias_logStatus", "Integrated Alarm System On");
        raw.put("ias_healthStatus", "Active");

        // CCTV reports N/A across the board — must be shown as-is.
        raw.put("cctv_sts", "N/A");
        raw.put("cctv_powerStatus", "N/A");
        raw.put("cctv_systemStatus", "N/A");
        raw.put("cctv_logStatus", "N/A");
        raw.put("cctv_healthStatus", "N/A");

        branch = mapper.map(raw);
    }

    private String answer(String targetSystem, String targetAttribute) {
        ResolvedQuery query = ResolvedQuery.builder()
                .intent(QueryIntent.SUBSYSTEM_STATUS)
                .targetBranch(branch)
                .targetSystem(targetSystem)
                .targetAttribute(targetAttribute)
                .deterministic(true)
                .confidence(1.0)
                .build();
        return handler.handle(query, List.of(branch), "CANARA");
    }

    @Test
    void answersIasPowerStatusFromTheField() {
        assertEquals("**For Branch CANARA-CHETLA, IAS Power Status is On.**", answer("ias", "powerStatus"));
    }

    @Test
    void answersIasSystemStatusFromTheField() {
        assertEquals("**For Branch CANARA-CHETLA, IAS System Status is Normal.**", answer("ias", "systemStatus"));
    }

    @Test
    void answersIasLogStatusFromTheField() {
        assertEquals("**For Branch CANARA-CHETLA, IAS Log Status is Integrated Alarm System On.**",
                answer("ias", "logStatus"));
    }

    @Test
    void answersIasHealthStatusFromTheField() {
        assertEquals("**For Branch CANARA-CHETLA, IAS Health Status is Active.**", answer("ias", "healthStatus"));
    }

    @Test
    void showsCctvPowerStatusNaAsIs() {
        assertEquals("**For Branch CANARA-CHETLA, CCTV Power Status is N/A.**", answer("cctv", "powerStatus"));
    }

    @Test
    void showsCctvSystemStatusNaAsIs() {
        assertEquals("**For Branch CANARA-CHETLA, CCTV System Status is N/A.**", answer("cctv", "systemStatus"));
    }

    @Test
    void showsCctvLogStatusNaAsIs() {
        assertEquals("**For Branch CANARA-CHETLA, CCTV Log Status is N/A.**", answer("cctv", "logStatus"));
    }

    @Test
    void fallsBackToRollupWhenNoFieldNamed() {
        // No targetAttribute -> the subsystem roll-up state, preserving prior behaviour.
        assertEquals("**For Branch CANARA-CHETLA, IAS Status is ONLINE.**", answer("ias", null));
    }
}
