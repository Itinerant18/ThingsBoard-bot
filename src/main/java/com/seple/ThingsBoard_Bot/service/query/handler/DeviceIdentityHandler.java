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
        if (imei != null) {
            return "**For Branch " + support.branchName(branch) + ", the device IMEI is " + imei + ".**";
        }
        return "**For Branch " + support.branchName(branch) + ", the device IMEI is not available.**";
    }
}
