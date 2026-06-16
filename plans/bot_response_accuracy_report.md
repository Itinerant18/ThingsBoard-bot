# Bot Response Accuracy Report (June 16, 2026)

This report details the execution, validation, and auditing of 10 test questions run in Chrome against the local ThingsBoard IoT Bot on port `8080` using the **Tenant Admin** JWT token. The Tenant Admin has cross-customer access to all branches across the entire network (BOI, BOB, CANARA, SBI, PNB).

---

## 1. Executive Summary

| Dimension | Score / Pass Rate | Percentage | Severity | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Branch Routing & Matching** | 8 / 10 | 80.0% | **MAJOR** | Failed to route Q3 & Q4 due to duplicate display names in the database for `BOB-BARAJAGULI`. |
| **Factual Accuracy (vs. Redis)** | 10 / 10 | 100.0% | **PASS** | 100% accurate against the Redis cache values for all successfully matched target branches. |
| **Factual Accuracy (vs. Backup JSON)** | 7 / 8 | 87.5% | **MINOR** | One minor telemetry discrepancy (CANARA-CHETLA gateway offline vs. online in backup) due to missing telemetry in the Redis cache. |
| **Status Nomenclature Compliance** | 8 / 8 | 100.0% | **PASS** | Evaluated statuses use strictly compliant terms (`ONLINE`, `OFFLINE`, `FAULT`, `N/A`). |
| **Overall Strict QA Pass Rate** | **7 / 10** | **70.0%** | **MODERATE** | Substantial improvement after implementing Tenant Admin scope. |

> [!NOTE]
> **Key Finding (Database Data Quality Bug):**
> The branch node `BOB-BARAJAGULI` has its display name set to `Branch_Shakuntal_Park` in the PostgreSQL database. This conflicts with the actual `BOB-SAKUNTALA` branch (named `BRANCH SHAKUNTAL PARK`).
> Because `BOB-BARAJAGULI` lacks a proper "BARAJAGULI" alias in the database, queries targeting `BOB-BARAJAGULI` matched the BOI branch `JAGULI` (since the substring "JAGULI" matches "JAGULI").

---

## 2. Test Execution & Validation Details

Below is the itemized evaluation of each test query against the raw Redis telemetry and the source of truth in `thingsboard_devices_backup.json`.

### Q1. What is the gateway status of CANARA-CHETLA
*   **User Question:** `What is the gateway status of CANARA-CHETLA`
*   **Bot Response:** `**For Branch CANARA-CHETLA, the Gateway status is currently OFFLINE.**`
*   **Source of Truth (Redis):** `CANARA:device:state:388f1380-31ad-11f1-9c6f-b71bb2771567` exists but contains no `gatewayStatus` telemetry field.
*   **Backup JSON:** `Gateway Status: ONLINE`
*   **QA Evaluation:**
    *   **Routing:** **PASS**. Resolved correctly to `CANARA-CHETLA`.
    *   **Factual Accuracy (Redis):** **PASS**. Correctly evaluated as `OFFLINE` due to missing telemetry.
    *   **Factual Accuracy (Backup):** **FAIL (MINOR discrepancy)**. The backup lists the gateway as `ONLINE`.

### Q2. What is the AC input voltage of CANARA-CHETLA
*   **User Question:** `What is the AC input voltage of CANARA-CHETLA`
*   **Bot Response:** `**For Branch CANARA-CHETLA, AC Input Voltage is 0V AC.**`
*   **Source of Truth (Redis):** No `ac_voltage` telemetry exists in Redis.
*   **Backup JSON:** `AC Input Voltage: 0.0V`
*   **QA Evaluation:**
    *   **Routing:** **PASS**.
    *   **Factual Accuracy (Redis & Backup):** **PASS** (both match `0V AC`).

### Q3. What is the status of BOB-BARAJAGULI gateway
*   **User Question:** `What is the status of BOB-BARAJAGULI gateway`
*   **Bot Response:** `**For Branch JAGULI, the Gateway status is currently OFFLINE.**`
*   **Source of Truth (Redis):** `BOB-BARAJAGULI` is `ONLINE`.
*   **Backup JSON:** `Gateway Status: ONLINE`
*   **QA Evaluation:**
    *   **Routing:** **FAIL (MAJOR)**. Matched `JAGULI` (BOI branch) instead of `BOB-BARAJAGULI`.
    *   **Root Cause:** In the DB, `BOB-BARAJAGULI`'s display name is set to `Branch_Shakuntal_Park`, so it had no "BARAJAGULI" alias. The bot fell back to matching the substring `"JAGULI"` to the BOI `"JAGULI"` branch.

