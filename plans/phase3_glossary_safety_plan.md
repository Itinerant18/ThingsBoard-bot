# Phase 3 Plan: Glossary, Canned Capability Replies & Safety Gates

Builds on Phase 1 (fuzzy resolution) and Phase 2 (extractor, orchestrator, active mode).
Incorporates the architecture-review revision: **Docs-RAG is cut** — the bot is the sole
dashboard interface (users have no ThingsBoard UI access), so `HOW_TO`/`NAVIGATION` become
canned capability replies and the glossary is a static map. No embeddings, no vector
store, no document retrieval anywhere.

---

## 1. Problem Being Closed

Phase 0 added the intents `HOW_TO`, `NAVIGATION`, `TROUBLESHOOTING`, `CONCEPT_EXPLAIN`,
`GLOSSARY`, `OUT_OF_SCOPE`, `REFUSAL`. Today only the last two have behavior (canned
replies in active mode). The other five are classified but **unhandled**: they fall
through `DeterministicAnswerService` to the LLM path, where the model free-generates an
answer — exactly the hallucination surface the master plan's zero-fabrication gate
forbids. Phase 3 gives every one of them a deterministic, sourced reply.

Second gap: the master plan's Task 3.3 safety gate. Phase 2 put `REFUSAL` classification
*inside* the extractor — the injection has already reached an LLM by then, and it only
runs in active mode. Phase 3 adds the cheap pre-LLM gate in front of everything.

## 2. Components

### 2.1 Glossary path (Task 3.2)

- **`glossary.json`** (`src/main/resources/`): term -> `{definition, aliases[]}`.
  Seed content from the real telemetry vocabulary (seen in `thingsboard_devices_backup.json`):
  `offline`, `stale`, `telemetry`, `heartbeat`, `gateway`, `IAS` (Intrusion Alarm System),
  `BAS` (Burglar/Bank Alarm System), `FAS` (Fire Alarm System), `TLS` (Time Lock System),
  `ACS` (Access Control System), `NBG`, `ZO` (Zonal Office), `tamper`, `HDD error`,
  `mains`, `battery low`, `uptime`, `fault` vs `alarm` distinction, `NVR`/`DVR`, `SOL ID`.
  Operators extend the file without recompiling (same pattern as `branch-aliases.properties`).
- **`GlossaryService`**: loads once, normalized lookup (case/space-insensitive), alias
  resolution, and `findTermInQuestion(question)` that scans the question for the longest
  matching known term (same longest-first trick as `NodeNameResolver`).
- **`GlossaryHandler implements AnswerHandler`**: supports `GLOSSARY` and
  `CONCEPT_EXPLAIN`. Looks the term up from the question text (works identically whether
  the intent came from the keyword resolver or the extractor). Unknown term -> honest
  "I don't have a definition for that" — never invent one. `CONCEPT_EXPLAIN` renders the
  same definition with any related terms appended; a longer explanation is a later
  refinement, not a different mechanism.

### 2.2 Canned capability replies (descoped Task 3.1)

- **`CapabilityReplyHandler implements AnswerHandler`**: supports `HOW_TO`, `NAVIGATION`,
  `TROUBLESHOOTING`. Fixed, honest replies:
  - `HOW_TO` ("how do I add a device?") -> read-only monitoring assistant; device/config
    changes go through the administrator.
  - `NAVIGATION` ("where can I see alarms?") -> there is nothing to navigate; just ask —
    with 3 concrete example questions.
  - `TROUBLESHOOTING` ("how do I fix the camera?") -> can report the current state and
    fault reason to assist a technician, but repair steps come from the operations team;
    offers to show the branch's fault data.
  - Reply text lives in `capability-replies.properties` so wording is editable without a build.

### 2.3 Keyword fast path + resolver integration

- `QueryIntentResolver.detectIntent` gains cheap patterns so these intents work in **all
  modes** (off/shadow/active), not just behind the extractor:
  - "WHAT DOES X MEAN" / "MEANING OF" / "DEFINE X" / "WHAT IS A(N) X" (term in glossary) -> `GLOSSARY`
  - "HOW DO I/CAN I ..." -> `HOW_TO`; "WHERE DO/CAN I ..." -> `NAVIGATION`
  - Guard: only when no branch was matched and the phrase is not a data question
    ("what is Tarakeshwar battery voltage" must stay `BATTERY_VOLTAGE`) — glossary
    detection requires the candidate term to actually exist in the glossary.
