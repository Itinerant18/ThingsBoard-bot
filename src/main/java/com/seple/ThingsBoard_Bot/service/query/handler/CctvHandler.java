package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.AnswerTemplateService;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** CCTV_STATUS, CCTV_HDD_ERROR_STATUS, CCTV_HDD_INFO, CCTV_RECORDING_INFO, CAMERA_DISCONNECT_HISTORY, CCTV_DEVICE_INFO. */
@Component
public class CctvHandler implements AnswerHandler {

    private final AnswerTemplateService answerTemplateService;
    private final AnswerSupport support;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CctvHandler(AnswerTemplateService answerTemplateService, AnswerSupport support) {
        this.answerTemplateService = answerTemplateService;
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.CCTV_STATUS || intent == QueryIntent.CCTV_HDD_ERROR_STATUS
                || intent == QueryIntent.CCTV_HDD_INFO || intent == QueryIntent.CCTV_RECORDING_INFO
                || intent == QueryIntent.CAMERA_DISCONNECT_HISTORY || intent == QueryIntent.CCTV_DEVICE_INFO;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        return switch (query.getIntent()) {
            case CCTV_STATUS -> answerTemplateService.renderCctvStatus(branch,
                    branch.getCctv().getOnlineCameraCount(), branch.getCctv().getCameraCount());
            case CCTV_HDD_ERROR_STATUS -> answerCctvHddErrorStatus(branch);
            case CCTV_HDD_INFO -> answerCctvHddInfo(branch);
            case CCTV_RECORDING_INFO -> answerCctvRecordingInfo(branch);
            case CAMERA_DISCONNECT_HISTORY -> answerCameraDisconnectHistory(query);
            case CCTV_DEVICE_INFO -> answerCctvDeviceInfo(branch);
            default -> null;
        };
    }

    /** NVR/DVR inventory: vendor, model, HDD slot count, storage capacity and resolution from raw telemetry. */
    private String answerCctvDeviceInfo(BranchSnapshot branch) {
        Map<String, Object> raw = branch.getRawData();
        String model = support.firstNonBlank(raw,
                "Hikvision_NVR_model", "rock_model", "rockAI_model", "Dahua_NVR_model");
        String vendor = resolveNvrVendor(raw, model);
        Integer hddSlots = support.firstInteger(raw,
                "rock_NoOfHDDSlots", "Hikvision_NVR_NoOfHDDSlots1", "Dahua_NVR_NoOfHDDSlots", "count_HDD");
        Double capacityTb = parsePositiveDouble(support.firstNonBlank(raw,
                "rock_capacity", "Hikvision_NVR_capacity1", "Dahua_NVR_capacity"));
        String resolution = support.firstNonBlank(raw,
                "Video Resolution", "Hikvision_NVR_Resolutions", "Dahua_NVR_Resolutions", "CP_Plus_NVR_Resolutions");

        List<String> parts = new ArrayList<>();
        if (vendor != null) {
            parts.add("Vendor: " + vendor);
        }
        if (model != null) {
            parts.add("Model: " + model);
        }
        if (hddSlots != null) {
            parts.add("HDD Slots: " + hddSlots);
        }
        if (capacityTb != null) {
            parts.add("Storage: " + trimNumber(capacityTb) + " TB");
        }
        if (resolution != null) {
            parts.add("Resolution: " + resolution);
        }
        if (parts.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV device information is not available.**";
        }
        return "**For Branch " + support.branchName(branch)
                + ", CCTV Device Info is: " + String.join(", ", parts) + ".**";
    }

