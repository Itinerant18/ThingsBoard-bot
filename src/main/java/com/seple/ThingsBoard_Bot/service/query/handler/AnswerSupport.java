package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.model.domain.SubsystemStatus;

/**
 * Shared low-level helpers used across the per-intent {@link AnswerHandler}s. These are the
 * formatting, raw-data resolution, and subsystem-lookup primitives that were previously private
 * methods of {@code DeterministicAnswerService}; they are moved here verbatim so the handlers can
 * share them without duplication. Logic is unchanged.
 */
@Component
public class AnswerSupport {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String formatState(NormalizedState state) {
        return switch (state) {
            case ONLINE -> "ONLINE";
            case OFFLINE -> "OFFLINE";
            case FAULT -> "FAULT";
            case NOT_INSTALLED -> "NOT INSTALLED";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    public String branchName(BranchSnapshot branch) {
        if (branch == null || branch.getIdentity() == null || branch.getIdentity().getBranchName() == null) {
            return "Unknown";
        }
        String display = branch.getIdentity().getBranchName()
                .replaceFirst("(?i)^BRANCH\\s+", "")
                .replaceFirst("(?i)^BOI-\\s*", "")
                .trim();
        String technicalId = branch.getIdentity().getTechnicalId();
        if ("TR".equalsIgnoreCase(display) && technicalId != null
                && technicalId.toUpperCase().contains("TRENDZ")) {
            return "TRENDZ";
        }
        return display;
    }

    public SubsystemStatus subsystemByTarget(BranchSnapshot branch, String targetSystem) {
        if (targetSystem == null) {
            return null;
        }
        return switch (targetSystem) {
            case "ias" -> branch.getSubsystems().getIas();
            case "bas" -> branch.getSubsystems().getBas();
            case "fas" -> branch.getSubsystems().getFas();
            case "timeLock" -> branch.getSubsystems().getTimeLock();
            case "accessControl" -> branch.getSubsystems().getAccessControl();
            case "cctv" -> branch.getSubsystems().getCctv();
            default -> null;
        };
    }

    public Boolean resolveSubsystemFault(BranchSnapshot branch, String targetSystem) {
        Map<String, Object> raw = branch.getRawData();
        return switch (targetSystem) {
            case "ias" -> resolveBoolean(raw,
                    "ticketStatus_IAS_FAULT", "intrusion_alarm_system_fault",
                    "INTRUSION ALARM SYSTEM FAULT", "iasBasFasStatus_INTRUSION ALARM SYSTEM FAULT");
            case "bas" -> resolveFromCount(raw, "BASfaultCOUNT");
            case "fas" -> resolveBoolean(raw,
                    "ticketStatus_FAS_FAULT", "fireAlarmSystem_fault", "FIRE ALARM SYSTEM FAULT",
                    "fire_alarm_system_fault", "iasBasFasStatus_FIRE ALARM SYSTEM FAULT");
            case "timeLock" -> resolveBoolean(raw,
                    "ticketStatus_TLS_TAMPER", "ticketStatus_TLS_OFF");
            case "accessControl" -> resolveBoolean(raw,
                    "ticketStatus_ACS_TAMPER", "ticketStatus_ACS_OFF");
            case "cctv" -> resolveBoolean(raw,
                    "HDD ERROR", "ticketStatus_HDD_ERROR", "ticketStatus_NVR_OFF", "cameraStatus_HDD ERROR");
            default -> null;
        };
    }

    public Boolean resolveSubsystemAlarm(BranchSnapshot branch, String targetSystem) {
        Map<String, Object> raw = branch.getRawData();
        return switch (targetSystem) {
            case "ias" -> resolveBoolean(raw,
                    "ticketStatus_IAS_ACTIVATE", "ticketStatus_IAS_INT_ACTIVATE",
                    "INTRUSION ALARM SYSTEM ACTIVATE", "iasBasFasStatus_INTRUSION ALARM SYSTEM ACTIVATE");
            case "bas" -> null;
            case "fas" -> resolveBoolean(raw,
                    "ticketStatus_FAS_ACTIVATE", "FIRE ALARM SYSTEM ACTIVATE",
                    "iasBasFasStatus_FIRE ALARM SYSTEM ACTIVATE");
            case "timeLock" -> {
                Boolean doorOpenAlarm = resolveBoolean(raw, "ticketStatus_TLS_DOOR_OPEN");
                if (doorOpenAlarm != null) {
                    yield doorOpenAlarm;
                }
                yield resolveFromCount(raw, "time_lock_door_open_count");
            }
            case "accessControl" -> {
                Boolean doorOpenAlarm = resolveBoolean(raw, "ticketStatus_ACS_DOOR_OPEN");
                if (doorOpenAlarm != null) {
                    yield doorOpenAlarm;
                }
                yield resolveFromCount(raw, "access_control_door_open_count");
            }
            case "cctv" -> resolveBoolean(raw, "CAMERA DISCONNECT", "ticketStatus_CAM_DIS", "ticketStatus_CAM_TAMPER");
            default -> null;
        };
    }

    public Boolean resolveFromCount(Map<String, Object> raw, String key) {
        Integer count = toInt(raw.get(key));
        if (count == null) {
            return null;
        }
        return count > 0;
    }

    public boolean hasHistoryEntries(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            return node.isArray() && !node.isEmpty();
        } catch (Exception ignored) {
            return !"[]".equals(rawJson.trim());
        }
    }

