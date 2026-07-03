package com.seple.ThingsBoard_Bot.service.query;

public enum QueryIntent {
    GLOBAL_OVERVIEW,
    GATEWAY_STATUS,
    BATTERY_VOLTAGE,
    BATTERY_LOW_STATUS,
    AC_VOLTAGE,
    SYSTEM_CURRENT,
    ACTIVE_DEVICES,
    FAULT_DEVICES,
    OFFLINE_DEVICES,
    CONNECTED_DEVICES,
    NETWORK_STATUS,
    CCTV_STATUS,
    CCTV_HDD_ERROR_STATUS,
    CCTV_HDD_INFO,
    CCTV_RECORDING_INFO,
    ALARM_STATUS,
    ERROR_STATUS,
    SUBSYSTEM_FAULT_STATUS,
    SUBSYSTEM_ALARM_STATUS,
    SUBSYSTEM_STATUS,
    DOOR_STATUS,
    ACCESS_CONTROL_USER_COUNT,
    ACCESS_CONTROL_DEVICE_INFO,
    FAULT_REASON,
    CAMERA_DISCONNECT_HISTORY,
    BATTERY_HEALTH,
    POWER_STATUS,
    DEVICE_IMEI,
    CCTV_DEVICE_INFO,
    GPS_LOCATION,
    LAST_REPORTED,
    NETWORK_OPERATOR,
    HIERARCHY_LIST_NODES,
    HIERARCHY_BRANCHES_UNDER,
    HIERARCHY_OWNER,
    CATEGORY_HEALTH,
    BRANCH_RANKING,
    BRANCH_COMPARE,
    BRANCH_FILTER,
    GENERAL_LLM,
    HOW_TO,
    NAVIGATION,
    TROUBLESHOOTING,
    CONCEPT_EXPLAIN,
    GLOSSARY,
    OUT_OF_SCOPE,
    REFUSAL;

    /**
     * Knowledge intents are answered from static content (glossary/capability replies) and are
     * never branch-scoped: they must not trigger "which branch?" clarification, and their
     * extracted entities are terms, not branch names.
     */
    public boolean isKnowledge() {
        return this == HOW_TO || this == NAVIGATION || this == TROUBLESHOOTING
                || this == CONCEPT_EXPLAIN || this == GLOSSARY
                || this == OUT_OF_SCOPE || this == REFUSAL;
    }
}
