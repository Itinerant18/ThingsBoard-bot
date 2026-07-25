package com.seple.ThingsBoard_Bot.model.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchSnapshot {
    private BranchIdentity identity;
    private GatewayStatus gateway;
    private PowerStatus power;
    private BranchSubsystems subsystems;
    private CctvStatus cctv;
    private AlertSummary alerts;
    private HardwareHealth hardware;

    @Builder.Default
    private List<String> rawSourceWarnings = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> rawData = Map.of();

    public boolean isOperationalBranch() {
        if (identity == null) {
            return false;
        }
        String name = identity.getBranchName();
        String techId = identity.getTechnicalId();

        String normName = name != null ? name.toUpperCase(java.util.Locale.ROOT) : "";
        String normTech = techId != null ? techId.toUpperCase(java.util.Locale.ROOT) : "";

        if (normName.isBlank() && normTech.isBlank()) {
            return false;
        }

        if (normName.contains("DEMO") || normTech.contains("DEMO")
                || normName.contains("TESTGATEWAY") || normTech.contains("TESTGATEWAY")
                || normName.contains("ZOHO") || normTech.contains("ZOHO")
                || normName.contains("RECORDING_IDS") || normTech.contains("RECORDING_IDS")
                || normName.contains("SBI LHO") || normTech.contains("SBI LHO")
                || normName.endsWith(" TEST") || normTech.endsWith(" TEST")
                || normName.equals("TEST") || normTech.equals("TEST")
                || normName.equals("DEMO") || normTech.equals("DEMO")) {
            return false;
        }

        return true;
    }
}
