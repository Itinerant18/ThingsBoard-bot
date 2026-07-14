# SWatch360
## Sidebar Navigation & User Guide
*Post-login application walkthrough, module by module*

* **Path**: `This guide maps every screen reachable from the sidebar after logging in. Each module shows its breadcrumb path (Sidebar -> Module -> Action) followed by numbered, click-by-click steps. A Note callout flags places where a control looks interactive but only expands or highlights in place rather than loading new data.`
## Sidebar Modules Covered

### 1. Home

### 2. Health Monitor

### 3. Branch List

### 4. Device List

### 5. Alerts (Alarms & Error)

### 6. Map View

### 7. S-Vault

### 8. S-Insights (5 reports)

### 9. Security (Audit Logs)
# 1. Home

* **Path**: `Sidebar -> Home`
- Click Home in the sidebar to open the static landing dashboard.
- Device-category rows (Gateway, CCTV, etc.) only highlight when clicked — they do not drill into another screen.
- Click the cluster marker (Home -> Map cluster marker) to zoom into the cluster of branch pins.
- Use the Alarms table's own filter row at the bottom of the page (FGMO, ZO, Branch, Type, Severity, date range) to filter alarms in place — no navigation occurs.
- Note: The dashboard cards are read-only summaries; the map cluster and the Alarms filter row are the only two live interactions on this screen.
# 2. Health Monitor

* **Path**: `Sidebar -> Health Monitor`
- Click Health Monitor in the sidebar.
- Select a month (Health Monitor -> "SELECT MONTH" tab, e.g. Jun 2026) — this reloads every card on the page for that month. Different months (e.g. Jun 2026 vs Apr 2026) return completely different totals.
- Health Monitor -> Hierarchy Drilldown Explorer -> click a FGMO row (e.g. "EAST") to populate the ZO column with that region's zones.
- … -> click a ZO row (e.g. "BARASAT") to populate the Branch column with that zone's branch health scores.
- Click "View all ->" next to the FGMO/ZO/Branch rankings to expand the same panel in place; it toggles to "View less" to collapse it again.
# 3. Branch List

* **Path**: `Sidebar -> Branch List`
- Click Branch List in the sidebar.
- Select a device-type tab (Branch List -> FAS tab, for example) to re-render the KPI cards and health-mix donut for that device category only. FGMO/ZO issue summaries stay global regardless of which tab is selected.
- Branch List -> FGMO dropdown -> ZO dropdown -> Apply to filter the branch table.
- Sort the branch table by clicking the small arrow icons on the FGMO/ZO column headers.
- Click on Branch -> Home Tab -> shows uptime percentage of all devices (Gateway, CCTV, IAS, BAS, FAS, TLS, ACS, UPS)
- … Gateway Tab ->
- Top status cards: Power Status, System Status, Health Status — quick at-a-glance health check.
- Second row of icons: Power Supply, Battery Status, Network, Operator, SOS Status (Active/Inactive), Devices Connected (count) — key operational indicators for the gateway hardware.
- Gateway Heartbeat: A circular gauge showing device heartbeat/uptime (currently N/A = no signal).
- Alarm / Error counters: Quick counts of active alarms and errors.
- Live meters: AC Voltage, Current, and Battery gauges with nominal reference values (230V / 1.0A / 12.0V).
- Device Location: Map pinpointing the branch's physical location.
- Task Manager button: Opens task management tools for this device.
- Gateway Alarms List: A searchable, filterable, sortable table of alarms (Originator, Type, Severity, Created/End time, Status, TAT) with pagination controls at the bottom.
- Notification Recipients / Notification Setting: Configure who gets alerted and how.
- Parameter Settings: Adjust device thresholds/parameters.
- System Logs: Access historical system log records.
- Contact information panel: Editable Address/Phone/Email for the branch contact (pencil icon to edit).

