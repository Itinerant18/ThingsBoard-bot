# SAI Deployment Guide — Phases 0-5

Reference for operating the layered semantic orchestrator built across Phases 0-5. All
flags below are Spring `@ConfigurationProperties` / `@Value` — set via
`application.properties`, environment variables, or your deployment's config overlay.
Every flag defaults to safe/off behavior; nothing in this guide is required to keep the
bot running as it does today.

## Config flags introduced in this work stream

| Property | Default | Introduced | What it controls |
|---|---|---|---|
| `iotchatbot.branch-dictionary.refresh-seconds` | `300` | Phase 1 | How often the per-customer branch-name dictionary reloads from PostgreSQL. Lookups always read the cached copy, never hit the DB directly. |
| `iotchatbot.fuzzy.silent-threshold` | `0.90` | Phase 1 | Jaro-Winkler score above which a typo'd branch name resolves silently. Calibrated in Phase 5 — see `phase5_calibration_and_handoff.md`. |
| `iotchatbot.fuzzy.confirm-threshold` | `0.75` | Phase 1 | Score band where SAI asks "Did you mean X?" instead of guessing. |
| `iotchatbot.fuzzy.suggestion-floor` | `0.55` | Phase 1 | Below `confirm-threshold` but above this, SAI offers a top-3 suggestion list. Below the floor: "couldn't find that branch" (never a guess). |
| `iotchatbot.extractor.mode` | `off` | Phase 2 | `off` \| `shadow` \| `active` — see cutover procedure below. **The one flag that changes user-facing behavior.** |
| `iotchatbot.extractor.history-turns` | `4` | Phase 2 | Conversation turns sent to the LLM extractor for follow-up context ("and Bhubaneswar?"). |
| `iotchatbot.extractor.confidence-gate` | `0.60` | Phase 2 | In `active` mode, extracted intents below this confidence are dropped rather than executed. Tune from shadow-mode data (Phase 5, not yet done). |

## Required resource files (already shipped, no action needed)

- `src/main/resources/branch-aliases.properties` — shorthand overrides (BBSR, MT, HO...)
- `src/main/resources/glossary.json` — domain vocabulary definitions
- `src/main/resources/capability-replies.properties` — HOW_TO/NAVIGATION/TROUBLESHOOTING wording
- `src/main/resources/prompts/intent-extractor-prompt.txt` — extractor system prompt

All four are editable in production without a rebuild (properties/JSON files loaded at
startup) — only a restart is needed to pick up wording or alias changes.

## Extractor cutover procedure (`off` → `shadow` → `active`)

**Step 1 — off (current/default state).** No behavior change from pre-Phase-2. Deterministic
keyword+fuzzy resolution and the existing LLM fallback are exactly as before.

**Step 2 — shadow.** Set `iotchatbot.extractor.mode=shadow`. The extractor now runs
asynchronously after every deterministic resolution, purely to compare its answer against
what the resolver already decided — **zero user-facing change**, confirmed by the Phase 2
shadow-mode tests (fires on a bounded daemon pool, drops under load, never blocks the
response). Watch:
- `extractor.agreement{outcome=match|intent_mismatch|entity_mismatch|empty}` — the
  calibration signal.
- `[SHADOW] outcome=... resolver=... extractor=...` log lines for individual disagreements.

Run this for a representative volume of real traffic (see `phase5_calibration_and_handoff.md`
for the exact gate). Compute match rate = `match / (match + intent_mismatch + entity_mismatch + empty)`.

**Step 3 — active, only after match rate ≥95%.** Set `iotchatbot.extractor.mode=active`.
The extractor now drives every question the deterministic resolver could not place
(`GENERAL_LLM` or ambiguous) — its own fast path is untouched in every mode.
`REFUSAL`/`OUT_OF_SCOPE` classifications become canned replies (also caught earlier by
the Phase 3 pre-LLM safety gate, so this is defense-in-depth, not the first line).
Everything else routes through the multi-intent orchestrator with format-aware generation.

**Rollback:** set `iotchatbot.extractor.mode=off` (or `shadow`) and restart. No data
migration, no schema change — this is a pure behavior flag.

## Safety gate (always on, no flag)

`SafetyGateService` runs on every question regardless of extractor mode — it is not
behind a flag and should not be disabled. It blocks injection attempts and garbage input
before the router, resolver, extractor, or any LLM call, at zero token cost. See
`plans/phase3_glossary_safety_plan.md` for the corpus it's tested against.

## Pre-deploy checklist

1. Run `./mvnw test` — full suite must be green (351 tests as of Phase 5).
2. Run `ReleaseGateTest` specifically and check the printed scorecard — all blocking
   categories must show PASS. This is the automated ship/no-ship signal
   (`plans/regression_report.md` has the latest snapshot).
3. Confirm `iotchatbot.extractor.mode` is `off` (or intentionally `shadow`) before first
   deploy of this work stream — never start at `active` without shadow data.