    public boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    public List<String> activeSystems(BranchSnapshot branch) {
        List<String> active = new ArrayList<>();
        maybeAdd(active, branch.getSubsystems().getCctv(), "CCTV DVR");
        maybeAdd(active, branch.getSubsystems().getIas(), "IAS Panel");
        maybeAdd(active, branch.getSubsystems().getBas(), "BAS Panel");
        maybeAdd(active, branch.getSubsystems().getFas(), "FAS Panel");
        maybeAdd(active, branch.getSubsystems().getTimeLock(), "Time Lock");
        maybeAdd(active, branch.getSubsystems().getAccessControl(), "Access Control Controller");
        return active;
    }

    private void maybeAdd(List<String> active, SubsystemStatus status, String label) {
        if (status != null && status.getState() == NormalizedState.ONLINE) {
            active.add(label);
        }
    }

    public List<String> systemsByState(BranchSnapshot branch, NormalizedState targetState) {
        List<String> systems = new ArrayList<>();
        subsystemMap(branch).forEach((label, status) -> {
            if (status != null && status.getState() == targetState) {
                systems.add(label);
            }
        });
        return systems;
    }

    public List<String> installedSystems(BranchSnapshot branch) {
        List<String> systems = new ArrayList<>();
        subsystemMap(branch).forEach((label, status) -> {
            if (status != null && status.isInstalled()) {
                systems.add(label);
            }
        });
        return systems;
    }

    private Map<String, SubsystemStatus> subsystemMap(BranchSnapshot branch) {
        Map<String, SubsystemStatus> systems = new LinkedHashMap<>();
        systems.put("CCTV DVR", branch.getSubsystems().getCctv());
        systems.put("IAS Panel", branch.getSubsystems().getIas());
        systems.put("BAS Panel", branch.getSubsystems().getBas());
        systems.put("FAS Panel", branch.getSubsystems().getFas());
        systems.put("Time Lock", branch.getSubsystems().getTimeLock());
        systems.put("Access Control Controller", branch.getSubsystems().getAccessControl());
        return systems;
    }

    public List<String> branchLevelFaultIndicators(BranchSnapshot branch) {
        Map<String, Object> raw = branch.getRawData();
        List<String> indicators = new ArrayList<>();
        if (isTrue(raw.get("ticketStatus_FAS_FAULT")) || isTrue(raw.get("fireAlarmSystem_fault"))
                || isTrue(raw.get("fire_alarm_system_fault"))) {
            indicators.add("Fire Alarm Fault");
        }
        if (isTrue(raw.get("ticketStatus_IAS_FAULT")) || isTrue(raw.get("intrusion_alarm_system_fault"))
                || isTrue(raw.get("INTRUSION ALARM SYSTEM FAULT"))) {
            indicators.add("Intrusion Alarm Fault");
        }
        if (isTrue(raw.get("DVR/NVR OFF")) || isTrue(raw.get("ticketStatus_NVR_OFF"))) {
            indicators.add("NVR/DVR Off");
        }
        if (isTrue(raw.get("HDD ERROR")) || isTrue(raw.get("ticketStatus_HDD_ERROR"))) {
            indicators.add("HDD Error");
        }
        return indicators;
    }

    public Boolean resolveBoolean(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank() || "N/A".equalsIgnoreCase(text) || "null".equalsIgnoreCase(text)) {
                continue;
            }
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "on".equalsIgnoreCase(text)
                    || "healthy".equalsIgnoreCase(text) || "online".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text) || "off".equalsIgnoreCase(text)
                    || "offline".equalsIgnoreCase(text) || "inactive".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return null;
    }

    public String firstNonBlank(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank() && !"null".equalsIgnoreCase(text) && !"N/A".equalsIgnoreCase(text) && !"NA".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    public Integer firstInteger(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Integer value = toInt(raw.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public boolean containsAny(String text, String... fragments) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase();
        for (String fragment : fragments) {
            if (normalized.contains(fragment.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
