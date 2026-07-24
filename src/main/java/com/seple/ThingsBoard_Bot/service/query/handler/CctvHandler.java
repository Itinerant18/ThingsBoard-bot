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

    /** Recording-retention compliance threshold in days. Cameras with fewer recorded days are non-compliant. */
    @org.springframework.beans.factory.annotation.Value("${iotchatbot.cctv.recording-retention-days:90}")
    private int retentionDays = 90;

    public CctvHandler(AnswerTemplateService answerTemplateService, AnswerSupport support) {
        this.answerTemplateService = answerTemplateService;
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.CCTV_STATUS || intent == QueryIntent.CCTV_HDD_ERROR_STATUS
                || intent == QueryIntent.CCTV_HDD_INFO || intent == QueryIntent.CCTV_RECORDING_INFO
                || intent == QueryIntent.CCTV_RECORDING_COMPLIANCE
                || intent == QueryIntent.CAMERA_DISCONNECT_HISTORY || intent == QueryIntent.CCTV_DEVICE_INFO;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        // Fleet compliance aggregates every branch's cameras, so it runs before the single-branch guard.
        if (query.getIntent() == QueryIntent.CCTV_RECORDING_COMPLIANCE) {
            return answerRecordingCompliance(snapshots);
        }
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

    /** First present field's text among alternative key spellings (e.g. Hikvision vs Dahua HDD keys). */
    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field)) {
                return node.path(field).asText("N/A");
            }
        }
        return "N/A";
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
                // Two NVR schemas: Hikvision uses HDDSlots/HDDcapacity/HDDfreeSpace; Dahua/XVR uses
                // HDDSlot/HDDCapacity/HDDFreeSpace. Read either so Dahua devices don't all show N/A.
                String slot = firstText(entry, "HDDSlots", "HDDSlot");
                String status = entry.path("HDDStatus").asText("N/A");
                String capacity = firstText(entry, "HDDcapacity", "HDDCapacity");
                String free = firstText(entry, "HDDfreeSpace", "HDDFreeSpace");
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

    /** One camera's recorded-days signal. days == 0 means present but empty; null channel is dropped. */
    private record CameraRec(String channel, int days) {}

    /** All CCTV recording-history keys, across NVR vendors, that carry per-channel recorded days. */
    private static final List<String> REC_KEYS = List.of(
            "rock_VIDEOdETAILS", "VIDEOdETAILS", "Hikvision_NVR_CameraRecInfo",
            "Dahua_NVR_CameraRecInfo", "CP_Plus_NVR_CameraRecInfo",
            "Hik_rock_NVR1_VIDEOdETAILS", "Hik_rock_NVR2_VIDEOdETAILS");

    /**
     * Parse per-channel recorded days from whatever RecInfo arrays a branch carries. Vendors name the
     * days field differently — {@code total_recording_days} (CP Plus/Dahua) or {@code total_duration}
     * (Hikvision/rock, same meaning) — and the channel as channel / channel_no / camera_id. Cameras are
     * keyed by channel so the same physical camera reported under two NVR keys isn't double-counted.
     */
    private List<CameraRec> parseRecordings(Map<String, Object> raw) {
        Map<String, CameraRec> byChannel = new java.util.LinkedHashMap<>();
        for (String key : REC_KEYS) {
            Object rawInfo = raw.get(key);
            if (rawInfo == null) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(String.valueOf(rawInfo));
                if (!node.isArray()) {
                    continue;
                }
                for (JsonNode e : node) {
                    if (!e.isObject()) {
                        continue;
                    }
                    String channel = e.has("channel_no") ? e.path("channel_no").asText("")
                            : e.has("channel") ? e.path("channel").asText("")
                            : e.path("camera_id").asText("");
                    if (channel == null || channel.isBlank() || "N/A".equalsIgnoreCase(channel)) {
                        continue;
                    }
                    int days = e.has("total_recording_days")
                            ? e.path("total_recording_days").asInt(0)
                            : e.path("total_duration").asInt(0);
                    // Keep the max seen for a channel (a camera reported twice; take the better signal).
                    CameraRec prev = byChannel.get(channel);
                    if (prev == null || days > prev.days()) {
                        byChannel.put(channel, new CameraRec(channel, days));
                    }
                }
            } catch (Exception ignored) {
                // skip an unparseable vendor blob; other keys may still yield data
            }
        }
        return new ArrayList<>(byChannel.values());
    }

    private String answerCctvRecordingInfo(BranchSnapshot branch) {
        List<CameraRec> cams = parseRecordings(branch.getRawData());
        if (cams.isEmpty()) {
            return "**For Branch " + support.branchName(branch)
                    + ", CCTV Recording Information is not available.**";
        }
        int compliant = 0, nonCompliant = 0, zero = 0;
        List<String> zeroCh = new ArrayList<>();
        int min = Integer.MAX_VALUE, max = 0;
        for (CameraRec c : cams) {
            if (c.days() <= 0) { zero++; zeroCh.add(c.channel()); }
            if (c.days() >= retentionDays) compliant++; else nonCompliant++;
            min = Math.min(min, c.days());
            max = Math.max(max, c.days());
        }
        StringBuilder b = new StringBuilder("**For Branch ").append(support.branchName(branch))
                .append(", CCTV Recording (retention target ").append(retentionDays).append(" days):** ")
                .append(cams.size()).append(" camera(s) — ")
                .append(compliant).append(" compliant (≥").append(retentionDays).append("d), ")
                .append(nonCompliant).append(" non-compliant");
        if (zero > 0) {
            b.append(" of which ").append(zero).append(" have 0 days (channel(s) ")
                    .append(String.join(", ", zeroCh)).append(")");
        }
        b.append(". Recorded days range ").append(min == Integer.MAX_VALUE ? 0 : min)
                .append("–").append(max).append(".");
        return b.toString();
    }

    /** Fleet-wide recording compliance: aggregate every branch's cameras against the retention target. */
    private String answerRecordingCompliance(List<BranchSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "No branches are in scope, so there is no recording compliance to report.";
        }
        int totalCams = 0, compliant = 0, nonCompliant = 0, zero = 0, branchesWithData = 0;
        List<String> nonCompliantBranches = new ArrayList<>();
        for (BranchSnapshot s : snapshots) {
            List<CameraRec> cams = parseRecordings(s.getRawData());
            if (cams.isEmpty()) {
                continue;
            }
            branchesWithData++;
            int branchNon = 0;
            for (CameraRec c : cams) {
                totalCams++;
                if (c.days() <= 0) zero++;
                if (c.days() >= retentionDays) compliant++; else { nonCompliant++; branchNon++; }
            }
            if (branchNon > 0 && s.getIdentity() != null && s.getIdentity().getBranchName() != null) {
                nonCompliantBranches.add(support.branchName(s) + " (" + branchNon + ")");
            }
        }
        if (totalCams == 0) {
            return "No camera recording data is available across the branches in scope.";
        }
        java.util.Collections.sort(nonCompliantBranches);
        StringBuilder b = new StringBuilder("**CCTV Recording Compliance (retention target ")
                .append(retentionDays).append(" days), ").append(branchesWithData).append(" branches:**\n")
                .append("- ").append(totalCams).append(" cameras total\n")
                .append("- ").append(compliant).append(" compliant (≥").append(retentionDays).append(" days)\n")
                .append("- ").append(nonCompliant).append(" non-compliant (<").append(retentionDays).append(" days)")
                .append(zero > 0 ? ", including " + zero + " with 0 days" : "").append("\n");
        if (!nonCompliantBranches.isEmpty()) {
            b.append("\nNon-compliant branches (count of cameras below target):\n");
            for (String br : nonCompliantBranches) {
                b.append("  - ").append(br).append("\n");
            }
        }
        return b.toString();
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
