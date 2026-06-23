package com.seple.ThingsBoard_Bot.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;

@Component
public class StructuredContextBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String build(List<BranchSnapshot> snapshots, BranchSnapshot targetBranch) throws JsonProcessingException {
        Map<String, Object> context = new LinkedHashMap<>();
        if (targetBranch != null) {
            context.put("focus_branch", compactBranch(targetBranch));
        } else {
            List<Map<String, Object>> branches = new ArrayList<>();
            for (BranchSnapshot snapshot : snapshots) {
                branches.add(compactBranch(snapshot));
            }
            context.put("branches", branches);
        }
        return objectMapper.writeValueAsString(context);
    }

    private Map<String, Object> compactBranch(BranchSnapshot snapshot) {
        Map<String, Object> branch = new LinkedHashMap<>();
        branch.put("branch_name", snapshot.getIdentity().getBranchName());
        branch.put("technical_id", snapshot.getIdentity().getTechnicalId());
        branch.put("gateway", snapshot.getGateway().getState());
        branch.put("battery_voltage", snapshot.getPower().getBatteryVoltage());
        branch.put("ac_voltage", snapshot.getPower().getAcVoltage());
        branch.put("system_current", snapshot.getPower().getSystemCurrent());
        branch.put("alarm_count", snapshot.getAlerts().getAlarmCount());
        branch.put("error_count", snapshot.getAlerts().getErrorCount());
        branch.put("subsystems", Map.of(
                "cctv", snapshot.getSubsystems().getCctv().getState(),
                "ias", snapshot.getSubsystems().getIas().getState(),
                "bas", snapshot.getSubsystems().getBas().getState(),
                "fas", snapshot.getSubsystems().getFas().getState(),
                "timeLock", snapshot.getSubsystems().getTimeLock().getState(),
                "accessControl", snapshot.getSubsystems().getAccessControl().getState()
        ));
        if (snapshot.getCctv().getCameraCount() != null) {
            branch.put("cctv_online_count", snapshot.getCctv().getOnlineCameraCount());
            branch.put("cctv_total_count", snapshot.getCctv().getCameraCount());
        }
        enrichFromRaw(branch, snapshot.getRawData());
        return branch;
    }

    /**
     * Surface the secondary attributes the LLM otherwise can't see (IMEI, GPS, network operator,
     * staleness, hierarchy names, CCTV inventory) so it can answer the long tail and fleet questions
     * instead of hallucinating. Values are read from the Redis-backed raw map; empties are omitted to
     * keep the context compact.
     */
    private void enrichFromRaw(Map<String, Object> branch, Map<String, Object> raw) {
        if (raw == null) {
            return;
        }
        branch.put("imei", resolveImei(raw));
        branch.put("gps", resolveGps(raw));
        branch.put("network_operator", resolveOperator(raw));

        String dataAsOf = firstNonBlank(raw, "data_as_of");
        if (dataAsOf != null) {
            branch.put("data_as_of", dataAsOf);
            branch.put("data_stale", isTrue(raw.get("data_stale")));
        }

        putIfPresent(branch, "nbg", firstNonBlank(raw, "nbg_name"));
        putIfPresent(branch, "zone", firstNonBlank(raw, "zo_name", "zone_name"));
        putIfPresent(branch, "region", firstNonBlank(raw, "ro_name", "co_name"));

        Map<String, Object> inventory = cctvInventory(raw);
        if (!inventory.isEmpty()) {
            branch.put("cctv_inventory", inventory);
        }
    }

    /** IMEI value, or "missing" for any not-reported sentinel ("0", "None", null, "N/A"). */
    private static String resolveImei(Map<String, Object> raw) {
        String v = firstNonBlank(raw, "imei_id_dev_id", "imei_id");
        if (v == null) {
            return "missing";
        }
        String t = v.trim();
        return (t.equals("0") || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("na")) ? "missing" : t;
    }

    /** "lat, lon", or "not reported" when missing / the default India-centroid placeholder. */
    private static String resolveGps(Map<String, Object> raw) {
        Double lat = parseDouble(firstNonBlank(raw, "lat", "latitude"));
        Double lon = parseDouble(firstNonBlank(raw, "lon", "longitude"));
        if (lat == null || lon == null) {
            return "not reported";
        }
        boolean usingDefault = isTrue(raw.get("lat_lon_default"))
                || (Math.abs(lat - 20.5937) < 0.001 && Math.abs(lon - 78.9629) < 0.001);
        return usingDefault ? "not reported" : lat + ", " + lon;
    }

    private static String resolveOperator(Map<String, Object> raw) {
        String op = firstNonBlank(raw, "system_status_statusbox_network", "statusbox_network", "networkOperator");
        return op != null ? op : "not mapped";
    }

    private static Map<String, Object> cctvInventory(Map<String, Object> raw) {
        Map<String, Object> inv = new LinkedHashMap<>();
        String model = firstNonBlank(raw, "Hikvision_NVR_model", "rock_model", "rockAI_model", "Dahua_NVR_model");
        putIfPresent(inv, "vendor", resolveVendor(raw, model));
        putIfPresent(inv, "model", model);
        Integer slots = firstInteger(raw,
                "rock_NoOfHDDSlots", "Hikvision_NVR_NoOfHDDSlots1", "Dahua_NVR_NoOfHDDSlots", "count_HDD");
        if (slots != null) {
            inv.put("hdd_slots", slots);
        }
        Double tb = parsePositiveDouble(firstNonBlank(raw, "rock_capacity", "Hikvision_NVR_capacity1", "Dahua_NVR_capacity"));
        if (tb != null) {
            inv.put("storage_tb", tb);
        }
        return inv;
    }

    private static String resolveVendor(Map<String, Object> raw, String model) {
        String brand = firstNonBlank(raw, "nvr_brand");
        if (brand != null) {
            return brand;
        }
        if (model == null) {
            return null;
        }
        String m = model.toUpperCase();
        if (m.startsWith("DS-") || m.startsWith("IDS-")) {
            return "Hikvision";
        }
        if (m.startsWith("DH") || m.startsWith("XVR") || m.startsWith("NVR4")) {
            return "Dahua";
        }
        if (m.startsWith("CP-") || m.startsWith("CP_")) {
            return "CPPLUS";
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String firstNonBlank(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank() && !"null".equalsIgnoreCase(text) && !"N/A".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    private static Integer firstInteger(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            String text = firstNonBlank(raw, key);
            if (text != null) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return null;
    }

    private static Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parsePositiveDouble(String value) {
        Double d = parseDouble(value);
        return (d != null && d > 0) ? d : null;
    }

    private static boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }
        String t = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(t) || "1".equals(t);
    }
}
