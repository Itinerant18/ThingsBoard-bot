# Coverage Matrix

This coverage matrix traces user questions through the intent detection engine of the ThingsBoard Bot and details the answering path, handler, and drift status.

## Questions & Intents Coverage Table

| Question (verbatim) | Expected answer | Detected intent (trace through detectIntent) | Answering path (SIMPLE_REDIS / deterministic handler / LLM) | Handler + template method | Status (MATCH / FORMAT_DRIFT / MISSING_OR_WRONG_INTENT) | Fix type (prompt / intent / handler / frontend) |
|---|---|---|---|---|---|---|
| **Gateway (Branch System)** | | | | | | |
| Q1. What is the Branch (Name) Gateway status right now? | The Branch Gateway status is currently ONLINE. All systems are operational and functioning normally. | `GATEWAY_STATUS` | deterministic handler | `GatewayStatusHandler` + `AnswerTemplateService.renderGatewayStatus` | FORMAT_DRIFT | handler |
| Q2. What is the current status of the Branch (Name) Gateway? | Current Branch Gateway Status: ACTIVE. Network connectivity is stable with no reported issues. | `GATEWAY_STATUS` | deterministic handler | `GatewayStatusHandler` + `AnswerTemplateService.renderGatewayStatus` | FORMAT_DRIFT | handler |
| Q3. Is the Branch (Name) Gateway working properly? | Yes, the Branch Gateway is working properly. All connected devices are communicating normally. | `GATEWAY_STATUS` | deterministic handler | `GatewayStatusHandler` + `AnswerTemplateService.renderGatewayStatus` | FORMAT_DRIFT | handler |
| Q4. What is the Branch (Name) Gateway battery voltage right now? | Current Battery Voltage: 13.6V DC. | `BATTERY_VOLTAGE` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q5. What is the current battery voltage of the Branch (Name) Gateway? | Battery Voltage Reading: 13.6V DC. | `BATTERY_VOLTAGE` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q6. How much battery voltage does the Branch (Name) Gateway have? | The Branch Gateway currently has 13.6V DC battery voltage. | `BATTERY_VOLTAGE` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q7. What is the Branch (Name) Gateway AC voltage right now? | AC Input Voltage: 220V AC. | `AC_VOLTAGE` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q8. What is the AC input voltage of the Branch (Name) Gateway? | The current AC input voltage is 220V AC. | `AC_VOLTAGE` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q9. How much AC voltage is coming to the Branch (Name) Gateway? | AC Voltage Input: 220V AC. | `AC_VOLTAGE` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q10. What is the Branch Gateway (Name) battery low status right now? | Battery Low Status: NORMAL. No low battery warning active. | `BATTERY_LOW_STATUS` | deterministic handler | `PowerHandler` + `PowerHandler.answerBatteryLowStatus` | FORMAT_DRIFT | handler |
| Q11. Is the Branch (Name) Gateway battery low? | No, the battery is NOT low. | `BATTERY_LOW_STATUS` | deterministic handler | `PowerHandler` + `PowerHandler.answerBatteryLowStatus` | FORMAT_DRIFT | handler |
| Q12. Is there a low battery warning on the Branch (Name) Gateway? | No low battery warning is currently active. Battery is in healthy condition. | `BATTERY_LOW_STATUS` | deterministic handler | `PowerHandler` + `PowerHandler.answerBatteryLowStatus` | FORMAT_DRIFT | handler |
| Q13. What is the Branch (Name) Gateway battery status right now? | Battery Status: HEALTHY. Voltage: 13.6V DC. | `BATTERY_HEALTH` | deterministic handler | `PowerHandler` + `PowerHandler.answerBatteryHealth` | MATCH |  |
| Q14. How is the battery condition of the Branch (Name) Gateway? | Battery Condition: GOOD. The battery is fully charged and operating within normal parameters. | `BATTERY_HEALTH` | deterministic handler | `PowerHandler` + `PowerHandler.answerBatteryHealth` | MATCH |  |
| Q15. Is the Branch (Name) Gateway battery healthy? | Yes, the Branch Gateway battery is healthy. Voltage reading is normal at 13.6V DC | `BATTERY_HEALTH` | deterministic handler | `PowerHandler` + `PowerHandler.answerBatteryHealth` | MATCH |  |
| Q16. What are the active devices in the Branch (Name) Gateway right now? | Active Devices (4): CCTV DVR, IAS Panel, FAS Panel, Access Control Controller. All responding normally. | `ACTIVE_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `AnswerTemplateService.renderActiveDevices` | FORMAT_DRIFT | handler |
| Q17. Which devices are currently active on the Branch (Name) Gateway? | Currently Active: CCTV DVR, IAS Panel, FAS Panel, Access Control Controller — 4 devices active. | `ACTIVE_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `AnswerTemplateService.renderActiveDevices` | FORMAT_DRIFT | handler |
| Q18. Show active devices on the Branch (Name) Gateway | Active Devices List: 1. CCTV DVR (Online), 2. IAS Panel (Online), 3. FAS Panel (Online), 4. Access Control (Online). | `ACTIVE_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `AnswerTemplateService.renderActiveDevices` | FORMAT_DRIFT | handler |
| Q19. What are the fault devices in the Branch (Name) Gateway right now? | Fault Devices: 1 device — BAS Panel. Requires inspection. | `FAULT_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerFaultDevices` | FORMAT_DRIFT | handler |
| Q20. Are there any faulty devices in the Branch (Name) Gateway? | Yes, 1 faulty device detected: BAS Panel. Other devices are functioning normally. | `FAULT_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerFaultDevices` | FORMAT_DRIFT | handler |
| Q21. Show fault devices in the Branch Gateway | Fault Device Report: BAS Panel. | `FAULT_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerFaultDevices` | FORMAT_DRIFT | handler |
| Q22. What are the offline devices in the Branch (Name) Gateway right now? | Offline Devices: 1 device — Time Lock Controller. Last online: 3 hours ago. | `OFFLINE_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerOfflineDevices` | FORMAT_DRIFT | handler |
| Q23. Which devices are offline on the Branch Gateway? | Currently Offline: Time Lock Controller. All other devices are online. | `OFFLINE_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerOfflineDevices` | FORMAT_DRIFT | handler |
| Q24. Show offline devices in the Branch (Name) Gateway | Offline Device: Time Lock Controller — Status: OFFLINE. Possible cause: Power interruption or network issue. | `OFFLINE_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerOfflineDevices` | FORMAT_DRIFT | handler |
| Q25. What are all connected devices in the Branch (Name) Gateway right now? | All Connected Devices (6): CCTV DVR, IAS Panel, FAS Panel, Access Control, BAS Panel, Time Lock Controller. | `CONNECTED_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerConnectedDevices` | FORMAT_DRIFT | handler |
| Q26. Show all connected devices in the Branch (Name) Gateway | Connected Devices: 1. CCTV DVR, 2. IAS Panel, 3. FAS Panel, 4. Access Control, 5. BAS Panel, 6. Time Lock Controller. | `CONNECTED_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerConnectedDevices` | FORMAT_DRIFT | handler |
| Q27. List all devices connected to the Branch (Name) Gateway | Total 6 devices registered: CCTV DVR (Active), IAS Panel (Active), FAS Panel (Active), Access Control (Active), BAS Panel (Fault), Time Lock (Offline). | `CONNECTED_DEVICES` | deterministic handler | `DeviceInventoryHandler` + `DeviceInventoryHandler.answerConnectedDevices` | FORMAT_DRIFT | handler |
| Q28. What is the Branch (Name) Gateway power status right now? | Power Status: ON. AC Mains: 220V (Normal), Battery Backup: 13.6V (Charged). | `POWER_STATUS` | deterministic handler | `PowerHandler` + `PowerHandler.answerPowerStatus` | MATCH |  |
| Q29. Is the Branch (Name) Gateway power on or off? | Branch Gateway Power: ON. Running on AC Mains power. Battery backup is fully charged and ready. | `POWER_STATUS` | deterministic handler | `PowerHandler` + `PowerHandler.answerPowerStatus` | MATCH |  |
| Q30. Check power status of the Branch (Name) Gateway | Power Check Result: AC Input: 220V , Battery: 13.6V , Power Status: NORMAL — No power issues detected. | `POWER_STATUS` | deterministic handler | `PowerHandler` + `PowerHandler.answerPowerStatus` | MATCH |  |
| Q31. What is the Branch (Name) Gateway network status right now? | Network Status: ON. Network Operator: Airtel. | `NETWORK_STATUS` | deterministic handler | `NetworkStatusHandler` + `NetworkStatusHandler.handle` | MATCH | Operator is data-dependent (real for most branches, ON-only for NA branches) |
| Q32. Is the Branch (Name) Gateway online or offline? | The Branch Gateway is ON. Network connection is active and stable. Network Operator: Airtel. | `GATEWAY_STATUS` | deterministic handler | `GatewayStatusHandler` + `AnswerTemplateService.renderGatewayStatus` | MISSING_OR_WRONG_INTENT | intent |
| Q33. Check network connectivity of the Branch (Name) Gateway | Network Check: Status: On. Network Operator: Airtel. | `NETWORK_STATUS` | deterministic handler | `NetworkStatusHandler` + `NetworkStatusHandler.handle` | MATCH | Operator is data-dependent (real for most branches, ON-only for NA branches) |
| Q34. What is the System current status of the Branch (Name) Gateway right now? | System current status: 2.0 Amp. | `SYSTEM_CURRENT` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q35. Give me the System current status of the Branch (Name) Gateway | System current status — 2.0 Amp. | `SYSTEM_CURRENT` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q36. Show overall System current status of the Branch (Name) Gateway | System current status: 2.0 Amp. | `SYSTEM_CURRENT` | deterministic handler | `PowerHandler` + `AnswerTemplateService.renderMetric` | FORMAT_DRIFT | handler |
| Q37. What is the status of all devices in all branches right now? | All Branch Status — Branch A: 4 Online, 1 Offline \| Branch B: 6 Online \| Branch C: 5 Online, 1 Offline. | `GLOBAL_OVERVIEW` | deterministic handler | `GlobalOverviewHandler` + `GlobalOverviewHandler.handle` | FORMAT_DRIFT | handler |
| Q38. Show status of all branch devices | Multi-Branch Summary: Branch A: Online (1 Offline), Branch B: 6 Online, Branch C: Online (1 offline). | `GLOBAL_OVERVIEW` | deterministic handler | `GlobalOverviewHandler` + `GlobalOverviewHandler.handle` | FORMAT_DRIFT | handler |
| Q39. Display all device statuses across branches | Cross-Branch Device Report: 15 devices total — 13 Online, 1 Offline (BAS Panel, Branch A), 1 Offline (Time Lock, Branch A). | `GLOBAL_OVERVIEW` | deterministic handler | `GlobalOverviewHandler` + `GlobalOverviewHandler.handle` | FORMAT_DRIFT | handler |
| Q40. What is the Branch (Name) Gateway alarm status right now? | Alarm Status: NO ACTIVE ALARMS. All monitored systems are within normal operating thresholds. | `ALARM_STATUS` | deterministic handler | `AlertHandler` + `AnswerTemplateService.renderAlertStatus` | FORMAT_DRIFT | handler |
| Q41. Is there any alarm in the Branch (Name) Gateway? | No alarms currently active in the Branch Gateway. System is operating normally. | `ALARM_STATUS` | deterministic handler | `AlertHandler` + `AnswerTemplateService.renderAlertStatus` | FORMAT_DRIFT | handler |
| Q42. Check alarm condition of the Branch (Name) Gateway | Alarm Check Result: CLEAR. No alarms triggered. | `ALARM_STATUS` | deterministic handler | `AlertHandler` + `AnswerTemplateService.renderAlertStatus` | FORMAT_DRIFT | handler |
| Q43. What is the Branch (Name) Gateway error status right now? | Error Status: 1 Error — BAS Panel. All other systems error-free. | `ERROR_STATUS` | deterministic handler | `AlertHandler` + `AnswerTemplateService.renderAlertStatus` | FORMAT_DRIFT | handler |
| Q44. Are there any errors in the Branch (Name) Gateway? | Yes, 1 error: BAS Panel. Recommend checking BAS panel. | `ERROR_STATUS` | deterministic handler | `AlertHandler` + `AnswerTemplateService.renderAlertStatus` | FORMAT_DRIFT | handler |
| Q45. Show error status of the Branch (Name) Gateway | Error Report: BAS Panel. | `ERROR_STATUS` | deterministic handler | `AlertHandler` + `AnswerTemplateService.renderAlertStatus` | FORMAT_DRIFT | handler |
| **CCTV System** | | | | | | |
| Q1. What is the Branch (Name) CCTV power status right now? | CCTV Power Status: ON. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MATCH |  |
| Q2. Is the Branch (Name) CCTV system powered on? | Yes, the CCTV system is powered ON. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MATCH |  |
| Q3. Check CCTV power status Branch (Name). | CCTV Power Check: ON. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MATCH |  |
| Q4. What is the Branch (Name) CCTV camera status right now? | CCTV Camera Status: 8 cameras are ONLINE. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | FORMAT_DRIFT | handler |
| Q5. Are the Branch (Name) CCTV cameras working properly? | 8 cameras are working properly. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | FORMAT_DRIFT | handler |
| Q6. Show CCTV camera status Branch (Name). | Camera Status Report: CAM1: Online, CAM2: Online, CAM3: Online, CAM4: Online, CAM5: Online, CAM6: Online, CAM7: Online, CAM8: Online. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | FORMAT_DRIFT | handler |
| Q7. What is the Branch (Name) CCTV HDD Error right now? | CCTV HDD Error Status: No Error. | `CCTV_HDD_ERROR_STATUS` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvHddErrorStatus` | FORMAT_DRIFT | handler |
| Q8. Is the Branch (Name) CCTV HDD working properly? | Yes, the CCTV HDD is working properly. No Error. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MISSING_OR_WRONG_INTENT | intent |
| Q9. Check HDD Error status of CCTV system of Branch (Name). | HDD Error Check: Status: Healthy, No Error. | `CCTV_HDD_ERROR_STATUS` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvHddErrorStatus` | FORMAT_DRIFT | handler |
| Q10. What is the Branch (Name) CCTV alarm status right now? | CCTV Alarm Status: NVR/DVR Off. Off Time- 2026-03-15 19:57:51. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q11. Is there Branch (Name) any CCTV alarm? | Yes, NVR/DVR Off. Off Time- 2026-03-15 19:57:51. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q12. Check CCTV alarm condition of Branch (Name). | CCTV Alarm Check: NVR/DVR Off. Off Time- 2026-03-15 19:57:51. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q13. What is the Branch (Name) CCTV disconnect status right now? | CCTV Disconnect Status: 1 camera disconnected — Channel 3. | `CAMERA_DISCONNECT_HISTORY` | deterministic handler | `CctvHandler` + `CctvHandler.answerCameraDisconnectHistory` | FORMAT_DRIFT | handler |
| Q14. Branch (Name) Are any CCTV devices disconnected? | Yes, Channel 3 is disconnected. all other 7 cameras are connected and working. | `CAMERA_DISCONNECT_HISTORY` | deterministic handler | `CctvHandler` + `CctvHandler.answerCameraDisconnectHistory` | FORMAT_DRIFT | handler |
| Q15. Show disconnected CCTV devices of Branch (Name). | Disconnected CCTV Devices: Channel 3. | `CAMERA_DISCONNECT_HISTORY` | deterministic handler | `CctvHandler` + `CctvHandler.answerCameraDisconnectHistory` | FORMAT_DRIFT | handler |
| Q16. What is the Branch (Name) CCTV HDD information right now? | CCTV HDD Status: HEALTHY. HDD Slot: Slot no, Capacity: Hdd Memory, Used: Memory Use(%), Free: Memory Free (%). | `CCTV_HDD_INFO` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvHddInfo` | FORMAT_DRIFT | handler |
| Q17. Show CCTV HDD details of Branch (Name). | HDD Details — HDD Slot: Slot no, Capacity: Hdd Memory, Used: Memory Use(%), Free: Memory Free (%). Status: Healthy, | `CCTV_HDD_INFO` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvHddInfo` | FORMAT_DRIFT | handler |
| Q18. Give CCTV HDD information of Branch (Name). | CCTV HDD Information: Drive Type: Status: HEALTHY. HDD Slot: Slot no, Capacity: Hdd Memory, Used: Memory Use(%), Free: Memory Free (%). | `CCTV_HDD_INFO` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvHddInfo` | FORMAT_DRIFT | handler |
| Q19. What is the Branch (Name) CCTV recording information right now? | CCTV Recording Info: Channel No- Camera No, Device Name- Camera Name, Recording Days- Rec. Days. | `CCTV_RECORDING_INFO` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvRecordingInfo` | FORMAT_DRIFT | handler |
| Q20. Is CCTV recording working properly on Branch (Name)? | Yes, CCTV recording is working properly. Channel No- Camera No, Device Name- Camera Name, Recording Days- Rec. Days.. | `CCTV_RECORDING_INFO` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvRecordingInfo` | FORMAT_DRIFT | handler |
| Q21. Show Branch (Name) recording details of CCTV. | Recording Details: Channel No- Camera No, Device Name- Camera Name, Recording Days- Rec. Days... | `CCTV_RECORDING_INFO` | deterministic handler | `CctvHandler` + `CctvHandler.answerCctvRecordingInfo` | FORMAT_DRIFT | handler |
| Q22. What is the Branch (Name) CCTV camera information right now? | Camera Info: Total: No of cameras, Channel No:Total no of Channels, Make: Brand Name, Model: Model No,Type: IP No, Resolution: Camera Resolutions. Online: Camera Online No. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MISSING_OR_WRONG_INTENT | intent |
| Q23. Show camera details of CCTV of Branch (Name). | Camera Details: Total: No of cameras, Channel No:Total no of Channels, Make: Brand Name, Model: Model No,Type: IP No, Resolution: Camera Resolutions. Online: Camera Online No. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MISSING_OR_WRONG_INTENT | intent |
| Q24. Give Branch (Name) information about CCTV cameras. | CCTV Camera Info: Total: No of cameras, Channel No:Total no of Channels, Make: Brand Name, Model: Model No,Type: IP No, Resolution: Camera Resolutions. Online: Camera Online No. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MISSING_OR_WRONG_INTENT | intent |
| Q25. What is the Branch (Name) CCTV device information right now? | CCTV Device Info: DVR/NVR Model: Device Model No, Channels: Total No of Channel, IP: Show Device IP, Firmware: Show Device Vertion, Status: Online. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MISSING_OR_WRONG_INTENT | intent |
| Q26. Show CCTV device details of Branch (Name). | CCTV Device Details: DVR/NVR Model: Device Model No, Channels: Total No of Channel, IP: Show Device IP, Firmware: Show Device Vertion, Status: Online. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MISSING_OR_WRONG_INTENT | intent |
| Q27. Branch (Name) Give full info of CCTV system. | Full CCTV System Info: DVR/NVR Model: Device Model No, Channels: Total No of Channel, IP: Show Device IP, Firmware: Show Device Vertion, Status: Online.Recording: Active. | `CCTV_STATUS` | deterministic handler | `CctvHandler` + `AnswerTemplateService.renderCctvStatus` | MISSING_OR_WRONG_INTENT | intent |
| **IAS (Intrusion Alarm System)** | | | | | | |
| Q1. What is the Branch (Name) IAS power status right now? | IAS Power Status: ON. Panel is receiving DC Power. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q2. Branch (Name) is the IAS system powered on? | Yes, the IAS is powered ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q3. Check IAS power status of Branch (Name). | IAS Power Check: Panel Power: ON — All power sources normal. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q4. What is the Branch (Name) IAS alarm status right now? | IAS Alarm Status: NO ACTIVE ALARMS. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q5. Branch (Name) is there any IAS alarm? | No IAS alarms are currently active. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q6. Check IAS alarm condition of Branch (Name). | IAS Alarm Check: Active Alarms: None. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q7. What is the Branch (Name) IAS fault status right now? | IAS Fault Status: NO FAULTS DETECTED. All sensors, zones, and communication lines are functioning correctly. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q8. Branch (Name) is there any fault in IAS? | No faults detected in the IAS system. Sensors, and communication lines are working properly. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q9. Show IAS fault status of Branch (Name). | IAS Fault Report: Active Faults: None. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| **BAS (Building Automation System)** | | | | | | |
| Q1. What is the Branch (Name) BAS power status right now? | BAS Power Status: ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q2. Branch (Name) is the BAS system powered on? | Yes, the BAS (Burglar Alarm System) main panel is powered ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q3. Check BAS power status of Branch (Name). | BAS Power Check: Panel Power: ON | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q4. What is the Branch (Name) BAS alarm status right now? | BAS Alarm Status: NO ACTIVE ALARMS. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q5. Branch (Name) is there any BAS alarm? | No active BAS alarm. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q6. Check BAS alarm condition of Branch (Name). | BAS Alarm Check: Alarms: None. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q7. What is the Branch (Name) BAS fault status right now? | BAS Fault Status: 1 FAULT ACTIVE — INTRUSION ALARM SYSTEM FAULT. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q8. Branch (Name) is there any fault in BAS? | Yes, 1 fault in BAS: INTRUSION ALARM SYSTEM FAULT. since approximately 2 hours ago. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q9. Show BAS fault status of Branch (Name) | BAS Fault Report: INTRUSION ALARM SYSTEM FAULT. since approximately 2 hours ago. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| **FAS (Fire Alarm System)** | | | | | | |
| Q1. What is the Branch (Name) FAS power status right now? | FAS Power Status: ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q2. Branch (Name) is the fire alarm system powered on? | Yes, the FAS (Fire Alarm System) is powered ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q3. Check FAS power status of Branch (Name). | FAS Power Check: Panel: ON — All power sources healthy. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q4. What is the Branch (Name) FAS alarm status right now? | FAS Alarm Status: NO ACTIVE ALARMS. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q5. Branch (Name) is there any fire alarm active? | No alarms are currently active. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q6. Check FAS  alarm status of Branch (Name). | Fire Alarm Check: Active Alarms: None. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q7. What is the Branch (Name) FAS fault status right now? | FAS Fault Status: NO FAULTS DETECTED. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q8. Branch (Name) Is there any fault in fire alarm system? | No faults detected in the FAS. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q9. Show FAS fault status of Branch (Name). | FAS Fault Report: Active Faults: None. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| **Time Lock System** | | | | | | |
| Q1. What is the Branch (Name) Time Lock power status right now? | Time Lock Power Status: OFFLINE. The Time Lock controller has been offline for approximately 3 hours. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q2. Branch (Name) is the Time Lock powered on? | The Time Lock status is currently OFFLINE and cannot confirm power status. Last known state: Powered ON (3 hours ago). | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q3. Check Time Lock power status of Branch (Name). | Time Lock Power Check: Current Status: OFFLINE (3 hours). | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q4. What is the Branch (Name) Time Lock alarm status right now? | Time Lock Alarm Status: TIME LOCK SYSTEM OFF. Device is currently offline. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q5. Branch (Name) is there any alarm in Time Lock? | Time Lock system is OFFLINE. Please check device physically. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q6. Check Time Lock alarm status | Time Lock Alarm Check: Status: OFFLINE — Last known alarm status: No Active Alarms. Reconnection required to get current status | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q7. What is the Branch (Name) Time Lock fault status right now? | Time Lock Fault Status: OFFLINE — Device disconnected 3 hours ago. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q8. Branch (Name) is there any fault in Time Lock? | Fault detected: Time Lock Controller is OFFLINE. Physical inspection of the device is recommended. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q9. Show Time Lock fault status of Branch (Name). | Time Lock Fault Report: Device Status: OFFLINE, Action: On-site inspection required. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q10. What is the Branch (Name) Time Lock door status right now? | Time Lock Door Status: N/A — is offline. Last known door status: N/A. Physical verification recommended. | `DOOR_STATUS` | deterministic handler | `DoorStatusHandler` + `DoorStatusHandler.handle` | FORMAT_DRIFT | handler |
| Q11. Branch (Name) is the Time Lock door open or closed? | Cannot determine door status — Time Lock is OFFLINE. Last recorded state: Close. Please verify physically at the branch. | `DOOR_STATUS` | deterministic handler | `DoorStatusHandler` + `DoorStatusHandler.handle` | FORMAT_DRIFT | handler |
| Q12. Check Time Lock door status of Branch (Name). | Door Status Check: Result: N/A (Offline). Last known state: Closed of 3 hours ago. Immediate site visit recommended. | `DOOR_STATUS` | deterministic handler | `DoorStatusHandler` + `DoorStatusHandler.handle` | FORMAT_DRIFT | handler |
| **Access Control System** | | | | | | |
| Q1. What is the Branch (Name) Access Control power status right now? | Access Control Power Status: ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q2. Branch (Name) is the Access Control system powered on? | Yes, Access Control is powered ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q3. Check Access Control power status of Branch (Name). | Access Control Power: ON. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | FORMAT_DRIFT | handler |
| Q4. What is the Branch (Name) Access Control alarm status right now? | Access Control Alarm Status: NO ACTIVE ALARMS. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q5. Branch (Name) is there any alarm in Access Control system? | No Access Control alarms currently active. | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q6. Check Access Control alarm status of Branch (Name). | Access Control Alarm Check: NO ACTIVE ALARMS | `SUBSYSTEM_ALARM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemAlarmStatus` | FORMAT_DRIFT | handler |
| Q7. What is the Branch (Name) Access Control fault status right now? | Access Control Fault Status: NO FAULTS. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q8. Branch (Name) is there any fault in Access Control system? | No faults detected in Access Control system. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q9. Show Access Control fault status of Branch (Name). | Access Control Fault Report: NO FAULTS DETECTED. | `SUBSYSTEM_FAULT_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemFaultStatus` | FORMAT_DRIFT | handler |
| Q10. What is the Branch (Name) Access Control door status right now? | Door Status — Door Open. | `DOOR_STATUS` | deterministic handler | `DoorStatusHandler` + `DoorStatusHandler.handle` | FORMAT_DRIFT | handler |
| Q11. Branch (Name) Are the doors locked or unlocked? | Door Status — Door Open. | `DOOR_STATUS` | deterministic handler | `DoorStatusHandler` + `DoorStatusHandler.handle` | FORMAT_DRIFT | handler |
| Q12. Check the Access Control door status of Branch (Name). | Door Status — Door Open. | `DOOR_STATUS` | deterministic handler | `DoorStatusHandler` + `DoorStatusHandler.handle` | FORMAT_DRIFT | handler |
| Q13. What is the Branch (Name) Access Control total user count right now? | Total Registered Users: 142. | `ACCESS_CONTROL_USER_COUNT` | deterministic handler | `AccessControlHandler` + `AccessControlHandler.answerAccessControlUserCount` | FORMAT_DRIFT | handler |
| Q14. How many users are in the Access Control system of Branch (Name). | Access Control has 142 total registered users. | `ACCESS_CONTROL_USER_COUNT` | deterministic handler | `AccessControlHandler` + `AccessControlHandler.answerAccessControlUserCount` | FORMAT_DRIFT | handler |
| Q15. Show total users in Access Control of Branch (Name). | User Count: Total: 142. | `ACCESS_CONTROL_USER_COUNT` | deterministic handler | `AccessControlHandler` + `AccessControlHandler.answerAccessControlUserCount` | FORMAT_DRIFT | handler |
| Q16. What is the Branch (Name) Access Control device information right now? | Access Control Device Info: Device: Name, Model: Name, IP: Device IP, Firmware:Device Version, Doors: Open, Status: Online. | `ACCESS_CONTROL_DEVICE_INFO` | deterministic handler | `AccessControlHandler` + `AccessControlHandler.answerAccessControlDeviceInfo` | FORMAT_DRIFT | handler |
| Q17. Show Branch (Name) Access Control device details? | Device Details: Device: Name, Model: Name, IP: Device IP, Firmware:Device Version, Doors: Open, Status: Online. | `ACCESS_CONTROL_DEVICE_INFO` | deterministic handler | `AccessControlHandler` + `AccessControlHandler.answerAccessControlDeviceInfo` | FORMAT_DRIFT | handler |
| Q18. Give full information of Access Control system of Branch (Name). | Full Access Control Info: Device: Name, Model: Name, IP: Device IP, Firmware:Device Version, Doors: Open, Status: Online. | `SUBSYSTEM_STATUS` | deterministic handler | `SubsystemHandler` + `SubsystemHandler.answerSubsystemStatus` | MISSING_OR_WRONG_INTENT | intent |

