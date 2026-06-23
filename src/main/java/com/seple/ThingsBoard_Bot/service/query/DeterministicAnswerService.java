package com.seple.ThingsBoard_Bot.service.query;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.dto.GlobalOverviewCounters;
import com.seple.ThingsBoard_Bot.service.query.handler.AccessControlHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.AlertHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.AnswerHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.AnswerSupport;
import com.seple.ThingsBoard_Bot.service.query.handler.CctvHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.DeviceIdentityHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.DeviceInventoryHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.DoorStatusHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.FaultReasonHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.GatewayStatusHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.GlobalOverviewHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.NetworkStatusHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.PowerHandler;
import com.seple.ThingsBoard_Bot.service.query.handler.SubsystemHandler;

/**
 * Routes a {@link ResolvedQuery} to the {@link AnswerHandler} that supports its intent and returns
 * the deterministic answer string (or {@code null} to fall back to the LLM path).
 *
 * <p>The per-intent logic lives in the {@code handler} package; this class is just the dispatcher.
 * Two construction paths are supported:
 * <ul>
 *   <li>Spring injects the discovered {@code List<AnswerHandler>} beans via the {@code @Autowired}
 *       constructor.</li>
 *   <li>The single-arg convenience constructor self-wires the default handler set, so existing
 *       unit tests that do {@code new DeterministicAnswerService(new AnswerTemplateService())}
 *       keep working unchanged.</li>
 * </ul>
 */
@Service
public class DeterministicAnswerService {

    private final AnswerTemplateService answerTemplateService;
    private final List<AnswerHandler> handlers;

    @Autowired
    public DeterministicAnswerService(AnswerTemplateService answerTemplateService, List<AnswerHandler> handlers) {
        this.answerTemplateService = answerTemplateService;
        this.handlers = handlers;
    }

    /** Convenience constructor that self-wires the default handler set (used by unit tests). */
    public DeterministicAnswerService(AnswerTemplateService answerTemplateService) {
        this(answerTemplateService, defaultHandlers(answerTemplateService));
    }

    private static List<AnswerHandler> defaultHandlers(AnswerTemplateService ats) {
        AnswerSupport support = new AnswerSupport();
        return List.of(
                new GlobalOverviewHandler(ats),
                new GatewayStatusHandler(ats, support),
                new PowerHandler(ats, support),
                new DeviceInventoryHandler(ats, support),
                new NetworkStatusHandler(support),
                new CctvHandler(ats, support),
                new AlertHandler(ats),
                new SubsystemHandler(ats, support),
                new DoorStatusHandler(support),
                new AccessControlHandler(support),
                new FaultReasonHandler(support),
                new DeviceIdentityHandler(support));
    }

    public String answer(ResolvedQuery query, List<BranchSnapshot> snapshots) {
        return answer(query, snapshots, "BOI");
    }

    public String answer(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        QueryIntent intent = query.getIntent();
        for (AnswerHandler handler : handlers) {
            if (handler.supports(intent)) {
                return handler.handle(query, snapshots, customerId);
            }
        }
        // GENERAL_LLM (and any unhandled intent) -> defer to the LLM path.
        return null;
    }

    public String answerGlobalOverview(GlobalOverviewCounters counters) {
        if (counters == null) {
            return null;
        }
        int online = counters.getOnlineBranches() != null ? counters.getOnlineBranches() : 0;
        int offline = counters.getOfflineBranches() != null ? counters.getOfflineBranches() : 0;
        return answerTemplateService.renderGlobalOverviewFromCounters(online, offline);
    }
}
