package com.seple.ThingsBoard_Bot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.client.ThingsBoardClient;
import com.seple.ThingsBoard_Bot.model.domain.ActiveAlarm;
import com.seple.ThingsBoard_Bot.model.domain.AlertSummary;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;

import lombok.extern.slf4j.Slf4j;

/**
 * On-demand alarm fetcher with a per-device TTL cache (60 seconds).
 *
 * <p>Alarm detail is NOT loaded during the background 60-second snapshot refresh cycle.
 * Instead, it is loaded lazily when a chat query requires alarm severity data. This
 * keeps the main data pipeline fast and avoids hammering the ThingsBoard alarm API
 * for every branch on every cycle.
 *
 * <p>Call {@link #enrichWithAlarmDetail(List)} to attach severity counts and top alarms
 * to a list of snapshots before rendering an alarm-detail answer.
 */
@Slf4j
@Service
public class AlarmFetchService {

    /** Maximum alarms to retrieve per device per request. */
    private static final int PAGE_SIZE = 50;

    /** Cache TTL in milliseconds (60 seconds). */
    private static final long TTL_MS = 60_000L;

    private final ThingsBoardClient tbClient;

    /** Cache: deviceId → (fetchedAtMs, alarms). */
    private final ConcurrentHashMap<String, CachedAlarms> cache = new ConcurrentHashMap<>();

    public AlarmFetchService(ThingsBoardClient tbClient) {
        this.tbClient = tbClient;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches alarm detail for every snapshot in the list and stores it in the snapshot's
     * {@link AlertSummary}. Results are cached per device for {@value #TTL_MS} ms.
     *
     * @param snapshots branch snapshots to enrich
     */
    public void enrichWithAlarmDetail(List<BranchSnapshot> snapshots) {
        for (BranchSnapshot snap : snapshots) {
            if (snap == null) continue;
            String deviceId = deviceIdOf(snap);
            if (deviceId == null || deviceId.isBlank()) continue;

            List<ActiveAlarm> alarms = getOrFetch(deviceId);
            applyAlarmDetail(snap, alarms);
        }
    }

    /**
     * Fetches and returns all active alarms across the given snapshots, merged into one list.
     * Primarily used for fleet-wide alarm queries.
     */
    public List<ActiveAlarm> fetchAll(List<BranchSnapshot> snapshots) {
        List<ActiveAlarm> result = new ArrayList<>();
        for (BranchSnapshot snap : snapshots) {
            if (snap == null) continue;
            String deviceId = deviceIdOf(snap);
            if (deviceId == null || deviceId.isBlank()) continue;
            result.addAll(getOrFetch(deviceId));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private List<ActiveAlarm> getOrFetch(String deviceId) {
        CachedAlarms cached = cache.get(deviceId);
        if (cached != null && !cached.isExpired()) {
            return cached.alarms;
        }
        List<ActiveAlarm> fresh = tbClient.fetchAlarmsByDevice(deviceId, PAGE_SIZE);
        cache.put(deviceId, new CachedAlarms(fresh));
        return fresh;
    }

    private void applyAlarmDetail(BranchSnapshot snap, List<ActiveAlarm> alarms) {
        if (snap.getAlerts() == null) return;

        int critical = 0, major = 0, minor = 0, warning = 0;
        for (ActiveAlarm a : alarms) {
            if (a.severity() == null) continue;
            switch (a.severity().toUpperCase()) {
                case "CRITICAL" -> critical++;
                case "MAJOR"    -> major++;
                case "MINOR"    -> minor++;
                case "WARNING"  -> warning++;
                default -> { /* INDETERMINATE or unknown — skip */ }
            }
        }

        AlertSummary alerts = snap.getAlerts();
        alerts.setCriticalCount(critical);
        alerts.setMajorCount(major);
        alerts.setMinorCount(minor);
        alerts.setWarningCount(warning);
        alerts.setTopAlarms(alarms.size() > 10 ? alarms.subList(0, 10) : alarms);
        alerts.setAlarmDetailLoaded(true);
    }

    private String deviceIdOf(BranchSnapshot snap) {
        if (snap.getIdentity() != null && snap.getIdentity().getDeviceId() != null) {
            return snap.getIdentity().getDeviceId();
        }
        // Fallback: try rawData
        if (snap.getRawData() != null) {
            Object id = snap.getRawData().get("device_id");
            if (id != null) return id.toString();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Cache entry
    // -------------------------------------------------------------------------

    private record CachedAlarms(List<ActiveAlarm> alarms, long fetchedAt) {
        CachedAlarms(List<ActiveAlarm> alarms) {
            this(alarms, System.currentTimeMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() - fetchedAt > TTL_MS;
        }
    }
}
