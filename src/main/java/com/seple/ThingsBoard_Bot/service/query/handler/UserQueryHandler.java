package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.client.UserAwareThingsBoardClient;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.TbUser;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

@Component
public class UserQueryHandler implements AnswerHandler {

    private final UserAwareThingsBoardClient client;

    public UserQueryHandler(UserAwareThingsBoardClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.USER_QUERY;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        List<TbUser> users = client.fetchUsers(null, customerId, 100);
        
        if (users == null || users.isEmpty()) {
            return "There are no users registered for this account.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here is the list of registered users (Total: ").append(users.size()).append("):\n\n");
        for (TbUser user : users) {
            sb.append("- ").append(user.getFullName()).append(" (").append(user.getEmail()).append(") [").append(user.getAuthority()).append("]\n");
        }
        return sb.toString();
    }
}