#### …CCTV Tab
- Top status cards: Power Status (Off), Camera Status, Camera Link Status, HDD Status — quick health snapshot of the CCTV system.
- Summary cards: Branch Name, Heartbeat (On/Off), System Status (Healthy), Total Cameras (e.g., 16/16 online), and Storage Info (total HDD capacity and number of active disks).
- About Device: Device details like system time/date, last sync time, device type/make/model, serial number, firmware version, and manufacture date.
- HDD Information table: Lists each HDD slot with capacity, free space, and status (OK/Not Exist).
- Camera Information table: Full inventory of all channels — device name, make, model, serial number, resolution, FPS, IP address, camera status (active/inactive), and SD card capacity.
- Recording Information: Toggle between NVR Recording and SD Recording views, showing each channel's start/end recording time and total recording days (with a daily/time-range selector).
- Alarms List: Counters for Camera Tamper, Disconnections, and HDD Errors, each clickable for more details.
- Daily Channel Statistics: Per-channel analysis chart/data, filterable by date.

#### … IAS Tab
- Top status cards: Power Status, System Status, Health Status — all currently showing N/A (no live data/device connected for this branch).
- Time Range selector: Dropdown (default "Daily") to filter the alarms data shown below by time period.
- Alarms List section: Three alarm-category cards (green dot = active tracking) — likely Tamper/Intrusion alerts, System Off logs, and System Fault alarms — each showing a live count and a "Click to view details" link.
- Detail popup: Clicking any alarm card opens an "Integrated Alarms List" table (Originator, Type, Severity, Created/End Time, Status, TAT) with search, filter, column, export, and pagination controls — same style as the Gateway/CCTV alarm tables.

#### …BAS Tab
- Top status cards: Power Status (On), System Status (Normal), Health Status (Active) — overall system health snapshot.
- Summary cards: Branch Name, Heart Beat (OK), Panel State, Panel Mode — key panel identity/status indicators.
- Uptime Dashboard: Shows BAS Heartbeat Health as a percentage — tracks device connectivity uptime.
- Zone Information table: Lists each zone with Zone Type, Area, State, Bell Status, Zone Events, and Timestamp.
- About Device: Panel IP, Model, Firmware Version, Last Updated — device identification details.
- Power Status panel: System Voltage, Battery Voltage, System Current, Battery Current, Battery Status, and Main (AC) Status — electrical health of the panel.
- Time Range selector: Filters the alarms data (default "Daily").
- Alarms List: System Off and System Fault counters, each clickable to view a detailed alarm log table.

#### …FAS Tab
- Top status cards: Power Status (On), System Status (Normal), Health Status (Active) — overall fire system health snapshot.
- Time Range selector: Dropdown (default "Daily") to filter the alarm/heartbeat data shown below.
- Alarms List section: Three clickable cards — "Alarms List" (general alarm counter), "System Off" (off logs), and "System Fault" (fault alarms) — each opens a detailed alarm log table (Originator, Type, Severity, Created/End Time, Status, TAT) when clicked.

#### …TLS Tab
- Top status cards: Power Status, Door Status, Health Status.
- Time Range selector: Dropdown (default "Daily") to filter the alarm data below.
- Alarms List section: Four clickable cards — "Alarms List" (general), "System Off" (off logs), "System Fault" (fault alarms), and "Door Open" (door-open logs) — each shows a live count and opens a detailed alarm log table when clicked.

#### …ACS Tab
- Top status cards: Power Status, Door Status, Health Status — basic access-control health snapshot.
- Summary cards: HeartBeat, System Status, Total Users — device connectivity and enrolled-user count.
- About Device: Detailed device info including System Time/Date, Last Sync Date & Time, Device Name/Type/SubType, Manufacturer, Model Number, Serial Number, Device ID, MAC Address, Firmware Version, Total Cards, Door Status, and Magnet Sensor.
- Time Range selector: Dropdown (default "Daily") for filtering alarm data.
- Alarms List section: Four clickable cards — "Alarms List" (general), "System Off", "System Fault", and "Door Open" — each shows a live count and opens a detailed alarm log table when clicked.

#### …UPS Tab
- Top status cards: Power Connection (Offline), UPS Mode, Mains Status — overall power backup health snapshot.
- Second row of metric cards: Input Voltage, Output Voltage, Battery Percentage, Temperature, Battery Status, Input Frequency — key electrical readings from the UPS unit.
- Live Meters section: Four real-time gauges — Input Voltage (range 150V–300V), Output Voltage (150V–300V), Output Load (0–100%), and Battery Charge (0–100%) — with a "Last updated" timestamp showing data freshness.
# 4. Device List