## Gaps Summary

Below is the summary of all gaps identified in the coverage matrix, grouped by the recommended fix type.

### 1. Fix Type: `intent` (Missing or Wrong Intent Routing)
For these questions, the regex/keyword detection in `QueryIntentResolver.java#detectIntent` is either too broad, missing rules, or incorrectly classifying the intent, which causes them to fall back to `GENERAL_LLM` or resolve to a less detailed intent (like `GATEWAY_STATUS` or `CCTV_STATUS`):

*   **Gateway Online/Offline Status (Network context)** (Mapped to `GATEWAY_STATUS` instead of `NETWORK_STATUS` which is required to fetch the network operator detail):
    *   *Q32*: "Is the Branch (Name) Gateway online or offline?"
*   **CCTV Power Status** (Mapped to generic `CCTV_STATUS` camera count instead of CCTV system power state):
    *   *Q1*: "What is the Branch (Name) CCTV power status right now?"
    *   *Q2*: "Is the Branch (Name) CCTV system powered on?"
    *   *Q3*: "Check CCTV power status Branch (Name)."
*   **CCTV HDD Proper Function** (Mapped to `CCTV_STATUS` camera count because it lacks keywords like "ERROR" or "FAULT" to route to `CCTV_HDD_ERROR_STATUS`):
    *   *Q8*: "Is the Branch (Name) CCTV HDD working properly?"
