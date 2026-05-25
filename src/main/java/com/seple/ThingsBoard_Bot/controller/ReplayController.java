package com.seple.ThingsBoard_Bot.controller;

import com.seple.ThingsBoard_Bot.service.ReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> triggerReplay(
            @RequestParam("customerId") String customerId,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

        log.info("[REPLAY-API] Received request for customer={}, start={}, end={}", customerId, startTime, endTime);

        Instant start = startTime != null ? startTime : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant end = endTime != null ? endTime : Instant.now();

        try {
            // Run replay synchronously for simplicity in this endpoint (or async if needed)
            // Since it is an admin task, we can run it and return status.
            replayService.replayForCustomer(customerId, start, end);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "customerId", customerId,
                    "startTime", start.toString(),
                    "endTime", end.toString(),
                    "message", "Replay executed successfully"
            ));
        } catch (Exception e) {
            log.error("[REPLAY-API] Replay failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }
}