* **Path**: `Sidebar -> Device List`
- Click Device List in the sidebar.
- Device List -> Period / FGMO / ZO / Branch / Network / GPS Status dropdowns -> Apply to filter the page.
- Click "View all ↓" on the "Branches with Missing IMEI/Network" panel to expand that list in place (toggles to "Show less ↑"). This does not open a new page.
- Sort the Device Inventory Matrix table at the bottom via its FGMO/ZO/Branch column headers.
# 5. Alerts (Alarms & Error)

* **Path**: `Sidebar -> Alerts -> Alarms   |   Sidebar -> Alerts -> Error`
- Note: Both screens share an identical structure and the same steps below apply to either.
- Click Alerts in the sidebar, then choose either Alarms or Error.
- Filter with Time Range, FGMO, ZO, Branch, Severity, Type, Status -> Apply, or -> Clear to reset.
- Review the KPI cards, the Alarm/Error trend chart, the severity-mix donut, and the top FGMO/ZO/Branch lists — these update from the same filter.
- Scroll to the "Recent Events" table at the bottom. This is a flat log — clicking a row does not open further detail.
# 6. Map View

* **Path**: `Sidebar -> Map View`
- Click Map View in the sidebar.
- Filter with FGMO / ZO / Branch -> Apply.
- Click a branch row in "Branches in Current View" (or click a marker directly) — this zooms the map to that branch's coordinates and populates the "Selected Branch Summary" panel on the right with per-device online/offline status.
- Map View -> Layer Controls -> toggle CCTV / IAS / BAS / FAS / TLS / ACS to overlay those device layers on the map.
- Click "Reset Layers" to revert to the default view.
# 7. S-Vault

* **Path**: `Sidebar -> S-Vault`
- Click S-Vault in the sidebar to open the footage request form.
- Choose an entry method tab:
- "Enter SOL ID" tab — a single SOL-ID search box.
- "Enter Manual" tab — cascading dropdowns HO -> FGMO -> ZO -> Branch -> Device Type -> Channel, plus a calendar for the playback date and a time-range selector.
- Click "Submit Request" to send the footage request once all fields are set.
- S-Vault -> History button switches to a "Download Request History" table listing past footage requests.
- Click "Back" to return to the request form.
# 8. S-Insights (5 Reports)

* **Path**: `Sidebar -> S-Insights -> [Report Name]`
## 8.1  TAT Report

* **Path**: `Sidebar -> S-Insights -> TAT Report.`
- Filter with the device-type chip row (Gateway/CCTV/IAS/BAS/FAS/TLS/ACS) and the "All Status" dropdown (All / Offline / Fault).
- Click the Analytics toggle to reveal three extra charts above the incident table: Open Issues by System, TAT Age Distribution, and Top 5 Max Downtime. Click Analytics again to hide them.
## 8.2  Branch Report

* **Path**: `Sidebar -> S-Insights -> Branch Report.`
- Use the FGMO/ZO dropdowns and/or the text search box.
- Type an exact Branch Name (the search box filters strictly by that column) and click Apply.
## 8.3  Uptime Report

* **Path**: `Sidebar -> S-Insights -> Uptime Report.`
- Click Date to open a calendar restricted to the past 30 days.
- Pick a day (e.g. Uptime Report -> Date -> Jul 4) to reload the whole per-branch/per-device-type table for that day.
- Click Reset to return the table to today's date.
## 8.4  Recording Report

* **Path**: `Sidebar -> S-Insights -> Recording Report.`
- Use the FGMO -> ZO -> Branch cascading dropdowns to load a camera-level table showing IP, days of footage available, risk level, and last checked time.
## 8.5  CCTV Inventory Report

* **Path**: `Sidebar -> S-Insights -> CCTV Inventory Report.`
- Use the FGMO/ZO/Branch/Make/Resolution/Recording-Status dropdowns to filter the hardware inventory table.
- Use the "Search Branch" box for a direct branch lookup.
# 9. Security (Audit Logs)

* **Path**: `Sidebar -> Security -> Audit Logs`
- Click Security then Audit Logs.
- Click the "last 1 day" button to open a time-window popup with Last / Range / Relative modes and a duration selector.
- Click "Audit log filter" to open the "Audit log types" dropdown filter (Reset / Cancel / Update).
