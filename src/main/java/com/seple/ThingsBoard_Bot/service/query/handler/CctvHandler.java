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

/** CCTV_STATUS, CCTV_HDD_ERROR_STATUS, CCTV_HDD_INFO, CCTV_RECORDING_INFO, CAMERA_DISCONNECT_HISTORY. */
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
                || intent == QueryIntent.CAMERA_DISCONNECT_HISTORY;
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
            default -> null;
        };
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
