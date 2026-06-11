package com.seple.ThingsBoard_Bot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Validates the Postgres-only SQL the application relies on but H2 cannot represent
 * (audit #11 / #7.4): the {@code jsonb} raw_payload column and the {@code SELECT DISTINCT ON}
 * "latest event per branch" query ({@link DeviceEventRepository#findLatestEventsForEachBranch}).
 *
 * <p>Run directly over JDBC against a real PostgreSQL container so a divergence between the H2
 * test database and production is caught in CI instead of in production. Auto-disabled when Docker
 * is unavailable, so it never breaks a Docker-less build but does run in CI/dev.
 */
@Testcontainers(disabledWithoutDocker = true)
class DeviceEventRepositoryPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Mirrors {@link DeviceEventRepository#findLatestEventsForEachBranch}. */
    private static final String DISTINCT_ON_SQL =
            "SELECT DISTINCT ON (branch_node_id) branch_node_id, new_value, raw_payload "
            + "FROM device_events WHERE customer_id = ? AND field = ? "
            + "ORDER BY branch_node_id, event_time DESC";

    @Test
    void jsonbRoundTripAndDistinctOnReturnsLatestPerBranch() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE device_events ("
                        + "id BIGSERIAL PRIMARY KEY, customer_id VARCHAR(64), branch_node_id VARCHAR(128), "
                        + "field VARCHAR(64), new_value TEXT, event_time TIMESTAMPTZ, raw_payload JSONB)");
                st.execute("INSERT INTO device_events (customer_id, branch_node_id, field, new_value, event_time, raw_payload) VALUES "
                        + "('BOI','BR1','gateway_status','offline','2026-01-01T00:00:00Z','{\"value\":\"offline\"}'),"
                        + "('BOI','BR1','gateway_status','online', '2026-01-02T00:00:00Z','{\"value\":\"online\"}'),"
                        + "('BOI','BR2','gateway_status','online', '2026-01-01T00:00:00Z','{\"value\":\"online\"}')");
            }

            Map<String, String> latestByBranch = new HashMap<>();
            Map<String, String> payloadByBranch = new HashMap<>();
            try (var ps = conn.prepareStatement(DISTINCT_ON_SQL)) {
                ps.setString(1, "BOI");
                ps.setString(2, "gateway_status");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        latestByBranch.put(rs.getString("branch_node_id"), rs.getString("new_value"));
                        payloadByBranch.put(rs.getString("branch_node_id"), rs.getString("raw_payload"));
                    }
                }
            }

            assertEquals(2, latestByBranch.size(), "one row per branch");
            assertEquals("online", latestByBranch.get("BR1"), "newest event wins for BR1");
            assertEquals("online", latestByBranch.get("BR2"));
            assertTrue(payloadByBranch.get("BR1").contains("online"), "jsonb payload round-trips");
        }
    }
}
