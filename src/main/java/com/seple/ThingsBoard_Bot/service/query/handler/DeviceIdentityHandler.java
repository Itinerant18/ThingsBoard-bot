package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** DEVICE_IMEI and GPS_LOCATION — reports gateway device identity (IMEI, GPS) from raw telemetry. */
@Component
public class DeviceIdentityHandler implements AnswerHandler {

    /** India geographic centroid that devices report when they have no real GPS fix. */
    private static final double DEFAULT_LAT = 20.5937;
    private static final double DEFAULT_LON = 78.9629;

    private final AnswerSupport support;

    public DeviceIdentityHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.DEVICE_IMEI || intent == QueryIntent.GPS_LOCATION
                || intent == QueryIntent.LAST_REPORTED;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            // Fleet-level query: scan all snapshots
            return switch (query.getIntent()) {
                case DEVICE_IMEI -> answerFleetImei(snapshots);
                case GPS_LOCATION -> answerFleetGps(snapshots);
                default -> null;
            };
        }
        return switch (query.getIntent()) {
            case DEVICE_IMEI -> answerImei(branch);
            case GPS_LOCATION -> answerGpsLocation(branch);
            case LAST_REPORTED -> answerLastReported(branch);
            default -> null;
        };
    }

    private String answerLastReported(BranchSnapshot branch) {
        Map<String, Object> raw = branch.getRawData();
        // data_as_of is the ISO stamp derived from the device's lastUpdatedAt (UserDataService);
        // audit_ts is the device-reported fallback.
        String asOf = support.firstNonBlank(raw, "data_as_of", "audit_ts");
        if (asOf == null) {
            return "**For Branch " + support.branchName(branch) + ", the last-reported time is not available.**";
        }
        boolean stale = support.isTrue(raw.get("data_stale"));
        return "**For Branch " + support.branchName(branch) + ", the device last reported at " + asOf
                + (stale ? " (data is stale)" : "") + ".**";
    }

    private String answerImei(BranchSnapshot branch) {
        String imei = support.firstNonBlank(branch.getRawData(), "imei_id_dev_id", "imei_id");
        if (imei == null || isMissingImei(imei)) {
            // "0", "None", null/blank, "N/A" and the absent key all mean the device never reported an
            // IMEI -- the dashboard's "missing IMEI" case. Report it honestly, never echo the sentinel.
            return "**For Branch " + support.branchName(branch) + ", the device IMEI is not reported (missing).**";
        }
        return "**For Branch " + support.branchName(branch) + ", the device IMEI is " + imei + ".**";
    }

    /** True when the resolved IMEI is a "not reported" sentinel ("0", "None", "null", "N/A", "NA"). */
    private static boolean isMissingImei(String value) {
        String v = value.trim();
        return v.isEmpty() || v.equals("0") || v.equalsIgnoreCase("none")
                || v.equalsIgnoreCase("null") || v.equalsIgnoreCase("n/a") || v.equalsIgnoreCase("na");
    }

    private String answerGpsLocation(BranchSnapshot branch) {
        Map<String, Object> raw = branch.getRawData();
        Double lat = parseDouble(support.firstNonBlank(raw, "lat", "latitude"));
        Double lon = parseDouble(support.firstNonBlank(raw, "lon", "longitude"));

        // No fix, or the India-centroid placeholder, or lat_lon_default=true -> the device has no real
        // GPS fix (the dashboard's "missing GPS" case). Report honestly rather than the placeholder.
        boolean usingDefault = support.isTrue(raw.get("lat_lon_default"))
                || (lat != null && lon != null && isCentroid(lat, lon));
        if (lat == null || lon == null) {
            return "**For Branch " + support.branchName(branch) + ", the GPS location is not reported (missing).**";
        }
        if (usingDefault) {
            return "**For Branch " + support.branchName(branch)
                    + ", the GPS location is not reported (device is using the default location).**";
        }
        return "**For Branch " + support.branchName(branch)
                + ", the GPS location is " + lat + ", " + lon + " (latitude, longitude).**";
    }

    private static boolean isCentroid(double lat, double lon) {
        return Math.abs(lat - DEFAULT_LAT) < 0.001 && Math.abs(lon - DEFAULT_LON) < 0.001;
    }

    private static Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // ── Fleet-level methods ─────────────────────────────────────────────────

    private String answerFleetImei(List<BranchSnapshot> snapshots) {
        List<String> missing = new java.util.ArrayList<>();
        List<String> present = new java.util.ArrayList<>();
        for (BranchSnapshot snap : snapshots) {
            String imei = support.firstNonBlank(snap.getRawData(), "imei_id_dev_id", "imei_id");
            if (imei == null || isMissingImei(imei)) {
                missing.add(support.branchName(snap));
            } else {
                present.add(support.branchName(snap));
            }
        }
        int total = snapshots.size();
        StringBuilder sb = new StringBuilder();
        sb.append("### 📱 IMEI Data Quality Across Fleet\n\n");
        sb.append("| Metric | Count |\n|--------|-------|\n");
        sb.append("| Total Devices | ").append(total).append(" |\n");
        sb.append("| ✅ IMEI Present | ").append(present.size()).append(" |\n");
        sb.append("| ❌ IMEI Missing | ").append(missing.size()).append(" |\n");
        if (total > 0) {
            sb.append("| Data Quality | ").append(Math.round(present.size() * 100.0 / total)).append("% |\n");
        }
        if (!missing.isEmpty()) {
            sb.append("\n**Branches with Missing IMEI (").append(missing.size()).append("):**\n");
            for (String name : missing) {
                sb.append("- ").append(name).append("\n");
            }
        }
        return sb.toString();
    }

    private String answerFleetGps(List<BranchSnapshot> snapshots) {
        List<String> missing = new java.util.ArrayList<>();
        List<String> defaultGps = new java.util.ArrayList<>();
        List<String> valid = new java.util.ArrayList<>();
        for (BranchSnapshot snap : snapshots) {
            Map<String, Object> raw = snap.getRawData();
            Double lat = parseDouble(support.firstNonBlank(raw, "lat", "latitude"));
            Double lon = parseDouble(support.firstNonBlank(raw, "lon", "longitude"));
            boolean usingDefault = support.isTrue(raw.get("lat_lon_default"))
                    || (lat != null && lon != null && isCentroid(lat, lon));
            if (lat == null || lon == null) {
                missing.add(support.branchName(snap));
            } else if (usingDefault) {
                defaultGps.add(support.branchName(snap));
            } else {
                valid.add(support.branchName(snap));
            }
        }
        int total = snapshots.size();
        StringBuilder sb = new StringBuilder();
        sb.append("### 📍 GPS Data Quality Across Fleet\n\n");
        sb.append("| Metric | Count |\n|--------|-------|\n");
        sb.append("| Total Devices | ").append(total).append(" |\n");
        sb.append("| ✅ Valid GPS | ").append(valid.size()).append(" |\n");
        sb.append("| ⚠️ Default Location | ").append(defaultGps.size()).append(" |\n");
        sb.append("| ❌ GPS Missing | ").append(missing.size()).append(" |\n");
        if (total > 0) {
            sb.append("| Data Quality | ").append(Math.round(valid.size() * 100.0 / total)).append("% |\n");
        }
        if (!missing.isEmpty()) {
            sb.append("\n**Branches with Missing GPS (").append(missing.size()).append("):**\n");
            for (String name : missing) {
                sb.append("- ").append(name).append("\n");
            }
        }
        if (!defaultGps.isEmpty()) {
            sb.append("\n**Branches Using Default Location (").append(defaultGps.size()).append("):**\n");
            for (String name : defaultGps) {
                sb.append("- ").append(name).append("\n");
            }
        }
        return sb.toString();
    }
}
