# Format Contract

This document outlines the canonical answer formats for the ThingsBoard Bot, reviews the current compliance of the template rendering methods and intent handlers, and maps the normalizing rewrites performed by `ChatService`.

---

## The Canonical Answer Format

The canonical answer format for the entire system is defined as:

1. **Single Branch**: 
   `**For Branch [NAME], the [Metric] is [Value].**`
   * *Example*: `**For Branch BALLY BAZAR, the Gateway status is ONLINE.**`
2. **Global / Multi-Branch**:
   `**Total: X Online | Y Offline**` with **NO** per-branch header in the response.
3. **Null / N/A Policy**:
   Null or N/A values must render as `"Offline"` or `"Not Installed"` (never `"N/A"` or `"null"`).
4. **Branch Name Normalization**:
   The branch name must have any leading `"BRANCH "` and `"BOI-"` prefixes stripped.

---

## 1. AnswerTemplateService.java Methods Review

Here is the compliance analysis for every method in `AnswerTemplateService.java` that emits a user-facing string:

### `renderGlobalOverview`
*   **Signature**: `renderGlobalOverview(List<String> onlineBranches, List<String> offlineBranches)`
*   **Current Output**:
    ```
    **Total: X Online | Y Offline**
    For your question about all branches, here is the current branch-level status.
    Online:
    - [Branch A]
    ...
    ```
*   **Compliance**: **DEVIATES**
    *   *Reason*: Emits an additional explanation sentence and appends a detailed per-branch listing/headers instead of returning only the clean summary line.

### `renderGlobalOverviewFromCounters`
*   **Signature**: `renderGlobalOverviewFromCounters(int onlineCount, int offlineCount)`
*   **Current Output**:
    ```
    **Total: X Online | Y Offline**
    For your question about all branches, here is the current branch-level status.
    ```
*   **Compliance**: **DEVIATES**
    *   *Reason*: Includes the extra trailing sentence instead of returning only the clean summary line.

### `renderGatewayStatus`
*   **Signature**: `renderGatewayStatus(BranchSnapshot branch, String stateText)`
*   **Current Output**: `**For Branch [NAME], the Gateway status is currently [stateText].**`
*   **Compliance**: **DEVIATES**
    *   *Reason*: Emits `"is currently [stateText].**"` instead of the canonical `"is [stateText].**"`.

### `renderMetric`
*   **Signature**: `renderMetric(BranchSnapshot branch, String label, Double value, String unit)`
*   **Current Output**:
    *   *Value present*: `**For Branch [NAME], [label] is [value][unit].**`
    *   *Value null*: `**For Branch [NAME], [label] is N/A.**`
*   **Compliance**: **DEVIATES**
    *   *Reason*:
        1.  Missing the word `"the"` before the metric label.
        2.  Renders null/N/A values as `"N/A"` instead of `"Offline"` or `"Not Installed"`.

### `renderActiveDevices`
*   **Signature**: `renderActiveDevices(BranchSnapshot branch, List<String> activeSystems)`
*   **Current Output**: `**For Branch [NAME], Active Devices ([count]): [device1, device2...].**`
*   **Compliance**: **DEVIATES**
    *   *Reason*: Missing `"the"` before metric name, and includes count and colon details deviating from simple value mapping.

### `renderCctvStatus`
*   **Signature**: `renderCctvStatus(BranchSnapshot branch, Integer onlineCameras, Integer totalCameras)`
*   **Current Output**: `**For Branch [NAME], CCTV Camera Status is [online] of [total] cameras ONLINE.**` plus an optional block of offline cameras.
*   **Compliance**: **DEVIATES**
    *   *Reason*: Missing `"the"`, lists detailed offline cameras underneath, and uses non-canonical status wording.

### `renderAlertStatus`
*   **Signature**: `renderAlertStatus(BranchSnapshot branch, String label, int count)`
*   **Current Output**: `**For Branch [NAME], [label] is [count].**`
*   **Compliance**: **DEVIATES**
    *   *Reason*: Missing `"the"` before the metric label.

### `renderSubsystemStatus`
*   **Signature**: `renderSubsystemStatus(BranchSnapshot branch, String displayName, String stateText)`
*   **Current Output**: `**For Branch [NAME], [displayName] Status is [stateText].**`
*   **Compliance**: **DEVIATES**
    *   *Reason*: Missing `"the"` before the subsystem name, and appends `"Status"`.

### `renderSubsystemFaultStatus`
*   **Signature**: `renderSubsystemFaultStatus(BranchSnapshot branch, String displayName, String stateText)`
*   **Current Output**: `**For Branch [NAME], [displayName] Fault Status is [stateText].**`
*   **Compliance**: **DEVIATES**
    *   *Reason*: Missing `"the"`, and appends `"Fault Status"`.

### `renderSubsystemAlarmStatus`
*   **Signature**: `renderSubsystemAlarmStatus(BranchSnapshot branch, String displayName, String stateText)`
*   **Current Output**: `**For Branch [NAME], [displayName] Alarm Status is [stateText].**`
*   **Compliance**: **DEVIATES**
    *   *Reason*: Missing `"the"`, and appends `"Alarm Status"`.

### `renderCctvHddErrorStatus`
*   **Signature**: `renderCctvHddErrorStatus(BranchSnapshot branch, String stateText)`
*   **Current Output**: `**For Branch [NAME], CCTV HDD Error Status is [stateText].**`
*   **Compliance**: **DEVIATES**
    *   *Reason*: Missing `"the"`, and appends `"Error Status"`.

### Helper method: `branchName`
*   **Signature**: `branchName(BranchSnapshot branch)`
*   **Current implementation**:
    ```java
    String display = branch.getIdentity().getBranchName()
            .replaceFirst("(?i)^BRANCH\\s+", "")
            .trim();
    ```