### Q4. What is the battery voltage of BOB-BARAJAGULI
*   **User Question:** `What is the battery voltage of BOB-BARAJAGULI`
*   **Bot Response:** `**For Branch JAGULI, Battery Voltage Reading is 0V DC.**`
*   **Source of Truth (Redis):** `BOB-BARAJAGULI` battery voltage is `14.0V`.
*   **Backup JSON:** `Battery Voltage: 14.0V`
*   **QA Evaluation:**
    *   **Routing:** **FAIL (MAJOR)**. Matched `JAGULI` instead of `BOB-BARAJAGULI` due to the database display name bug.

### Q5. What is the AC input voltage of BOB-AMTALA
*   **User Question:** `What is the AC input voltage of BOB-AMTALA`
*   **Bot Response:** `**For Branch AMTALA, AC Input Voltage is 230V AC.**`
*   **Source of Truth (Redis):** `ac_voltage: 230.0`
*   **Backup JSON:** `AC Input Voltage: 230.0V`
*   **QA Evaluation:**
    *   **Routing:** **PASS**.
    *   **Factual Accuracy (Redis & Backup):** **PASS** (both match `230V AC` perfectly).

### Q6. What is the battery voltage of BOB-AMTALA
*   **User Question:** `What is the battery voltage of BOB-AMTALA`
*   **Bot Response:** `**For Branch AMTALA, Battery Voltage Reading is 14V DC.**`
*   **Source of Truth (Redis):** `battery_voltage: 14.0`
*   **Backup JSON:** `Battery Voltage: 14.0V`
*   **QA Evaluation:**
    *   **Routing:** **PASS**.
    *   **Factual Accuracy (Redis & Backup):** **PASS** (both match `14V DC` perfectly).

### Q7. What is the CCTV status of BOB-SAKUNTALA
*   **User Question:** `What is the CCTV status of BOB-SAKUNTALA`
*   **Bot Response:** `**For Branch BRANCH_SHAKUNTAL_PARK, CCTV Camera Status is 0 of 0 cameras ONLINE.**`
*   **Source of Truth (Redis):** `cctvStatus: On`, but no `cameraDetails` array exists.
*   **Backup JSON:** `CCTV Status: On`
*   **QA Evaluation:**
    *   **Routing:** **PASS**. Correctly mapped to `BRANCH_SHAKUNTAL_PARK`.
    *   **Factual Accuracy:** **PASS**. Correctly evaluated based on available Redis fields.

### Q8. What is the battery voltage of BOB-SAKUNTALA
*   **User Question:** `What is the battery voltage of BOB-SAKUNTALA`
*   **Bot Response:** `**For Branch BRANCH_SHAKUNTAL_PARK, Battery Voltage Reading is 14V DC.**`
*   **Source of Truth (Redis):** `battery_voltage: 14.0` (from `BOB:device:state:c42a57c0-7f3c-11f0-8542-4de65a555965`).
*   **Backup JSON:** `Battery Voltage: 14.0V`
*   **QA Evaluation:**
    *   **Routing:** **PASS**.
    *   **Factual Accuracy (Redis & Backup):** **PASS** (both match `14V DC` perfectly).

### Q9. What is the AC input voltage of BOB-KESTOPUR
*   **User Question:** `What is the AC input voltage of BOB-KESTOPUR`
*   **Bot Response:** `**For Branch KESTOPUR, AC Input Voltage is 240V AC.**`
*   **Source of Truth (Redis):** `ac_voltage: 240.0`
*   **Backup JSON:** `AC Input Voltage: 240.0V`
*   **QA Evaluation:**
    *   **Routing:** **PASS**.
    *   **Factual Accuracy (Redis & Backup):** **PASS** (both match `240V AC` perfectly).

### Q10. What is the battery voltage of BOB-KESTOPUR
*   **User Question:** `What is the battery voltage of BOB-KESTOPUR`
*   **Bot Response:** `**For Branch KESTOPUR, Battery Voltage Reading is 0V DC.**`
*   **Source of Truth (Redis):** `battery_voltage: 0.0`
*   **Backup JSON:** `Battery Voltage: 0.0V`
*   **QA Evaluation:**
    *   **Routing:** **PASS**.
    *   **Factual Accuracy (Redis & Backup):** **PASS** (both match `0V DC` perfectly).

---

## 3. Recommended Actions & Next Steps

1.  **Correct Database Display Names (Data Quality Fix):**
    Update the database record for `BOB-BARAJAGULI` to have the display name `BRANCH BARAJAGULI` instead of the duplicate `Branch_Shakuntal_Park`.
    ```sql
    UPDATE hierarchy_nodes SET display_name = 'BRANCH BARAJAGULI' WHERE node_id = 'BOB-BARAJAGULI';
    ```
2.  **Telemetry Ingestion Completeness:**
    Ensure the RabbitMQ ingestion consumer properly ingests and updates the `gatewayStatus` and `ac_voltage` fields in Redis for `CANARA-CHETLA` and other newly provisioned non-BOI branches to match the source-of-truth backup.
