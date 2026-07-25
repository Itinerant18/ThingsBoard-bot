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
                || normName.contains("TEST") || normTech.contains("TEST")
                || normName.contains("KAFKA") || normTech.contains("KAFKA")
                || normName.contains("ARCHITECTURE") || normTech.contains("ARCHITECTURE")
                || normName.contains("PLAYBACK") || normTech.contains("PLAYBACK")
                || normName.contains("RECORDING_IDS") || normTech.contains("RECORDING_IDS")
                || normName.contains("ZOHO") || normTech.contains("ZOHO")
                || normName.contains("SBI LHO") || normTech.contains("SBI LHO")
                || normName.contains("SEPLE") || normTech.contains("SEPLE")
                || normName.contains("SEPL-") || normTech.contains("SEPL-")
                || normName.contains("SDF-") || normTech.contains("SDF-")
                || normName.contains("-DX") || normTech.contains("-DX")
                || normName.contains("MCC") || normTech.contains("MCC")
                || normName.endsWith("-BAS") || normTech.endsWith("-BAS")
                || normName.startsWith("CANARA-") || normTech.startsWith("CANARA-")
                || normName.startsWith("BOB-") || normTech.startsWith("BOB-")
                || normName.startsWith("PNB") || normTech.startsWith("PNB")) {
            return false;
        }

        return true;
    }
}
