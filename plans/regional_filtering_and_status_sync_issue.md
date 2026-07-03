# Bug Report & Resolution Plan: Regional Filtering Scope Leak & Redis Status Desync

This document provides a detailed breakdown of two major issues identified in the **ThingsBoard-Bot** chatbot system:
1. **Regional/Zone Filter Bypass (NBG Scope Leak)**: Why users with limited scope (e.g., Jharkhand NBG) can see branches from other regions.
2. **Telemetry Status Desynchronization**: Why branches marked `Online` in the backup JSON are reported as `Offline` by the bot.

This document is structured specifically to serve as a high-context implementation guide for another AI coding agent.

---

## Problem 1: Regional/Zone Filter Bypass (NBG Scope Leak)

### 1. Description
When a user logs in with a regional `CUSTOMER_USER` token—such as one representing the **Jharkhand NBG** (`NBG JH`) region—the bot is supposed to filter the branch inventory and only return the **21 branches** belonging to that region. Instead, the bot returns **100 branches** across all regions (`NBG EAST`, `NBG MP`, `NBG ODISHA`, `NBG West II`, etc.). This constitutes a security scope violation and data leak.

### 2. Root Cause Analysis
The bug resides in [BranchIndexService.java](file:///d:/Ganesh/Office/ThingsBoard-Bot/src/main/java/com/seple/ThingsBoard_Bot/service/index/BranchIndexService.java#L164-L183). 
When the chatbot determines which branches are accessible to a user, it builds a lightweight branch index. During this process, it attempts to resolve the user's regional/zone scope name from their JWT token using the `resolveZoneName` method.

#### Current Buggy Code:
```java
// File: src/main/java/com/seple/ThingsBoard_Bot/service/index/BranchIndexService.java
private String resolveZoneName(String userToken) {
    String firstName = JwtParserUtil.extractClaim(userToken, "firstName");
    String lastName = JwtParserUtil.extractClaim(userToken, "lastName");
    if (firstName != null && lastName != null) {
        String combined = (firstName + " " + lastName).toUpperCase();
        if (combined.contains("ZO ")) {
            return combined.substring(combined.indexOf("ZO ")).trim(); // e.g., "ZO HOWRAH"
        }
    }
    String sub = JwtParserUtil.extractClaim(userToken, "sub");
    if (sub != null && sub.contains("@")) {
        String prefix = sub.split("@")[0].toUpperCase();
        if (prefix.contains(".")) {
            String firstPart = prefix.split("\\.")[0];
            return "ZO " + firstPart; // e.g. "ZO HOWRAH"
        }
        return "ZO " + prefix;
    }
    return null;
}
```

#### The Failure Path:
1. For a **Jharkhand NBG** user, the token contains `firstName = "BOI NBG"` and `lastName = "Jh"` (or similar).
2. The combined string `combined = "BOI NBG JH"` is checked: `combined.contains("ZO ")` is **`false`** (since it contains `NBG`, not `ZO`).
3. The code falls back to parsing the `sub` claim (e.g., `nb.jh@bankofindia.bank.in`).
4. The prefix `nb.jh` is split by `.`, giving `firstPart = "nb"`.
5. The method returns **`"ZO NB"`**.
6. Back in `refreshIndex(userToken)`, the code queries the hierarchy repository for a node with displayName or ID matching `"ZO NB"`:
   ```java
   Optional<HierarchyNode> zoNodeOpt = hierarchyNodeRepository.findAll().stream()
           .filter(node -> "ZO".equalsIgnoreCase(node.getNodeType()) && 
                           (node.getDisplayName().equalsIgnoreCase(zoneName) || 
                            node.getNodeId().toUpperCase().contains(upperZone.replace(" ", "_"))))
           .findFirst();
   ```
7. Since no node named `"ZO NB"` exists in the database, `zoNodeOpt.isPresent()` returns **`false`**.
8. The regional filter is completely skipped, causing the service to return the **entire nationwide list of 100+ branches** instead of just the 21 Jharkhand branches.

*Note: A similar method `resolveRegionName(userToken)` in [UserDataService.java](file:///d:/Ganesh/Office/ThingsBoard-Bot/src/main/java/com/seple/ThingsBoard_Bot/service/UserDataService.java#L204-L230) handles this correctly because it checks all regional prefixes: `List.of("FGMO", "LHO", "ZO", "CO", "RO", "RBO", "NBG")`.*

---

## Problem 2: Telemetry Status Desynchronization (Redis vs. Backup JSON)

### 1. Description
Even if we look only at the Jharkhand NBG branches, the bot reports **`0 Online | 21 Offline`**, whereas the backup JSON contains **`2 Online | 19 Offline`** branches. Specifically, `BRANCH CHHOTA GAMHARIA` (`BOI-CHHOTAGAMHARIA`) and `ZO RANCHI` (`BOI-DX7`) are listed in the bot's `Offline` collapsible section, despite having `gateway_sts = "Online"` in the backup file.

### 2. Root Cause Analysis
The bot's code evaluates branch statuses based on data stored in the **Redis Cache** (or TimescaleDB database).
* In [FieldPrecedenceResolver.java](file:///d:/Ganesh/Office/ThingsBoard-Bot/src/main/java/com/seple/ThingsBoard_Bot/service/normalization/FieldPrecedenceResolver.java#L20-L25), the gateway status is resolved *only* from the `gateway_sts` server attribute.
* If a branch's real-time state is missing from Redis, or if the `gateway_sts` cache key has expired/never been initialized, the system falls back to `NormalizedState.UNKNOWN`.
* The chatbot's deterministic aggregator [GlobalOverviewHandler.java](file:///d:/Ganesh/Office/ThingsBoard-Bot/src/main/java/com/seple/ThingsBoard_Bot/service/query/handler/GlobalOverviewHandler.java#L131-L137) treats `UNKNOWN` as **`Offline`**.
* Thus, because Redis is desynchronized or unpopulated during local/staging execution, the bot reports all branches as offline (`0 Online`).

---

### 3. Solution (Action Plan)
1. **Cache Ingestion Validation**: Ensure that the background sync task (or data ingestion pipeline) captures and writes `gateway_sts` and other `_sts` attributes into the Redis hash keys: `BOI:device:state:<deviceId>`.
2. **Dev/Test Data Preloading**: Implement a developer utility script (or JUnit listener) that parses `thingsboard_devices_backup.json` and populates the local Redis instance with these correct attributes at startup.

#### Redis Key Format Example:
```redis
# Hash key for BOI-CHHOTAGAMHARIA state
HSET "BOI:device:state:efc737f0-dd78-11f0-93b7-0f70161fd1f6" "gateway_sts" "Online"
HSET "BOI:device:state:efc737f0-dd78-11f0-93b7-0f70161fd1f6" "status" "Healthy"
```

---

## Summary of Verification Steps for the Next Agent
1. **Apply the scope resolution fix** in `BranchIndexService.java`.
2. **Write a unit test** in `BranchIndexServiceTest.java` verifying that a token with `firstName: "BOI NBG"`, `lastName: "Jh"` resolves to a list of only 21 devices instead of all 100.
3. **Populate Redis** with the backup JSON data (ensuring `gateway_sts = "Online"` is written for `BOI-CHHOTAGAMHARIA` and `BOI-DX7`).
4. **Run the local server** and ask: `"how many branches i have"`.
5. **Verify the expected response**:
   * Total branches listed must be exactly **21**.
   * Status must read: **`Total: 2 Online | 19 Offline`**.
   * `BRANCH CHHOTA GAMHARIA` and `ZO RANCHI` must be in the `Online` section, and not in the `Offline` section.
