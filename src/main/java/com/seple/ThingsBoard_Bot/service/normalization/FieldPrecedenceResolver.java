package com.seple.ThingsBoard_Bot.service.normalization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;

@Component
public class FieldPrecedenceResolver {

    private final ValueNormalizer valueNormalizer;

    public FieldPrecedenceResolver(ValueNormalizer valueNormalizer) {
        this.valueNormalizer = valueNormalizer;
    }

    public ResolvedField resolveGatewayState(Map<String, Object> raw) {
        // Order matters: try the fields devices actually emit first.
        //  - status_device_gateway_status: derived authoritative status ("Online"/"Offline"/"Fault").
        //  - gatewayStatus_SYSTEM ON: flattened system-on flag ("true"/"false").
        // The legacy primaries (gateway_sts/gateway_status) are absent on current fleet payloads, and
        // the bare "gatewayStatus" is a JSON blob ({"SYSTEM ON":"true",...}) that toState() cannot
        // classify — it resolves to UNKNOWN and is skipped, so the real keys must come first or every
        // branch falls into the Unknown bucket despite being online.
        return resolveFirstState(raw, List.of(
                "status_device_gateway_status",
                "statusbox_system_healthy",
                "system_status_statusbox_system_healthy",
                "statusbox_system_on",
                "system_status_statusbox_system_on",
                "gatewayStatus_SYSTEM ON",
                "gateway_sts",
                "gateway_status",
                "gatewayStatus",
                "rock_gateway_status",
                "gwHealth",
                "status",
                "active",
                "cctv_sts",
                "ias_sts"
        ));
    }

    public ResolvedField resolveSubsystemState(Map<String, Object> raw, String primaryField, String... fallbacks) {
        List<String> candidates = new ArrayList<>();
        candidates.add(primaryField);
        for (String fallback : fallbacks) {
            candidates.add(fallback);
        }
        return resolveFirstState(raw, candidates);
    }

    public ResolvedMetric resolveBatteryVoltage(Map<String, Object> raw) {
        Double gatewayVoltage = valueNormalizer.toDouble(raw.get("gatewayStatus_battery_voltage"));
        Double batteryVoltage = valueNormalizer.toDouble(raw.get("battery_status_battery_voltage"));

        // User-requested precedence: use battery_status_battery_voltage for battery questions.
        // Keep gatewayStatus_battery_voltage only as fallback when battery_status is absent/unparseable.
        if (batteryVoltage != null) {
            return new ResolvedMetric(batteryVoltage, "battery_status_battery_voltage");
        }
        if (gatewayVoltage != null) {
            return new ResolvedMetric(gatewayVoltage, "gatewayStatus_battery_voltage");
        }
        return new ResolvedMetric(null, null);
    }

    public ResolvedMetric resolveAcVoltage(Map<String, Object> raw) {
        Double value = valueNormalizer.toDouble(raw.get("ac_status_ac_voltage"));
        return new ResolvedMetric(value, value != null ? "ac_status_ac_voltage" : null);
    }

    public ResolvedMetric resolveSystemCurrent(Map<String, Object> raw) {
        Double value = valueNormalizer.toDouble(raw.get("current_status_system_current"));
        return new ResolvedMetric(value, value != null ? "current_status_system_current" : null);
    }

    public Boolean resolveMainsOn(Map<String, Object> raw) {
        for (String key : List.of("statusbox_mains_on", "system_status_statusbox_mains_on", "MAINS ON", "gatewayStatus_MAINS ON")) {
            Object val = raw.get(key);
            if (val != null) {
                Boolean bool = valueNormalizer.toBoolean(String.valueOf(val));
                if (bool != null) {
                    return bool;
                }
            }
        }
        return null;
    }

    private ResolvedField resolveFirstState(Map<String, Object> raw, List<String> candidates) {
        for (String key : candidates) {
            Object rawValue = raw.get(key);
            if (rawValue == null) {
                continue;
            }
            String value = String.valueOf(rawValue);
            NormalizedState state = valueNormalizer.toState(value);
            if (state != NormalizedState.UNKNOWN) {
                return new ResolvedField(state, key, value);
            }
        }
        return new ResolvedField(NormalizedState.UNKNOWN, null, null);
    }

    public record ResolvedField(NormalizedState state, String sourceField, String rawValue) {
    }

    public record ResolvedMetric(Double value, String sourceField) {
    }
}
