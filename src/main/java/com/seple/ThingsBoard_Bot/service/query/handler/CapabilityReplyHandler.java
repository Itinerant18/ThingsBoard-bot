package com.seple.ThingsBoard_Bot.service.query.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

import lombok.extern.slf4j.Slf4j;

/**
 * HOW_TO, NAVIGATION, TROUBLESHOOTING (Phase 3 - descoped Task 3.1). The bot is the sole
 * dashboard interface, so there is no UI to guide users through and no actions they can
 * perform: these intents get fixed, honest capability replies loaded from
 * {@code capability-replies.properties} (wording editable without a rebuild).
 */
@Slf4j
@Component
public class CapabilityReplyHandler implements AnswerHandler {

    private static final String RESOURCE = "capability-replies.properties";

    private final Map<QueryIntent, String> replies = new EnumMap<>(QueryIntent.class);

    public CapabilityReplyHandler() {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                log.warn("{} not found on classpath - capability replies are empty", RESOURCE);
            }
        } catch (IOException e) {
            log.warn("Failed to load {} - capability replies are empty", RESOURCE, e);
        }
        for (QueryIntent intent : List.of(QueryIntent.HOW_TO, QueryIntent.TROUBLESHOOTING)) {
            String reply = properties.getProperty(intent.name());
            if (reply != null && !reply.isBlank()) {
                replies.put(intent, reply.trim());
            }
        }
        log.info("Capability replies loaded: {} intents", replies.size());
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return replies.containsKey(intent);
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        return replies.get(query.getIntent());
    }
}
