package com.seple.ThingsBoard_Bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ThingsBoardBackupUtility {

    private static final String DEFAULT_URL = "https://seple.iot-private.cloud";
    private static final String DEFAULT_USER = "info@seple.in";
    private static final String DEFAULT_PASS = "@bcde9894";

    private final String url;
    private final String username;
    private final String password;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String jwtToken;

    public ThingsBoardBackupUtility() throws Exception {
        Properties props = new Properties();
        try (InputStream input = ThingsBoardBackupUtility.class.getClassLoader().getResourceAsStream("application-dev.properties")) {
            if (input != null) {
                props.load(input);
                System.out.println("[CONFIG] Loaded properties from application-dev.properties");
            } else {
                System.out.println("[CONFIG] Warning: application-dev.properties not found on classpath. Using defaults.");
            }
        }

        this.url = props.getProperty("iotchatbot.thingsboard.url", DEFAULT_URL).trim();
        this.username = props.getProperty("iotchatbot.thingsboard.username", DEFAULT_USER).trim();
        this.password = props.getProperty("iotchatbot.thingsboard.password", DEFAULT_PASS).trim();

        System.out.println("[CONFIG] ThingsBoard Host URL: " + this.url);
        System.out.println("[CONFIG] ThingsBoard Username: " + this.username);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   ThingsBoard Standalone JSON Backup Utility    ");
        System.out.println("==================================================");

        try {
            ThingsBoardBackupUtility utility = new ThingsBoardBackupUtility();
            utility.executeBackup();
        } catch (Exception e) {
            System.err.println("[ERROR] Backup execution failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void executeBackup() throws Exception {
        // Step 1: Authenticate
        authenticate();

        // Step 2: Fetch all devices (pagination)
        System.out.println("[SYNC] Querying all tenant devices...");
        List<JsonNode> devices = fetchAllDevices();
        System.out.println("[SYNC] Found " + devices.size() + " devices in tenant.");

        // Step 3: Fetch telemetry & attributes for each device
        ArrayNode backupArray = objectMapper.createArrayNode();
        int total = devices.size();
        int current = 0;

        for (JsonNode device : devices) {
            current++;
            String deviceId = device.get("id").asText();
            String deviceName = device.get("name").asText();
            String deviceType = device.get("type").asText();

            System.out.printf("[%d/%d] Fetching data for device: %s (ID: %s, Type: %s)\n",
                    current, total, deviceName, deviceId, deviceType);

            // Fetch attributes & telemetry with pacing and retry protection
            JsonNode telemetry = fetchWithRetry("/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries");
            JsonNode serverAttrs = fetchWithRetry("/api/plugins/telemetry/DEVICE/" + deviceId + "/values/attributes/SERVER_SCOPE");
            JsonNode clientAttrs = fetchWithRetry("/api/plugins/telemetry/DEVICE/" + deviceId + "/values/attributes/CLIENT_SCOPE");
            JsonNode sharedAttrs = fetchWithRetry("/api/plugins/telemetry/DEVICE/" + deviceId + "/values/attributes/SHARED_SCOPE");

            ObjectNode deviceRecord = objectMapper.createObjectNode();
            deviceRecord.put("id", deviceId);
            deviceRecord.put("name", deviceName);
            deviceRecord.put("type", deviceType);
            deviceRecord.set("telemetry", telemetry != null ? flattenJsonNode(parseTelemetryValues(telemetry)) : objectMapper.createObjectNode());
            deviceRecord.set("serverAttributes", serverAttrs != null ? flattenJsonNode(parseAttributeValues(serverAttrs)) : objectMapper.createObjectNode());
            deviceRecord.set("clientAttributes", clientAttrs != null ? flattenJsonNode(parseAttributeValues(clientAttrs)) : objectMapper.createObjectNode());
            deviceRecord.set("sharedAttributes", sharedAttrs != null ? flattenJsonNode(parseAttributeValues(sharedAttrs)) : objectMapper.createObjectNode());

            backupArray.add(deviceRecord);

            // Pacing delay (50ms) to respect rate limits
            Thread.sleep(50);
        }

        // Step 4: Write to file
        File outputDir = new File("Thingsboard-Data");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File outputFile = new File(outputDir, "thingsboard_devices_backup.json");
        objectMapper.writeValue(outputFile, backupArray);

        System.out.println("==================================================");
        System.out.println("✅ BACKUP SUCCESSFUL!");
        System.out.println("File saved to: " + outputFile.getAbsolutePath());
        System.out.println("==================================================");
    }

    private void authenticate() throws Exception {
        String loginUrl = url + "/api/auth/login";
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode node = objectMapper.readTree(response.body());
            this.jwtToken = node.get("token").asText();
            System.out.println("[AUTH] Authenticated with ThingsBoard successfully.");
        } else {
            throw new RuntimeException("ThingsBoard authentication failed with HTTP code: " + response.statusCode() + ", body: " + response.body());
        }
    }

    private List<JsonNode> fetchAllDevices() throws Exception {
        List<JsonNode> devicesList = new ArrayList<>();
        int page = 0;
        int pageSize = 100;
        boolean hasNext = true;

        while (hasNext) {
            String pageUrl = url + "/api/tenant/devices?pageSize=" + pageSize + "&page=" + page;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pageUrl))
                    .header("X-Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch devices page " + page + ": HTTP " + response.statusCode());
            }

            JsonNode pageNode = objectMapper.readTree(response.body());
            JsonNode dataArray = pageNode.get("data");
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode deviceNode : dataArray) {
                    ObjectNode simplified = objectMapper.createObjectNode();
                    simplified.put("id", deviceNode.get("id").get("id").asText());
                    simplified.put("name", deviceNode.get("name").asText());
                    simplified.put("type", deviceNode.get("type").asText());
                    devicesList.add(simplified);
                }
            }

            hasNext = pageNode.get("hasNext").asBoolean(false);
            page++;
        }

        return devicesList;
    }

    private JsonNode fetchWithRetry(String path) {
        int maxRetries = 3;
        long backoffMs = 1000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + path))
                        .header("X-Authorization", "Bearer " + jwtToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return objectMapper.readTree(response.body());
                } else if (response.statusCode() == 429) {
                    System.out.printf("[WARN] Rate limited (429) on path %s. Retry attempt %d/%d after backoff...\n",
                            path, attempt, maxRetries);
                    Thread.sleep(backoffMs);
                    backoffMs *= 2; // Exponential backoff
                } else {
                    System.err.printf("[WARN] HTTP error %d on path %s. Attempt %d/%d...\n",
                            response.statusCode(), path, attempt, maxRetries);
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                System.err.printf("[WARN] Request failed on path %s (Attempt %d/%d): %s\n",
                        path, attempt, maxRetries, e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
        System.err.printf("[ERROR] Failed to fetch path %s after %d attempts.\n", path, maxRetries);
        return null;
    }

    private JsonNode parseTelemetryValues(JsonNode telemetryResponse) {
        ObjectNode parsed = objectMapper.createObjectNode();
        if (telemetryResponse == null) return parsed;

        telemetryResponse.fields().forEachRemaining(entry -> {
            JsonNode array = entry.getValue();
            if (array.isArray() && !array.isEmpty()) {
                JsonNode latestPoint = array.get(0);
                if (latestPoint.has("value")) {
                    parsed.set(entry.getKey(), latestPoint.get("value"));
                }
            }
        });
        return parsed;
    }

    private JsonNode parseAttributeValues(JsonNode attributeResponse) {
        ObjectNode parsed = objectMapper.createObjectNode();
        if (attributeResponse == null) return parsed;

        if (attributeResponse.isArray()) {
            for (JsonNode attr : attributeResponse) {
                if (attr.has("key") && attr.has("value")) {
                    parsed.set(attr.get("key").asText(), attr.get("value"));
                }
            }
        }
        return parsed;
    }

    JsonNode flattenJsonNode(JsonNode root) {
        if (root == null || !root.isObject()) {
            return root;
        }
        ObjectNode result = objectMapper.createObjectNode();
        root.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            
            // Put the original key-value pair
            result.set(key, value);
            
            // 1. If it's a stringified JSON, parse it first
            JsonNode parsedChild = null;
            if (value.isTextual()) {
                String text = value.asText().trim();
                if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                    try {
                        parsedChild = objectMapper.readTree(text);
                    } catch (Exception ignored) {
                        // Not a valid JSON
                    }
                }
            } else if (value.isObject()) {
                // 2. If it's already a JSON object
                parsedChild = value;
            }
            
            // If we successfully parsed/identified a child object, flatten its fields
            if (parsedChild != null && parsedChild.isObject()) {
                parsedChild.fields().forEachRemaining(childEntry -> {
                    String childKey = childEntry.getKey();
                    JsonNode childValue = childEntry.getValue();
                    String newKey = key + "_" + childKey;
                    
                    if (childValue.isContainerNode()) {
                        // Serialize arrays/objects as JSON strings for mapping compatibility
                        result.put(newKey, childValue.toString());
                    } else {
                        // Primitives
                        result.set(newKey, childValue);
                    }
                });
            }
        });
        return result;
    }
}