*   **CCTV Camera Detailed Information** (Mapped to `CCTV_STATUS` camera count instead of returning a detailed inventory layout):
    *   *Q22*: "What is the Branch (Name) CCTV camera information right now?"
    *   *Q23*: "Show camera details of CCTV of Branch (Name)."
    *   *Q24*: "Give Branch (Name) information about CCTV cameras."
*   **CCTV Device Detailed Information** (Mapped to `CCTV_STATUS` camera count instead of detailed specs):
    *   *Q25*: "What is the Branch (Name) CCTV device information right now?"
    *   *Q26*: "Show CCTV device details of Branch (Name)."
    *   *Q27*: "Branch (Name) Give full info of CCTV system."
*   **Access Control Full Information** (Mapped to generic `SUBSYSTEM_STATUS` status check instead of detailed biometric/ACS device specs):
    *   *Q18*: "Give full information of Access Control system of Branch (Name)."

### 2. Fix Type: `handler` (Format Drift in Deterministic Handler/Template)
For these questions, the query resolves to the correct intent, but the hardcoded wording, headers, or details returned by the Java handler/template differ from the user's expected answer. To resolve this, updates are needed in the corresponding handler classes in `service/query/handler/` or the template strings in `AnswerTemplateService.java`:

*   **Gateway Status Phrasing**:
    *   *Q1, Q2, Q3* (Uses `GatewayStatusHandler` + `AnswerTemplateService.renderGatewayStatus`)
