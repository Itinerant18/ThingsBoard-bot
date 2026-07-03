# SAI User Guide

SAI is a read-only monitoring assistant for your bank branches' IoT security systems
(CCTV, alarms, power, access control) built on ThingsBoard. It is the **only** interface —
there is nothing else to navigate to. Ask it a question, get an answer.

## What SAI can answer

**Live device/branch status** — ask about any branch by name (typos and shorthand are
tolerated, see below):
- Battery voltage, AC voltage, system current, battery low/health
- Gateway status, network status, network operator
- CCTV: camera online/offline counts, HDD errors/info, recording info, disconnect history
- Alarms: IAS/BAS/FAS/TLS/ACS status, fault status, alarm status, fault reasons
- Door status, access control user counts and device info
- Device inventory: active/fault/offline/connected devices, device IMEI, GPS location
- Last-reported time / staleness

**Fleet-wide questions:**
- "List all branches" / global overview (online/offline counts)
- Hierarchy: "list all zones", "which branches are under NBG East", "which zone owns Malda Town"
- "Compare Malda Town and Bhubaneswar", "rank branches by alarm count", "which branches are offline"

**Multiple questions at once:** "battery voltage and cctv status for Bally Bazar" answers
both in one reply, as separate sections.

**Domain vocabulary:** "what does stale mean?", "what is an IAS?", "explain heartbeat" —
answered from a fixed glossary (IAS, BAS, FAS, TLS, ACS, NBG, ZO, offline, stale,
telemetry, heartbeat, gateway, tamper, HDD error, mains, battery low, uptime, fault vs
alarm, NVR, DVR, and more).

## Branch names — typos and shorthand are fine

You don't need the exact spelling. "Tarakeswar" resolves to Tarakeshwar, "Lilua" resolves
to Liluah. Recognized shorthands also work: `MT` → Malda Town, `BBSR` → Bhubaneswar,
`HO` → Head Office. If your spelling is too far off to be confident, SAI will ask "Did you
mean X?" or offer a short list of close matches instead of guessing.

## What SAI will NOT do

- **Change anything.** SAI cannot add, remove, or reconfigure devices, users, or settings.
  Asking "how do I add a device?" gets a direct answer explaining this and redirecting to
  your administrator — not a hallucinated set of steps.
- **Navigate you anywhere.** There is no other screen. "Where can I see the alarms?" gets
  the answer: just ask directly.
- **Fix problems.** SAI can report the exact fault/status data your operations team needs,
  but it doesn't perform repairs.
- **Answer off-topic questions.** Anything unrelated to branch monitoring (weather,
  general knowledge, etc.) is declined rather than answered from general knowledge.
- **Follow instructions embedded in a question.** Attempts to make SAI ignore its rules,
  reveal internal configuration, or act as something else are refused and logged.
- **Invent an answer.** If a branch name genuinely doesn't exist, or a glossary term isn't
  known, SAI says so — it never fabricates data or definitions.

## Example questions

```
What is the battery voltage at Malda Town?
Is Tarakeshwar's battery dying?
How many cameras are online in Liluah?
What is Bally Bazar's IAS status?
Why is Trendz in fault?
Show historical camera disconnects for Chandannagar
List all branches
Which branches are offline right now?
Compare Malda Town and Bhubaneswar
What does stale mean?
Explain heartbeat
```