    /**
     * NVR vendor: prefer the reported {@code nvr_brand}; otherwise infer from the model prefix
     * (DS-/iDS- = Hikvision, DH or XVR or NVR4 = Dahua, CP-/CP_ = CPPLUS). {@code dexter_config_brand}
     * is the system-integrator brand (e.g. "SEPLE"), not the NVR make, so it is deliberately not used.
     */
    private String resolveNvrVendor(Map<String, Object> raw, String model) {
        String brand = support.firstNonBlank(raw, "nvr_brand");
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

    private Double parsePositiveDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(value.trim());
            return d > 0 ? d : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String trimNumber(double value) {
        return value % 1 == 0 ? String.valueOf((long) value) : String.valueOf(value);
    }

    private String answerCctvHddErrorStatus(BranchSnapshot branch) {
        Boolean hddError = support.resolveBoolean(branch.getRawData(),
                "HDD ERROR", "ticketStatus_HDD_ERROR", "cameraStatus_HDD ERROR");
        if (hddError == null) {
            String hddHealth = support.firstNonBlank(branch.getRawData(), "hddStatus");
            if (hddHealth != null) {
                hddError = "HEALTHY".equalsIgnoreCase(hddHealth) ? Boolean.FALSE : null;
            }
        }
        if (Boolean.TRUE.equals(hddError)) {
            return answerTemplateService.renderCctvHddErrorStatus(branch, "ACTIVE");
        }
        if (Boolean.FALSE.equals(hddError)) {
            return answerTemplateService.renderCctvHddErrorStatus(branch, "NORMAL");
        }
        return answerTemplateService.renderCctvHddErrorStatus(branch, "N/A");
    }

    private String answerCctvHddInfo(BranchSnapshot branch) {
        Object rawInfo = branch.getRawData().get("rock_HddINFO");
        if (rawInfo == null) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV HDD Information is not available.**";
        }

        try {
            JsonNode node = objectMapper.readTree(String.valueOf(rawInfo));
            if (!node.isArray() || node.isEmpty()) {
                return "**For Branch " + support.branchName(branch)
                        + ", CCTV HDD Information is not available.**";
            }

            List<String> slots = new ArrayList<>();
            for (JsonNode entry : node) {
                if (!entry.isObject()) {
                    continue;
                }
                String slot = entry.path("HDDSlots").asText("N/A");
                String status = entry.path("HDDStatus").asText("N/A");
                String capacity = entry.path("HDDcapacity").asText("N/A");
                String free = entry.path("HDDfreeSpace").asText("N/A");
                slots.add("Slot " + slot + ": " + status + ", Capacity " + capacity + " TB, Free " + free + " TB");
            }

            if (slots.isEmpty()) {
                return "**For Branch " + support.branchName(branch)
                        + ", CCTV HDD Information is not available.**";
            }

            return "**For Branch " + support.branchName(branch)
                    + ", CCTV HDD Information is: " + String.join("; ", slots) + ".**";
        } catch (Exception ignored) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV HDD Information is not available.**";
        }
    }

    private String answerCctvRecordingInfo(BranchSnapshot branch) {
        Object rawInfo = branch.getRawData().get("rock_VIDEOdETAILS");
        if (rawInfo == null) {
            rawInfo = branch.getRawData().get("Hikvision_NVR_CameraRecInfo");
        }
        if (rawInfo == null) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV Recording Information is not available.**";
        }

        try {
            JsonNode node = objectMapper.readTree(String.valueOf(rawInfo));
            if (!node.isArray() || node.isEmpty()) {
                return "**For Branch " + support.branchName(branch)
                        + ", CCTV Recording Information is not available.**";
            }

            int withRecording = 0;
            List<String> noRecordingChannels = new ArrayList<>();
            for (JsonNode entry : node) {
                if (!entry.isObject()) {
                    continue;
                }
                int duration = entry.path("total_duration").asInt(0);
                String channel = entry.has("channel_no") ? entry.path("channel_no").asText("") : entry.path("camera_id").asText("");
                if (duration > 0) {
                    withRecording++;
                } else if (channel != null && !channel.isBlank() && !"N/A".equalsIgnoreCase(channel)) {
                    noRecordingChannels.add(channel);
                }
            }

            StringBuilder builder = new StringBuilder("**For Branch ")
                    .append(support.branchName(branch))
                    .append(", CCTV Recording Information is: ");
            builder.append(withRecording).append(" channel(s) have recording data");
            if (!noRecordingChannels.isEmpty()) {
                builder.append("; no recording data for channel(s) ")
                        .append(String.join(", ", noRecordingChannels));
            }
            builder.append(".**");
            return builder.toString();
        } catch (Exception ignored) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV Recording Information is not available.**";
        }
    }

    private String answerCameraDisconnectHistory(ResolvedQuery query) {
        BranchSnapshot branch = query.getTargetBranch();
        Map<String, Object> raw = branch.getRawData();
        boolean historical = query.getOriginalQuestion() == null
                || support.containsAny(query.getOriginalQuestion(), "history", "historical");
        List<String> channelsWithHistory = new ArrayList<>();
        List<String> channelsDisconnectedNow = new ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            String key = "cameraDisconnectCH" + i + "_history";
            Object value = raw.get(key);
            if (value == null) {
                value = raw.get("cameraStatus_CAMERA DISCONNECT CH " + i);
                if (support.isTrue(value)) {
                    channelsDisconnectedNow.add("Channel " + i);
                }
                continue;
            }
            if (support.hasHistoryEntries(String.valueOf(value))) {
                channelsWithHistory.add("Channel " + i);
            }
        }

        Integer disconnectCount = support.toInt(raw.get("camera_disconnect_count"));
        if (disconnectCount != null && disconnectCount > 0 && channelsDisconnectedNow.isEmpty()) {
            channelsDisconnectedNow.add(disconnectCount + " camera(s)");
        }

        if (historical) {
            if (channelsWithHistory.isEmpty()) {
                return "**For Branch " + support.branchName(branch)
                        + ", No historical camera disconnects found.**";
            }
            return "**For Branch " + support.branchName(branch) + ", Historical camera disconnects found: "
                    + String.join(", ", channelsWithHistory) + ".**";
        }

        if (!channelsDisconnectedNow.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV Disconnect Status is: " + String.join(", ", channelsDisconnectedNow) + " disconnected.**";
        }
        if (disconnectCount != null && disconnectCount == 0) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV Disconnect Status is: No disconnected cameras detected.**";
        }
        if (channelsWithHistory.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", No historical camera disconnects found.**";
        }
        return "**For Branch " + support.branchName(branch)
                + ", CCTV Disconnect Status is: No disconnected cameras detected.**";
    }
}
