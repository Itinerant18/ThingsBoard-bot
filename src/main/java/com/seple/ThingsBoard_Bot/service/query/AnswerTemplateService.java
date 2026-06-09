package com.seple.ThingsBoard_Bot.service.query;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;

@Component
public class AnswerTemplateService {

    public String renderGlobalOverview(List<String> onlineBranches, List<String> offlineBranches) {
        StringBuilder builder = new StringBuilder();
        builder.append("**Total: ")
                .append(onlineBranches.size()).append(" Online | ")
                .append(offlineBranches.size()).append(" Offline**");
        builder.append("\nFor your question about all branches, here is the current branch-level status.");

        if (!onlineBranches.isEmpty()) {
            builder.append("\n\nOnline:");
            for (String branch : onlineBranches) {
                builder.append("\n- ").append(branch);
            }
        }

        if (!offlineBranches.isEmpty()) {
            builder.append("\n\nOffline:");
            for (String branch : offlineBranches) {
                builder.append("\n- ").append(branch);
            }
        }

        return builder.toString();
    }

    public String renderGlobalOverviewFromCounters(int onlineCount, int offlineCount) {
        return "**Total: " + onlineCount + " Online | " + offlineCount + " Offline**"
                + "\nFor your question about all branches, here is the current branch-level status.";
    }

    public String renderGatewayStatus(BranchSnapshot branch, String stateText) {
        return "**For Branch " + branchName(branch) + ", the Gateway status is currently " + stateText + ".**";
    }

    public String renderMetric(BranchSnapshot branch, String label, Double value, String unit) {
        if (value == null) {
            return "**For Branch " + branchName(branch) + ", " + label + " is N/A.**";
        }
        return "**For Branch " + branchName(branch) + ", " + label + " is " + trim(value) + unit + ".**";
    }

    public String renderActiveDevices(BranchSnapshot branch, List<String> activeSystems) {
        return "**For Branch " + branchName(branch) + ", Active Devices (" + activeSystems.size() + "): "
                + String.join(", ", activeSystems)
                + ".**";
    }

    private static class OfflineCamera {
        final String name;
        final String channel;

        OfflineCamera(String name, String channel) {
            this.name = name;
            this.channel = channel;
        }
    }

    public String renderCctvStatus(BranchSnapshot branch, Integer onlineCameras, Integer totalCameras) {
        if (onlineCameras == null && totalCameras == null) {
            return "**For Branch " + branchName(branch) + ", CCTV Camera Status is N/A.**";
        }
        List<OfflineCamera> offlineCameras = null;
        if (onlineCameras != null && totalCameras != null && onlineCameras < totalCameras) {
            offlineCameras = getOfflineCameras(branch);
        }
        
        if (offlineCameras != null && !offlineCameras.isEmpty()) {
            Map<String, List<String>> groups = new java.util.LinkedHashMap<>();
            for (OfflineCamera cam : offlineCameras) {
                String keyName = cam.name;
                if (keyName.isEmpty()) {
                    keyName = "Channel " + cam.channel;
                }
                groups.computeIfAbsent(keyName, k -> new ArrayList<>()).add(cam.channel);
            }
            
            List<String> formattedOffline = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
                String nameKey = entry.getKey();
                List<String> channels = entry.getValue();
                
                if (nameKey.startsWith("Channel ") && channels.size() == 1 && nameKey.substring(8).equals(channels.get(0))) {
                    formattedOffline.add(nameKey);
                } else {
                    if (channels.size() == 1) {
                        formattedOffline.add("Channel " + channels.get(0) + ": " + nameKey);
                    } else {
                        formattedOffline.add(nameKey + " (" + channels.size() + " units: Channels " + formatChannels(channels) + ")");
                    }
                }
            }
            
            StringBuilder sb = new StringBuilder();
            if (totalCameras != null) {
                sb.append("**For Branch ").append(branchName(branch))
                  .append(", CCTV Camera Status is ")
                  .append(onlineCameras).append(" of ").append(totalCameras).append(" cameras ONLINE.**");
            } else {
                sb.append("**For Branch ").append(branchName(branch))
                  .append(", CCTV Camera Status is ").append(onlineCameras).append(" cameras ONLINE.**");
            }
            sb.append("\n\nOffline Cameras:");
            for (String item : formattedOffline) {
                sb.append("\n- ").append(item);
            }
            return sb.toString();
        }