*   **Compliance**: **DEVIATES**
    *   *Reason*: Only strips `"BRANCH "` (case-insensitively). It **does not** strip the `"BOI-"` prefix.

---

## 2. Intent Handlers Direct Outputs Review

Here is the compliance analysis for any methods in the `service/query/handler/` package that emit user-facing strings directly:

### `AccessControlHandler.java`
*   **`answerAccessControlUserCount`**:
    *   *Emits*: `**For Branch [NAME], Access Control User Count is [Value].**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and has custom fallback messages returning `"N/A"` or detailed state strings when null).
*   **`answerAccessControlDeviceInfo`**:
    *   *Emits*: `**For Branch [NAME], Access Control Device Info is: Status: [status], Model: ...**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and outputs list blocks instead of a single metric value).

### `CctvHandler.java`
*   **`answerCctvHddInfo`**:
    *   *Emits*: `**For Branch [NAME], CCTV HDD Information is: [details].**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, uses custom status labels, and returns `"not available"` on null).
*   **`answerCctvRecordingInfo`**:
    *   *Emits*: `**For Branch [NAME], CCTV Recording Information is: [details].**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and uses custom wording).
*   **`answerCameraDisconnectHistory`**:
    *   *Emits*: `**For Branch [NAME], CCTV Disconnect Status is: [channels] disconnected.**` / `**For Branch [NAME], No historical camera disconnects found.**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and completely custom phrasings).

### `DeviceInventoryHandler.java`
*   **`answerFaultDevices`**, **`answerOfflineDevices`**, & **`answerConnectedDevices`**:
    *   *Emits*: `**For Branch [NAME], [Metric] Devices ([count]): [list].**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and appends detailed lists/counts).

### `DoorStatusHandler.java`
*   **`handle`**:
    *   *Emits*: `**For Branch [NAME], Time Lock Door Status is [Value].**` / `**For Branch [NAME], Door status is not available.**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and custom fallbacks).

### `FaultReasonHandler.java`
*   **`handle`**:
    *   *Emits*: `**For Branch [NAME], there is a fault indication because [reasons].**`
    *   *Compliance*: **DEVIATES** (Does not follow the canonical single-branch metric template).

### `GlobalOverviewHandler.java`
*   **`renderGroupedGlobalOverview`**:
    *   *Emits*: Multiline Markdown summaries with collapsible sections for online and offline branches.
    *   *Compliance*: **DEVIATES** (Does not conform to the simple summary `**Total: X Online | Y Offline**` structure).

### `NetworkStatusHandler.java`
*   **`handle`**:
    *   *Emits*: `**For Branch [NAME], Network Status: ON. Network Operator: [operator].**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and formats status with colons and extra operators).

### `PowerHandler.java`
*   **`answerBatteryLowStatus`**:
    *   *Emits*: `**For Branch [NAME], Battery Low Status is [Value].**`
    *   *Compliance*: **DEVIATES** (Missing `"the"`, and returns `"N/A"` or custom warning strings).

---

## 3. ChatService.java `normalizeAnswerStyle()` Analysis

`normalizeAnswerStyle(String answer)` acts as a post-processing filter on LLM responses to align them with the legacy styling expected by the frontend.

### Regex Rewrites Documented

1.  **Bold Legacy Heading Normalization**:
    *   *Regex*: `(?i)^\\*\\*Branch\\s+([^:]+):\\s*The\\s+`
    *   *Replacement*: `**For Branch $1, `
    *   *Operation*: Matches case-insensitive strings starting with double asterisks followed by `"Branch "`, a captured branch name group, a colon, optional spacing, `"The "`, and a space. Rewrites it to the canonical bold heading start.
2.  **Plain Legacy Heading Normalization**:
    *   *Regex*: `(?i)^Branch\\s+([^:]+):\\s*The\\s+`
    *   *Replacement*: `For Branch $1, `
    *   *Operation*: Identical to rule 1 but handles headings that lack the bold double asterisks (`**`).
3.  **Bold Prefix Spacing Uniformity**:
    *   *Regex*: `(?i)^\\*\\*For\\s+Branch\\s+`
    *   *Replacement*: `**For Branch `
    *   *Operation*: Collapses uneven spacing and makes capitalization consistent at the start of bold prefixes.
4.  **Plain Prefix Spacing Uniformity**:
    *   *Regex*: `(?i)^For\\s+Branch\\s+`
    *   *Replacement*: `For Branch `
    *   *Operation*: Collapses uneven spacing and makes capitalization consistent for unbolded prefixes.
5.  **Bold Double-Branch Strip**:
    *   *Regex*: `(?i)^\\*\\*For\\s+Branch\\s+BRANCH\\s+`
    *   *Replacement*: `**For Branch `
    *   *Operation*: Removes accidental redundant repetitions of the word `"BRANCH"`, e.g. converting `**For Branch BRANCH BALLY BAZAR` to `**For Branch BALLY BAZAR`.
6.  **Plain Double-Branch Strip**:
    *   *Regex*: `(?i)^For\\s+Branch\\s+BRANCH\\s+`
    *   *Replacement*: `For Branch `
    *   *Operation*: Identical to rule 5 but matches unbolded headers.

### Redundancy & Future Clean-up Note

> [!NOTE]
> Once `system-prompt.txt` is updated to dictate the new canonical format strictly:
> ```
> **For Branch [NAME], the [Metric] is [Value].**
> ```
> The LLM will natively emit correct canonical headings. The regex rewrites in `normalizeAnswerStyle()` (which correct legacy LLM styles like `**Branch XYZ: The...`) will become entirely redundant and should be safely removed from `ChatService.java` in a future refactoring step.