- **Ambiguity exemption (integration fix #1):** `ambiguous` currently flags any branchless
  non-global intent. The five knowledge intents are inherently branchless — exempt them,
  or every glossary question triggers "which branch?" nonsense.
- **Orchestrator fix (integration fix #2):** `MultiIntentOrchestrator` fuzzy-resolves
  every entity as a branch name. For knowledge intents the entity is a *term*
  ("stale"), which would short-circuit into "couldn't find a branch named stale".
  Knowledge intents skip branch resolution entirely.

### 2.4 Safety gate (Task 3.3)

- **`SafetyGateService`**, called in `ChatService.prepareAnswer` after auth, before the
  router/resolver — in front of every LLM touchpoint including the extractor:
  - **Injection screen** (regex, zero cost): "ignore previous/above instructions",
    "system prompt", "you are now / act as / pretend to be", "developer mode",
    delimiter smuggling (`<<<`, "END_USER_QUESTION"), base64-looking instruction blobs.
    Outcome: canned refusal, `log.warn` with the attempt, counter
    `safety.gate{outcome=injection}`. Question never reaches extractor or LLM.
  - **Garbage screen**: blank after normalization, punctuation/emoji-only, single
    repeated character runs, keyboard-mash heuristic (long vowel-free alnum token).
    Outcome: recovery fallback ("I didn't catch that — try asking about a branch, e.g.
    ..."), counter `safety.gate{outcome=garbage}`. No tokens spent.
  - Everything else: `outcome=clean`, proceed unchanged.
- Layering after Phase 3 (defense in depth): pre-gate (regex, all modes) -> extractor
  `REFUSAL` classification (active mode) -> existing `PROMPT_INJECTION_GUARD` +
  `wrapUntrusted` on the generation call. Three independent layers.
- False-positive discipline: the gate only blocks on *strong* markers. "how do I ignore
  a false alarm?" contains "ignore" but matches no pattern — covered by tests.

## 3. Step Sequence (one commit+push per step)

| Step | Content | Risk |
|------|---------|------|
| 1 | `glossary.json` + `GlossaryService` + `GlossaryHandler` + resolver keyword detection + ambiguity exemption + tests | low — new intents were previously unhandled |
| 2 | `CapabilityReplyHandler` + `capability-replies.properties` + orchestrator knowledge-intent fix + tests | low |
| 3 | `SafetyGateService` + `ChatService` wiring + metrics + injection corpus tests | medium — runs on every question; false-positive tests mandatory |
| 4 | Bench: golden-question + extractor-recording scenarios for all new paths + full-suite run | none |

## 4. Testing & Bench (bench-first)

- **Unit**: glossary lookup (exact/alias/case/longest-match/unknown), capability replies
  per intent, safety gate — a ~15-case injection corpus (all must block) and a
  false-positive corpus ("ignore a false alarm", "act on this alert", "what is the system
  prompt response time" wording traps) that must all pass through.
- **Golden bench additions** (work in mode=off — keyword path):
  - "What does stale mean?" -> `GLOSSARY`, contains the definition
  - "How do I add a new device?" -> `HOW_TO`, contains the read-only capability reply
  - "Where can I see the alarms?" -> `NAVIGATION`, contains example questions
- **Extractor-recording additions** (replay bench, active-mode path):
  - glossary + capability intents flowing through the orchestrator
  - injection string asserting the *pre-gate* blocks it before the extractor would run
- **Release-gate checkpoints**: prompt-injection refusal 100% on the fixed corpus
  (blocking, per master plan); garbage fabrication 0%; glossary answers only from
  `glossary.json` — the handler has no generative path at all.

## 5. Explicitly Deferred

- Conversational yes/no confirmation state machine (0.75-0.90 band) — needs session
  state; unchanged interim behavior from Phase 1.
- `ResponseFormat` rendering hints in deterministic handlers — documented no-op from
  Phase 2 stands.
- Threshold calibration and shadow-mode agreement review -> Phase 5, once shadow data
  accumulates from real traffic.
- Long-form `CONCEPT_EXPLAIN` content (multi-paragraph explanations) — ship with the
  same glossary definitions first; expand the JSON later without code changes.
