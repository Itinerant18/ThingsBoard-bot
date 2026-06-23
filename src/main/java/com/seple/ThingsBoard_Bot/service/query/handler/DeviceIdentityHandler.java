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
        return intent == QueryIntent.DEVICE_IMEI || intent == QueryIntent.GPS_LOCATION;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
        return switch (query.getIntent()) {
            case DEVICE_IMEI -> answerImei(branch);
            case GPS_LOCATION -> answerGpsLocation(branch);
            default -> null;
        };
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
}
