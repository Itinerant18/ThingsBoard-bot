package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.NormalizedState;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * Fleet-wide analytics over the already region-scoped snapshot list: category (device-type) health
 * breakdown. Operates on the whole snapshot set, so it answers questions the per-branch handlers and
 * the global online/offline overview can't. Counts are derived from the same normalized states the
 * rest of the pipeline uses; nothing is inferred.
 */
@Component
public class FleetAnalyticsHandler implements AnswerHandler {

    /** A device category and how to read its normalized state from a branch snapshot. */
    private record Category(String label, Function<BranchSnapshot, NormalizedState> stateOf) {
    }

    private final List<Category> categories = List.of(
            new Category("Gateway", s -> s.getGateway().getState()),
            new Category("CCTV", s -> s.getSubsystems().getCctv().getState()),
            new Category("IAS", s -> s.getSubsystems().getIas().getState()),
            new Category("BAS", s -> s.getSubsystems().getBas().getState()),
            new Category("FAS", s -> s.getSubsystems().getFas().getState()),
            new Category("Time Lock", s -> s.getSubsystems().getTimeLock().getState()),
            new Category("Access Control", s -> s.getSubsystems().getAccessControl().getState()));

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.CATEGORY_HEALTH;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "**No devices are available in your scope.**";
        }
        String question = upper(query.getOriginalQuestion());
        Map<String, Counts> counts = countByCategory(snapshots);

        // "which category has the most inactive/offline devices"
        if (question.contains("WHICH CATEGORY")
                && (question.contains("INACTIVE") || question.contains("OFFLINE") || question.contains("MOST"))) {
            return answerWorstCategory(counts);
        }

        // A single category named -> just that one.
        Category target = matchCategory(question);
        if (target != null) {
            return "**" + line(target.label(), counts.get(target.label())) + "**";
        }

        // Otherwise a full category-wise breakdown.
        StringBuilder builder = new StringBuilder("**Category-wise health (healthy = online of installed):**\n");
        for (Category category : categories) {
            builder.append("- ").append(line(category.label(), counts.get(category.label()))).append("\n");
        }
        return builder.toString();
    }

    private String answerWorstCategory(Map<String, Counts> counts) {
        String worst = null;
        int maxOffline = -1;
        for (Category category : categories) {
            int offline = counts.get(category.label()).offline;
            if (offline > maxOffline) {
                maxOffline = offline;
                worst = category.label();
            }
        }
        if (worst == null || maxOffline <= 0) {
            return "**No category has any inactive devices in your scope.**";
        }
        return "**The category with the most inactive devices is " + worst + " (" + maxOffline + " offline).**";
    }

    private Map<String, Counts> countByCategory(List<BranchSnapshot> snapshots) {
        Map<String, Counts> result = new LinkedHashMap<>();
        for (Category category : categories) {
            Counts counts = new Counts();
            for (BranchSnapshot snapshot : snapshots) {
                NormalizedState state = category.stateOf().apply(snapshot);
                switch (state) {
                    case ONLINE -> counts.online++;
                    case OFFLINE, FAULT -> counts.offline++;
                    case NOT_INSTALLED -> counts.notInstalled++;
                    default -> counts.unknown++;
                }
            }
            result.put(category.label(), counts);
        }
        return result;
    }

    private String line(String label, Counts c) {
        int installed = c.online + c.offline;
        String pct = installed > 0 ? Math.round(c.online * 100.0 / installed) + "%" : "N/A";
        List<String> detail = new ArrayList<>();
        detail.add(c.online + " online");
        detail.add(c.offline + " offline");
        if (c.unknown > 0) {
            detail.add(c.unknown + " unknown");
        }
        if (c.notInstalled > 0) {
            detail.add(c.notInstalled + " not installed");
        }
        return label + ": " + c.online + " of " + installed + " healthy (" + pct + ") — "
                + String.join(" | ", detail) + ".";
    }

    private Category matchCategory(String question) {
        for (Category category : categories) {
            if (matches(question, category.label())) {
                return category;
            }
        }
        return null;
    }

    private boolean matches(String question, String label) {
        return switch (label) {
            case "Gateway" -> question.contains("GATEWAY");
            case "CCTV" -> question.contains("CCTV") || question.contains("CAMERA");
            case "IAS" -> question.contains("IAS");
            case "BAS" -> question.contains("BAS");
            case "FAS" -> question.contains("FAS");
            case "Time Lock" -> question.contains("TLS") || question.contains("TIME LOCK");
            case "Access Control" -> question.contains("ACS") || question.contains("ACCESS CONTROL");
            default -> false;
        };
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private static final class Counts {
        int online;
        int offline;
        int unknown;
        int notInstalled;
    }
}
