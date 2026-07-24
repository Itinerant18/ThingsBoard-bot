package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * DATA_QUALITY — reports the percentage of devices that have key telemetry fields (IMEI, GPS,
 * network operator) present. Answers questions like "what % of devices have IMEI/GPS/network?"
 * and "show data quality / data completeness".
 */
@Component
public class DataQualityHandler implements AnswerHandler {

    private static final double DEFAULT_LAT = 20.5937;
    private static final double DEFAULT_LON = 78.9629;

    private final AnswerSupport support;

    public DataQualityHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.DATA_QUALITY;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        int total = snapshots.size();
        if (total == 0) {
            return "No branch devices found in your scope to assess data quality.";
        }

        int hasImei = 0, hasGps = 0, hasNetwork = 0;
        List<String> missingImei = new ArrayList<>();
        List<String> missingGps = new ArrayList<>();
        List<String> missingNetwork = new ArrayList<>();

        for (BranchSnapshot snap : snapshots) {
            Map<String, Object> raw = snap.getRawData();
            String name = support.branchName(snap);

            // IMEI check
            String imei = support.firstNonBlank(raw, "imei_id_dev_id", "imei_id");
            if (imei != null && !isMissingImei(imei)) {
                hasImei++;
            } else {
                missingImei.add(name);
            }

            // GPS check
            Double lat = parseDouble(support.firstNonBlank(raw, "lat", "latitude"));
            Double lon = parseDouble(support.firstNonBlank(raw, "lon", "longitude"));
            boolean validGps = lat != null && lon != null
                    && !support.isTrue(raw.get("lat_lon_default"))
                    && !isCentroid(lat, lon);
            if (validGps) {
                hasGps++;
            } else {
                missingGps.add(name);
            }

            // Network check
            String operator = support.firstNonBlank(raw,
                    "system_status_statusbox_network", "statusbox_network", "networkOperator");
            if (operator != null && !operator.isBlank()) {
                hasNetwork++;
            } else {
                missingNetwork.add(name);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 📊 Data Quality Report Across Fleet\n\n");
        sb.append("| Field | Present | Missing | Completeness |\n");
        sb.append("|-------|---------|---------|--------------|\n");
        sb.append("| IMEI | ").append(hasImei).append(" | ").append(total - hasImei)
                .append(" | ").append(pct(hasImei, total)).append("% |\n");
        sb.append("| GPS Coordinates | ").append(hasGps).append(" | ").append(total - hasGps)
                .append(" | ").append(pct(hasGps, total)).append("% |\n");
        sb.append("| Network Operator | ").append(hasNetwork).append(" | ").append(total - hasNetwork)
                .append(" | ").append(pct(hasNetwork, total)).append("% |\n");
        sb.append("\n**Overall Data Quality: ").append(pct(hasImei + hasGps + hasNetwork, total * 3)).append("%**");
        sb.append(" (across ").append(total).append(" devices)\n");

        return sb.toString();
    }

    private static long pct(int count, int total) {
        return total == 0 ? 0 : Math.round(count * 100.0 / total);
    }

    private static boolean isMissingImei(String value) {
        String v = value.trim();
        return v.isEmpty() || v.equals("0") || v.equalsIgnoreCase("none")
                || v.equalsIgnoreCase("null") || v.equalsIgnoreCase("n/a") || v.equalsIgnoreCase("na");
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
