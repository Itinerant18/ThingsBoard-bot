package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/** DEVICE_IMEI — reports the gateway device IMEI from raw telemetry. */
@Component
public class DeviceIdentityHandler implements AnswerHandler {

    private final AnswerSupport support;

    public DeviceIdentityHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.DEVICE_IMEI;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        BranchSnapshot branch = query.getTargetBranch();
        if (branch == null) {
            return null;
        }
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
}
