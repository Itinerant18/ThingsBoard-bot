package com.seple.ThingsBoard_Bot.tools;

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
import java.util.*;

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

            try {
                // 0. Check and Create Tables If Not Exist
                createTablesIfNotExist(conn);
                conn.commit(); // Commit DDL schema modifications

                // 1. Clear & Import Telemetry Events
                importTelemetryEvents(conn, rootNode);

                // 2. Build & Seed Hierarchy Nodes and Pre-Computed Paths
                importHierarchyAndPaths(conn, rootNode);

                conn.commit();
                System.out.println("==================================================");
                System.out.println("✅ MIGRATION & IMPORT SUCCESSFUL!");
                System.out.println("==================================================");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void createTablesIfNotExist(Connection conn) throws Exception {
        System.out.println("[DB] Checking/Creating database tables...");
        try (java.sql.Statement stmt = conn.createStatement()) {
            // Create device_events table
            stmt.execute("CREATE TABLE IF NOT EXISTS device_events (" +
                    "id BIGSERIAL, " +
                    "customer_id VARCHAR(64) NOT NULL, " +
                    "branch_node_id VARCHAR(128) NOT NULL, " +
                    "tb_message_id UUID, " +
                    "log_type VARCHAR(64), " +
                    "field VARCHAR(64), " +
                    "prev_value TEXT, " +
                    "new_value TEXT, " +
                    "event_time TIMESTAMP WITHOUT TIME ZONE NOT NULL, " +
                    "received_at TIMESTAMP WITHOUT TIME ZONE NOT NULL, " +
                    "raw_payload JSONB, " +
                    "PRIMARY KEY (id, event_time)" +
                    ")");

            // Convert to hypertable if it isn't already one
            try {
                stmt.execute("SELECT create_hypertable('device_events', 'event_time', if_not_exists => TRUE)");
                System.out.println("[DB] Timescale hypertable 'device_events' verified/created.");
            } catch (Exception e) {
                System.out.println("[DB] Note: Standard table used or hypertable conversion skipped: " + e.getMessage());
            }

            // Create hierarchy_nodes table
            stmt.execute("CREATE TABLE IF NOT EXISTS hierarchy_nodes (" +
                    "node_id VARCHAR(128) PRIMARY KEY, " +
                    "customer_id VARCHAR(64) NOT NULL, " +
                    "parent_id VARCHAR(128), " +
                    "node_type VARCHAR(32) NOT NULL, " +
                    "node_level INTEGER NOT NULL, " +
                    "display_name VARCHAR(256) NOT NULL, " +
                    "is_leaf BOOLEAN NOT NULL, " +
                    "tb_device_id UUID, " +
                    "created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            
            // Create branch_ancestor_paths table
            stmt.execute("CREATE TABLE IF NOT EXISTS branch_ancestor_paths (" +
                    "branch_node_id VARCHAR(128) PRIMARY KEY, " +
                    "customer_id VARCHAR(64) NOT NULL, " +
                    "ancestor_path VARCHAR(128)[], " +
                    "path_depth INTEGER NOT NULL, " +
                    "updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            
            System.out.println("[DB] Database tables verified/created successfully.");
        }
    }

    private void importTelemetryEvents(Connection conn, JsonNode rootNode) throws Exception {
        System.out.println("[DB] Importing device telemetry events...");
        String sql = "INSERT INTO device_events (customer_id, branch_node_id, tb_message_id, log_type, field, prev_value, new_value, event_time, received_at, raw_payload) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int totalEventsInserted = 0;
            int batchCount = 0;
            Instant now = Instant.now();

            // Delete all past events first to start clean and avoid ACCESS EXCLUSIVE lock issues
            try (PreparedStatement clearStmt = conn.prepareStatement("DELETE FROM device_events")) {
                int cleared = clearStmt.executeUpdate();
                System.out.println("[DB] Cleared " + cleared + " past events successfully.");
            }

            List<String> sourceBlocks = List.of("telemetry", "serverAttributes", "clientAttributes", "sharedAttributes");

            for (JsonNode device : rootNode) {
                String deviceId = device.path("id").asText();
                String deviceName = device.path("name").asText(); // E.g., BOI-MALDATOWN
                
                String customerId = "BOI";
                if (deviceName.contains("-")) {
                    customerId = deviceName.split("-")[0].toUpperCase();
                }

                // Try to find a timestamp inside telemetry or serverAttributes
                Instant eventTimestamp = now;
                JsonNode telemetryNode = device.path("telemetry");
                if (telemetryNode.has("timestamp")) {
                    try {
                        eventTimestamp = Instant.parse(telemetryNode.get("timestamp").asText());
                    } catch (Exception ignored) {}
                } else {
                    JsonNode serverAttrsNode = device.path("serverAttributes");
                    if (serverAttrsNode.has("timestamp")) {
                        try {
                            eventTimestamp = Instant.parse(serverAttrsNode.get("timestamp").asText());
                        } catch (Exception ignored) {}
                    }
                }

                for (String blockName : sourceBlocks) {
                    JsonNode blockNode = device.path(blockName);
                    if (blockNode.isMissingNode() || !blockNode.isObject()) {
                        continue;
                    }

                    java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = blockNode.fields();
                    while (fields.hasNext()) {
                        java.util.Map.Entry<String, JsonNode> entry = fields.next();
                        String fieldName = entry.getKey();
                        
                        // Convert complex json sub-objects/arrays to stringified representation
                        JsonNode fieldValueNode = entry.getValue();
                        String value = fieldValueNode.isContainerNode() ? fieldValueNode.toString() : fieldValueNode.asText();

                        // Construct a compact raw payload to avoid OOM and database bloat
                        ObjectNode compactPayload = objectMapper.createObjectNode();
                        compactPayload.put("device_id", deviceId);
                        compactPayload.put("device_name", deviceName);
                        compactPayload.put("field", fieldName);
                        compactPayload.put("value", value);
                        compactPayload.put("source_block", blockName);
                        String compactJson = compactPayload.toString();

                        // Truncate fields to respect database VARCHAR(64) constraints
                        String dbField = fieldName;
                        if (dbField != null && dbField.length() > 64) {
                            dbField = dbField.substring(0, 64);
                        }

                        String dbValue = value;

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

                        if (batchCount % 1000 == 0) {
                            pstmt.executeBatch();
                            conn.commit(); // Commit periodically to prevent database transaction bloat
                        }
                        totalEventsInserted++;
                    }
                }
            }

            if (batchCount % 1000 != 0) {
                pstmt.executeBatch();
            }
            conn.commit(); // Final commit for telemetry and attributes
            System.out.println("[DB] Inserted " + totalEventsInserted + " metrics/attributes successfully.");
        }
    }

    /**
     * Classifies a middle (non-root, non-leaf) hierarchy node from the leading code token of its
     * name. Returns the canonical level type (FGMO/NBG/LHO/ZO/RO/RBO/CO/HO) or {@code null} when
     * the segment does not start with a recognized level code, letting the caller fall back.
     *
     * <p>Examples: "RO KOLKATA - I" -> RO, "ZO(Kolkata)" -> ZO, "LHO Mumbai" -> LHO, "NBG JH" ->
     * NBG, "CO South" -> CO, "RBO 3" -> RBO. Bank-agnostic: each level self-declares via its name.
     */
    static String classifyMiddleNodeType(String segmentName) {
        if (segmentName == null) {
            return null;
        }
        String s = segmentName.trim().toUpperCase();
        int j = 0;
        while (j < s.length() && Character.isLetter(s.charAt(j))) {
            j++;
        }
        String code = s.substring(0, j);
        switch (code) {
            case "FGMO":
            case "NBG":
            case "LHO":
            case "ZO":
            case "RO":
            case "RBO":
            case "CO":
            case "HO":
                return code;
            case "ZONE":
                return "ZO";
            case "REGION":
                return "RO";
            default:
                return null;
        }
    }

    private static class Node {
        String nodeId;
        String customerId;
        String parentId;
        String nodeType;
        int nodeLevel;
        String displayName;
        boolean isLeaf;
        UUID tbDeviceId;
    }

    private void importHierarchyAndPaths(Connection conn, JsonNode rootNode) throws Exception {
        System.out.println("[HIERARCHY] Parsing tree structure dynamically from device backup full_path...");
        Map<String, Node> hierarchyMap = new HashMap<>();
        Set<String> activeCustomers = new HashSet<>();

        for (JsonNode device : rootNode) {
            String deviceId = device.path("id").asText();
            String deviceName = device.path("name").asText(); // E.g., BOI-MALDATOWN
            
            String customerId = "BOI";
            if (deviceName.contains("-")) {
                customerId = deviceName.split("-")[0].toUpperCase();
            }
            activeCustomers.add(customerId);

            JsonNode serverAttrs = device.path("serverAttributes");
            String branchName = serverAttrs.path("branch_name").asText("").trim();
            if (branchName.isEmpty()) {
                branchName = deviceName;
            }

            // Try to read full_path from telemetry or server attributes
            String fullPath = device.path("telemetry").path("full_path").asText("").trim();
            if (fullPath.isEmpty()) {
                fullPath = device.path("serverAttributes").path("full_path").asText("").trim();
            }

            List<String> pathSegments = new ArrayList<>();
            if (!fullPath.isEmpty()) {
                String[] parts = fullPath.split("→|->");
                for (String p : parts) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) {
                        pathSegments.add(trimmed);
                    }
                }
            }

            if (pathSegments.isEmpty()) {
                // Fallback if no full_path is available: put branch directly under a default HO root
                pathSegments.add(customerId + " Head Office");
                pathSegments.add(branchName);
            } else {
                // Ensure the root is a Head Office (HO) node
                String first = pathSegments.get(0).toUpperCase();
                boolean hasHO = first.contains("BANK") || first.contains("HO") || first.contains("HEAD OFFICE") || first.contains(customerId.toUpperCase());
                if (!hasHO) {
                    pathSegments.add(0, customerId + " Head Office");
                }
            }

            String lastParentId = null;
            for (int i = 0; i < pathSegments.size(); i++) {
                String segmentName = pathSegments.get(i);
                boolean isLeaf = (i == pathSegments.size() - 1);
                
                String nodeType;
                int nodeLevel = i + 1;
                String nodeId;
                
                if (i == 0) {
                    nodeType = "HO";
                    nodeId = customerId + "_HO";
                } else if (isLeaf) {
                    nodeType = "BRANCH";
                    nodeId = deviceName;
                } else {
                    String upper = segmentName.toUpperCase();
                    // Derive the level from the segment's own leading code token (e.g. "RO KOLKATA"
                    // -> RO, "LHO Mumbai" -> LHO, "NBG JH" -> NBG, "CO South" -> CO). This is
                    // bank-agnostic and works for every customer hierarchy (BOI/BOB/SBI/Canara/...)
                    // as long as the level code prefixes the segment name. Falls back to the legacy
                    // NBG/ZO heuristic only when a segment does not self-declare its level.
                    String classified = classifyMiddleNodeType(segmentName);
                    if (classified != null) {
                        nodeType = classified;
                    } else if (upper.contains("NBG")) {
                        nodeType = "NBG";
                    } else if (upper.contains("ZONE") || upper.contains("REGION")
                            || upper.contains("ZO") || upper.contains("RO")) {
                        nodeType = "ZO";
                    } else if (i == 1 && pathSegments.size() == 4) {
                        nodeType = "NBG";
                    } else {
                        nodeType = "ZO";
                    }
                    nodeId = customerId + "_" + nodeType + "_" + upper;
                }
                
                if (!hierarchyMap.containsKey(nodeId)) {
                    Node node = new Node();
                    node.nodeId = nodeId;
                    node.customerId = customerId;
                    node.parentId = lastParentId;
                    node.nodeType = nodeType;
                    node.nodeLevel = nodeLevel;
                    node.displayName = segmentName;
                    node.isLeaf = isLeaf;
                    if (isLeaf) {
                        try {
                            node.tbDeviceId = UUID.fromString(deviceId);
                        } catch (Exception ignored) {}
                    }
                    hierarchyMap.put(nodeId, node);
                }
                
                lastParentId = nodeId;
            }
        }

        System.out.println("[HIERARCHY] Extracted " + hierarchyMap.size() + " unique nodes. Seeding to Database...");

        // Clear past hierarchy tables first to start clean
        try (PreparedStatement clearPathsStmt = conn.prepareStatement("DELETE FROM branch_ancestor_paths")) {
            int clearedPaths = clearPathsStmt.executeUpdate();
            System.out.println("[HIERARCHY] Cleared " + clearedPaths + " ancestor paths.");
        }
        try (PreparedStatement clearNodesStmt = conn.prepareStatement("DELETE FROM hierarchy_nodes")) {
            int clearedNodes = clearNodesStmt.executeUpdate();
            System.out.println("[HIERARCHY] Cleared " + clearedNodes + " hierarchy nodes.");
        }

        // Insert hierarchy nodes
        String nodeSql = "INSERT INTO hierarchy_nodes (node_id, customer_id, parent_id, node_type, node_level, display_name, is_leaf, tb_device_id) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement nodeStmt = conn.prepareStatement(nodeSql)) {
            for (Node node : hierarchyMap.values()) {
                nodeStmt.setString(1, node.nodeId);
                nodeStmt.setString(2, node.customerId);
                nodeStmt.setString(3, node.parentId);
                nodeStmt.setString(4, node.nodeType);
                nodeStmt.setInt(5, node.nodeLevel);
                nodeStmt.setString(6, node.displayName);
                nodeStmt.setBoolean(7, node.isLeaf);
                nodeStmt.setObject(8, node.tbDeviceId);
                nodeStmt.addBatch();
            }
            nodeStmt.executeBatch();
            System.out.println("[HIERARCHY] Successfully inserted " + hierarchyMap.size() + " hierarchy nodes.");
        }

        // Compute and insert ancestor paths for leaf nodes
        System.out.println("[HIERARCHY] Computing ancestor paths for all branches...");
        String pathSql = "INSERT INTO branch_ancestor_paths (branch_node_id, customer_id, ancestor_path, path_depth) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pathStmt = conn.prepareStatement(pathSql)) {
            int pathsInserted = 0;
            for (Node node : hierarchyMap.values()) {
                if (node.isLeaf) {
                    List<String> ancestors = new ArrayList<>();
                    String currentParent = node.parentId;
                    while (currentParent != null) {
                        Node parent = hierarchyMap.get(currentParent);
                        if (parent == null) break;
                        ancestors.add(0, parent.nodeId); // Prepend to maintain root-to-leaf path order
                        currentParent = parent.parentId;
                    }

                    String[] ancestorArray = ancestors.toArray(new String[0]);
                    java.sql.Array sqlArray = conn.createArrayOf("VARCHAR", ancestorArray);

                    pathStmt.setString(1, node.nodeId);
                    pathStmt.setString(2, node.customerId);
                    pathStmt.setArray(3, sqlArray);
                    pathStmt.setInt(4, ancestors.size());
                    pathStmt.addBatch();
                    pathsInserted++;
                }
            }
            pathStmt.executeBatch();
            System.out.println("[HIERARCHY] Successfully computed and inserted " + pathsInserted + " branch ancestor paths.");
        }
    }
}