*   **Battery/AC Voltage Phrasing**:
    *   *Q4, Q5, Q6* (Uses `PowerHandler` + `AnswerTemplateService.renderMetric` for Battery Voltage)
    *   *Q7, Q8, Q9* (Uses `PowerHandler` + `AnswerTemplateService.renderMetric` for AC Voltage)
*   **Low Battery Status Wording**:
    *   *Q10, Q11, Q12* (Uses `PowerHandler` + `PowerHandler.answerBatteryLowStatus`)
*   **Active / Faulty / Offline / Connected Devices Phrasing**:
    *   *Q16, Q17, Q18* (Active devices)
    *   *Q19, Q20, Q21* (Faulty devices)
    *   *Q22, Q23, Q24* (Offline devices)
    *   *Q25, Q26, Q27* (Connected devices)
*   **Network status operator wording**:
    *   *Q31, Q33* (Uses `NetworkStatusHandler`)
*   **System Current Amp format**:
    *   *Q34, Q35, Q36* (Uses `PowerHandler` for System Current)
*   **Global Overview and Multi-Branch Layouts**:
    *   *Q37, Q38, Q39* (Uses `GlobalOverviewHandler`)
*   **Alarm & Error Counts formatting**:
    *   *Q40, Q41, Q42* (Uses `AlertHandler` for Alarms)
    *   *Q43, Q44, Q45* (Uses `AlertHandler` for Errors)
*   **CCTV Camera / HDD / Recording / Disconnect / Alarm Phrasing**:
    *   *Q4, Q5, Q6* (CCTV generic status counts)
    *   *Q7, Q9* (CCTV HDD error status check)
    *   *Q10, Q11, Q12* (CCTV alarm status check)
    *   *Q13, Q14, Q15* (CCTV camera disconnect lists)
    *   *Q16, Q17, Q18* (CCTV HDD info details)
    *   *Q19, Q20, Q21* (CCTV Recording specs)
*   **Subsystem (IAS, BAS, FAS, Time Lock, Access Control) Status/Alarm/Fault/Door Phrasing**:
    *   All corresponding subsystem Q1–Q9 queries (using `SubsystemHandler` and `DoorStatusHandler` templates)
*   **Access Control User Counts & Device Specs**:
    *   *Q13, Q14, Q15* (ACS User Counts)
    *   *Q16, Q17* (ACS Device Specs)
