# Phase 5: Threshold Calibration & Handoff

## Task 5.1 — Threshold Calibration

Calibration has two independent halves. One is doable offline against the regression
bench; the other requires real production traffic and has not started.

### Offline half — DONE (`ThresholdCalibrationSweepTest`, run 2026-07-08)

Grid-searched the fuzzy branch resolver's three bands (`silent-threshold`,
`confirm-threshold`, `suggestion-floor`) against two signals from the bench:

- **Recall** — do all `entity_resolution` scenarios (typo/spelling-variant branch names)
  still resolve to the *correct* branch silently? Release gate: ≥98%.
- **Fabrication safety** — does a fixed corpus of made-up branch names (`QXZPLW999`,
  `ZZZNOTREAL`, `FAKEBRANCHNAME`, ...) ever get silently `RESOLVED`? Release gate: 0%,
  always, at every threshold — this must never be threshold-dependent.

Results:

| Combo (silent / confirm / floor) | Recall | Fabrication leaks |
|---|---|---|
| 0.95 / 0.80 / 0.60 (stricter) | 85.7% (6/7) | 0 |
| **0.90 / 0.75 / 0.55 (current default)** | **100% (7/7)** | **0** |
| 0.85 / 0.70 / 0.50 (looser) | 100% (7/7) | 0 |
| 0.80 / 0.65 / 0.45 (much looser) | 100% (7/7) | 0 |
| 0.90 / 0.75 / 0.40 (wider suggestion floor) | 100% (7/7) | 0 |

**Finding:** the current default clears the 98% recall gate with margin — tightening to
0.95 breaks it (drops to 85.7%, misses a borderline typo), while loosening all the way to
0.80 still holds 100% recall with zero fabrication leaks. That means 0.90/0.75/0.55 isn't
sitting at a fragile edge; there's headroom in both directions. **No change needed —
keeping the shipped defaults.** Fabrication safety held at every point in the grid,
confirming it's governed by the floor-vs-garbage gap, not by exact threshold tuning.

Caveat: the bench's `entity_resolution` set is 7 scenarios (Tarakeshwar/Tarakeswar,
Liluah/Lilua, Bhadreswar/Bhadreswer, Bally Bazar) — enough to catch a threshold moving in
the wrong direction, not enough to detect a subtle regression. Grow this set as more
real-world typo patterns are observed in shadow mode.

### Online half — NOT STARTED (blocked on deployment)

The extractor's `confidence-gate` (default 0.60) and the eventual off→shadow→active
cutover decision can't be calibrated from a workstation — they need
`extractor.agreement{match|intent_mismatch|entity_mismatch|empty}` counts from real
questions running in `shadow` mode. Procedure once deployed:

1. Set `iotchatbot.extractor.mode=shadow` in production config. Zero user-facing change —
   confirmed by the Phase 2 shadow-mode tests (fires async, log-only).
2. Let it run against real traffic for a representative period (at minimum, enough
   volume to cover the intent taxonomy's long tail — a few hundred distinct questions is
   a reasonable first checkpoint, not a fixed day count).
3. Pull the `extractor.agreement` counters. Compute the match rate.
4. Gate: match rate ≥95% (the master plan's paraphrase gate) before flipping to `active`.
   Below that, inspect `intent_mismatch`/`entity_mismatch` log lines (`[SHADOW] outcome=...`)
   to find which intents the extractor confuses, fix the prompt or the confidence gate,
   and re-run shadow.
5. Only after the gate clears: `iotchatbot.extractor.mode=active`.

This step is explicitly deferred — it cannot be completed without a deployment.

## Task 5.2 — Handoff Documentation

See the three companion documents, written to close this task:

- **`plans/user_guide.md`** — what SAI answers, example questions by category, what it
  will decline to do and why (read-only, no navigation needed).
- **`plans/deployment_guide.md`** — every `iotchatbot.*` config flag introduced across
  Phases 0-5, the extractor mode cutover procedure, and rollback.
- **`plans/regression_report.md`** — current release scorecard snapshot (from
  `ReleaseGateTest`), what it covers, and how to extend it.