        if (totalCameras != null) {
            return "**For Branch " + branchName(branch) + ", CCTV Camera Status is "
                    + onlineCameras + " of " + totalCameras + " cameras ONLINE.**";
        }
        return "**For Branch " + branchName(branch) + ", CCTV Camera Status is " + onlineCameras + " cameras ONLINE.**";
    }

    private static String formatChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return "";
        }
        List<Integer> ints = new ArrayList<>();
        List<String> nonInts = new ArrayList<>();
        for (String ch : channels) {
            try {
                ints.add(Integer.parseInt(ch.trim()));
            } catch (NumberFormatException e) {
                nonInts.add(ch);
            }
        }
        java.util.Collections.sort(ints);
        
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < ints.size()) {
            int start = ints.get(i);
            int end = start;
            while (i + 1 < ints.size() && ints.get(i + 1) == end + 1) {
                end = ints.get(i + 1);
                i++;
            }
            if (start == end) {
                parts.add(String.valueOf(start));
            } else {
                parts.add(start + "-" + end);
            }
            i++;
        }
        parts.addAll(nonInts);
        return String.join(", ", parts);
    }

    private List<OfflineCamera> getOfflineCameras(BranchSnapshot branch) {
        List<OfflineCamera> offlineList = new ArrayList<>();
        if (branch == null || branch.getRawData() == null) {
            return offlineList;
        }
        Map<String, Object> raw = branch.getRawData();
        String cameraDetails = null;
        for (String key : new String[]{"rock_CAMERAdETAILS", "CAMERAdETAILS", "CAMERA_DETAILS"}) {
            if (raw.containsKey(key)) {
                Object val = raw.get(key);
                if (val != null) {
                    cameraDetails = val.toString();
                    break;
                }
            }
        }
        if (cameraDetails == null || !cameraDetails.startsWith("[")) {
            return offlineList;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(cameraDetails);
            if (node.isArray()) {
                for (JsonNode camera : node) {
                    if (camera == null || !camera.isObject()) {
                        continue;
                    }
                    String status = null;
                    if (camera.has("cameraStatus")) {
                        status = camera.path("cameraStatus").asText();
                    } else if (camera.has("status")) {
                        status = camera.path("status").asText();
                    } else if (camera.has("Active Status")) {
                        status = camera.path("Active Status").asText();
                    } else if (camera.has("camera_status")) {
                        status = camera.path("camera_status").asText();
                    }
                    
                    if (status != null && !status.isBlank()) {
                        String normStatus = status.trim().toLowerCase();
                        if (!"active".equals(normStatus) && !"online".equals(normStatus)) {
                            String name = null;
                            if (camera.has("Channel Name")) {
                                name = camera.path("Channel Name").asText();
                            } else if (camera.has("deviceName")) {
                                name = camera.path("deviceName").asText();
                            } else if (camera.has("channelName")) {
                                name = camera.path("channelName").asText();
                            }
                            
                            String chNo = camera.has("channelNo") ? camera.path("channelNo").asText() :
                                         (camera.has("channel_no") ? camera.path("channel_no").asText() : 
                                         (camera.has("id") ? camera.path("id").asText() : "Unknown"));
                            
                            if (name == null || name.isBlank()) {
                                name = "";
                            }
                            offlineList.add(new OfflineCamera(name.trim(), chNo.trim()));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return offlineList;
    }


    public String renderAlertStatus(BranchSnapshot branch, String label, int count) {
        return "**For Branch " + branchName(branch) + ", " + label + " is " + count + ".**";
    }

    public String renderSubsystemStatus(BranchSnapshot branch, String displayName, String stateText) {
        return "**For Branch " + branchName(branch) + ", " + displayName + " Status is " + stateText + ".**";
    }

    public String renderSubsystemFaultStatus(BranchSnapshot branch, String displayName, String stateText) {
        return "**For Branch " + branchName(branch) + ", " + displayName + " Fault Status is " + stateText + ".**";
    }

    public String renderSubsystemAlarmStatus(BranchSnapshot branch, String displayName, String stateText) {
        return "**For Branch " + branchName(branch) + ", " + displayName + " Alarm Status is " + stateText + ".**";
    }

    public String renderCctvHddErrorStatus(BranchSnapshot branch, String stateText) {
        return "**For Branch " + branchName(branch) + ", CCTV HDD Error Status is " + stateText + ".**";
    }

    private String branchName(BranchSnapshot branch) {
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

    private String trim(Double value) {
        return value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
