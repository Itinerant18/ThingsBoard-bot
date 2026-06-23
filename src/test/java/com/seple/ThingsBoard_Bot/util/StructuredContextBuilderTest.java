package com.seple.ThingsBoard_Bot.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;

/**
 * The LLM-fallback context must surface the secondary attributes (IMEI, GPS, operator, staleness,
 * hierarchy, CCTV inventory) so the model can answer the long tail instead of hallucinating.
 */
class StructuredContextBuilderTest {

    private final StructuredContextBuilder builder = new StructuredContextBuilder();
    private final BranchSnapshotMapper mapper =
            new BranchSnapshotMapper(new FieldPrecedenceResolver(new ValueNormalizer()), new ValueNormalizer());

    private BranchSnapshot snapshot(Map<String, Object> raw) {
        raw.putIfAbsent("branchName", "CANARA-CHETLA");
        return mapper.map(raw);
    }

    @Test
    void includesEnrichedFieldsWhenPresent() throws Exception {
        Map<String, Object> raw = new HashMap<>();
        raw.put("imei_id_dev_id", "358773400033916");
        raw.put("lat", "22.5726");
        raw.put("lon", "88.3639");
        raw.put("system_status_statusbox_network", "AirTel");
        raw.put("data_as_of", "2026-06-23T10:00:00Z");
        raw.put("data_stale", "false");
        raw.put("nbg_name", "HO (Canara Bank)");
        raw.put("zo_name", "RO KOLKATA - I");
        raw.put("nvr_brand", "Hikvision");
        raw.put("rock_model", "DS-7716NXI-K4");
        raw.put("rock_NoOfHDDSlots", "4");
        raw.put("rock_capacity", "21.84");

        BranchSnapshot snap = snapshot(raw);
        String json = builder.build(List.of(snap), snap);

        assertTrue(json.contains("358773400033916"), json);
        assertTrue(json.contains("22.5726, 88.3639"), json);
        assertTrue(json.contains("AirTel"), json);
        assertTrue(json.contains("\"nbg\""), json);
        assertTrue(json.contains("DS-7716NXI-K4"), json);
        assertTrue(json.contains("21.84"), json);
        assertTrue(json.contains("\"hdd_slots\":4"), json);
    }

    @Test
    void marksMissingImeiUnmappedOperatorAndNotReportedGps() throws Exception {
        Map<String, Object> raw = new HashMap<>();
        raw.put("imei_id_dev_id", "0");
        raw.put("lat", "20.5937");
        raw.put("lon", "78.9629");
        raw.put("lat_lon_default", "true");

        BranchSnapshot snap = snapshot(raw);
        String json = builder.build(List.of(snap), snap);

        assertTrue(json.contains("\"imei\":\"missing\""), json);
        assertTrue(json.contains("\"network_operator\":\"not mapped\""), json);
        assertTrue(json.contains("\"gps\":\"not reported\""), json);
    }
}
