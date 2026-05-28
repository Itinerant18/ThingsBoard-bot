package com.seple.ThingsBoard_Bot.utility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

public class ThingsBoardTimescaleImporter {

    private final String dbUrl;
    private final String dbUser;
    private final String dbPass;
    private final ObjectMapper objectMapper;

    public ThingsBoardTimescaleImporter() throws Exception {
        Properties props = new Properties();
        try (InputStream input = ThingsBoardTimescaleImporter.class.getClassLoader().getResourceAsStream("application-dev.properties")) {
            if (input != null) {
                props.load(input);
                System.out.println("[CONFIG] Loaded DB properties from application-dev.properties");
            } else {
                throw new RuntimeException("application-dev.properties not found on classpath!");
            }
        }

        this.dbUrl = props.getProperty("spring.datasource.url").trim();
        this.dbUser = props.getProperty("spring.datasource.username").trim();
        this.dbPass = props.getProperty("spring.datasource.password").trim();
        this.objectMapper = new ObjectMapper();

        System.out.println("[CONFIG] Database URL: " + dbUrl);
        System.out.println("[CONFIG] Database User: " + dbUser);
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   ThingsBoard to TimescaleDB Cloud Importer      ");
        System.out.println("==================================================");

        try {
            ThingsBoardTimescaleImporter importer = new ThingsBoardTimescaleImporter();
            importer.executeImport();
        } catch (Exception e) {
            System.err.println("[ERROR] Import failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void executeImport() throws Exception {
        File backupFile = new File("Thingsboard-Data/thingsboard_devices_backup.json");
        if (!backupFile.exists()) {
            throw new RuntimeException("Backup file not found at " + backupFile.getAbsolutePath() + ". Please run the backup utility first!");
        }

        System.out.println("[IMPORT] Reading backup JSON file...");
        JsonNode rootNode = objectMapper.readTree(backupFile);
        if (!rootNode.isArray()) {
            throw new RuntimeException("Invalid backup format: root element is not a JSON array.");
        }

        int totalDevices = rootNode.size();
        System.out.println("[IMPORT] Found " + totalDevices + " devices in backup.");

        System.out.println("[DB] Connecting to TimescaleDB...");
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            conn.setAutoCommit(false); // Enable transactional batch insertions

            String sql = "INSERT INTO device_events (customer_id, branch_node_id, tb_message_id, log_type, field, prev_value, new_value, event_time, received_at, raw_payload) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int totalEventsInserted = 0;
                int batchCount = 0;
                Instant now = Instant.now();

                for (JsonNode device : rootNode) {
                    String deviceId = device.path("id").asText();
                    String deviceName = device.path("name").asText(); // E.g., BOI-MALDATOWN
                    
                    // Extract customer prefix (e.g., BOI, BOB) from device name
                    String customerId = "BOI";
                    if (deviceName.contains("-")) {
                        customerId = deviceName.split("-")[0].toUpperCase();
                    }

                    // We will extract and save each telemetry metric as an individual row/event
                    JsonNode telemetry = device.path("telemetry");
                    
                    // Fallback event timestamp
                    Instant eventTimestamp = now;
                    if (telemetry.has("timestamp")) {
                        try {
                            eventTimestamp = Instant.parse(telemetry.get("timestamp").asText());
                        } catch (Exception ignored) {
                            // If timestamp is epoch string or invalid, fallback to current time
                        }
                    }

                    // Iterate over all telemetry keys
                    java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = telemetry.fields();
                    while (fields.hasNext()) {
                        java.util.Map.Entry<String, JsonNode> entry = fields.next();
                        String fieldName = entry.getKey();
                        String value = entry.getValue().asText();

                        // Construct a compact raw payload to avoid OOM and database bloat
                        ObjectNode compactPayload = objectMapper.createObjectNode();
                        compactPayload.put("device_id", deviceId);
                        compactPayload.put("device_name", deviceName);
                        compactPayload.put("field", fieldName);
                        compactPayload.put("value", value);
                        String compactJson = compactPayload.toString();

                        // Truncate field and value to respect the VARCHAR(64) database constraint
                        String dbField = fieldName;
                        if (dbField != null && dbField.length() > 64) {
                            dbField = dbField.substring(0, 64);
                        }

                        String dbValue = value;
                        if (dbValue != null && dbValue.length() > 64) {
                            dbValue = dbValue.substring(0, 61) + "...";
                        }

                        pstmt.setString(1, customerId);
                        pstmt.setString(2, deviceName);
                        pstmt.setObject(3, UUID.randomUUID());
                        pstmt.setString(4, "snapshot_import");
                        pstmt.setString(5, dbField);
                        pstmt.setString(6, "N/A");
                        pstmt.setString(7, dbValue);
                        pstmt.setTimestamp(8, Timestamp.from(eventTimestamp));
                        pstmt.setTimestamp(9, Timestamp.from(now));
                        pstmt.setString(10, compactJson);

                        pstmt.addBatch();
                        batchCount++;

                        // Execute batch in chunks of 1000 to prevent OOM
                        if (batchCount % 1000 == 0) {
                            pstmt.executeBatch();
                            conn.commit();
                        }
                    }

                    totalEventsInserted += telemetry.size();
                }

                // Execute remaining elements in the batch
                if (batchCount % 1000 != 0) {
                    pstmt.executeBatch();
                    conn.commit();
                }
                System.out.println("==================================================");
                System.out.println("✅ IMPORT SUCCESSFUL!");
                System.out.println("Inserted " + totalEventsInserted + " device metrics into your TimescaleDB cloud instance.");
                System.out.println("==================================================");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
