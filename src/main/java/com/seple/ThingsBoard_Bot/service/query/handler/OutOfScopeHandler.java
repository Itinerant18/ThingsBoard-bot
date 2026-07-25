package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.NotTracked;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * OUT_OF_SCOPE — capability-boundary replies for questions whose data the bot never has
 * (S-Vault, platform self-metrics, FGMO, branch master-data, MTTR/MTTA/SLA-breach timing).
 * The specific message comes from {@link NotTracked}, keyed off the question text.
 */
@Component
public class OutOfScopeHandler implements AnswerHandler {

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.OUT_OF_SCOPE;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        String msg = NotTracked.messageFor(query.getOriginalQuestion());
        // Fallback if routed here without a keyword match (shouldn't happen via the resolver).
        return msg != null ? msg
                : "That information isn't tracked by this monitoring bot.";
    }
}
