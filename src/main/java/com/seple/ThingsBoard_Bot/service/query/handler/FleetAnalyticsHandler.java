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

    private final AnswerSupport support;

    public FleetAnalyticsHandler(AnswerSupport support) {
        this.support = support;
    }

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
        return intent == QueryIntent.CATEGORY_HEALTH
                || intent == QueryIntent.BRANCH_RANKING
                || intent == QueryIntent.BRANCH_COMPARE
                || intent == QueryIntent.BRANCH_FILTER;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "**No devices are available in your scope.**";
        }
        return switch (query.getIntent()) {
            case CATEGORY_HEALTH -> answerCategoryHealth(query, snapshots);
            case BRANCH_RANKING -> answerRanking(query, snapshots);
            case BRANCH_COMPARE -> answerCompare(query, snapshots);
            case BRANCH_FILTER -> answerFilter(query, snapshots);
            default -> null;
        };
    }

    private String answerCategoryHealth(ResolvedQuery query, List<BranchSnapshot> snapshots) {
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

    // ---- Ranking ----

    /** A rankable metric: how to read it, its label, and how to format a value. */
    private record Metric(String label, Function<BranchSnapshot, Double> valueOf, Function<Double, String> format) {
    }

    private Metric matchMetric(String question) {
        if (question.contains("ALARM")) {
            return new Metric("alarm count", s -> (double) s.getAlerts().getAlarmCount(), v -> String.valueOf(v.longValue()));
        }
        if (question.contains("ERROR")) {
            return new Metric("error count", s -> (double) s.getAlerts().getErrorCount(), v -> String.valueOf(v.longValue()));
        }
        if (question.contains("CAMERA") || question.contains("CCTV")) {
            return new Metric("online cameras",
                    s -> s.getCctv().getOnlineCameraCount() == null ? null : (double) s.getCctv().getOnlineCameraCount(),
                    v -> String.valueOf(v.longValue()));
        }
        if (question.contains("BATTERY")) {
            return new Metric("battery voltage", s -> s.getPower().getBatteryVoltage(), v -> v + "V");
        }
        return null;
    }

    private String answerRanking(ResolvedQuery query, List<BranchSnapshot> snapshots) {
        String question = upper(query.getOriginalQuestion());
        Metric metric = matchMetric(question);
        if (metric == null) {
            // Not a metric we can compute (e.g. "rank by health score") -> defer to the honest-decline LLM path.
            return null;
        }
        boolean ascending = question.contains("BOTTOM") || question.contains("LEAST") || question.contains("LOWEST");
        int limit = parseLimit(question, 5);

        List<Ranked> ranked = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            Double value = metric.valueOf().apply(snapshot);
            if (value != null) {
                ranked.add(new Ranked(support.branchName(snapshot), value));
            }
        }
        if (ranked.isEmpty()) {
            return "**No branches have " + metric.label() + " data in your scope.**";
        }
        ranked.sort((a, b) -> ascending ? Double.compare(a.value(), b.value()) : Double.compare(b.value(), a.value()));

        int shown = Math.min(limit, ranked.size());
        StringBuilder builder = new StringBuilder("**").append(ascending ? "Bottom " : "Top ").append(shown)
                .append(" branches by ").append(metric.label()).append(":**\n");
        for (int i = 0; i < shown; i++) {
            Ranked r = ranked.get(i);
            builder.append(i + 1).append(". ").append(r.name()).append(" — ").append(metric.format().apply(r.value())).append("\n");
        }
        return builder.toString();
    }

    private int parseLimit(String question, int fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{1,3})\\b").matcher(question);
        if (matcher.find()) {
            int n = Integer.parseInt(matcher.group(1));
            if (n > 0) {
                return n;
            }
        }
        return fallback;
    }

    // ---- Compare ----

    private String answerCompare(ResolvedQuery query, List<BranchSnapshot> snapshots) {
        String question = upper(query.getOriginalQuestion());
        List<BranchSnapshot> matched = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            if (snapshot.getIdentity() == null || snapshot.getIdentity().getAliases() == null) {
                continue;
            }
            for (String alias : snapshot.getIdentity().getAliases()) {
                String norm = normalizeName(alias);
                if (norm.length() >= 4 && question.contains(norm)) {
                    if (!matched.contains(snapshot)) {
                        matched.add(snapshot);
                    }
                    break;
                }
            }
            if (matched.size() == 2) {
                break;
            }
        }
        if (matched.size() < 2) {
            return "**Please name two branches to compare.**";
        }
        BranchSnapshot a = matched.get(0);
        BranchSnapshot b = matched.get(1);
        String nameA = support.branchName(a);
        String nameB = support.branchName(b);

        StringBuilder builder = new StringBuilder("**Comparing ").append(nameA).append(" and ").append(nameB).append(":**\n");
        builder.append("- Gateway: ").append(support.formatState(a.getGateway().getState()))
                .append(" vs ").append(support.formatState(b.getGateway().getState())).append("\n");
        builder.append("- Alarms: ").append(a.getAlerts().getAlarmCount())
                .append(" vs ").append(b.getAlerts().getAlarmCount()).append("\n");
        builder.append("- Online cameras: ").append(cameras(a)).append(" vs ").append(cameras(b)).append("\n");
        builder.append("- Battery: ").append(voltage(a)).append(" vs ").append(voltage(b));
        return builder.toString();
    }

    private String cameras(BranchSnapshot s) {
        Integer online = s.getCctv().getOnlineCameraCount();
        Integer total = s.getCctv().getCameraCount();
        if (online == null || total == null) {
            return "N/A";
        }
        return online + "/" + total;
    }

    private String voltage(BranchSnapshot s) {
        Double v = s.getPower().getBatteryVoltage();
        return v == null ? "N/A" : v + "V";
    }

    // ---- Multi-condition filter ----

    /** Expected condition for a category in a filter. */
    private enum MatchKind { ONLINE, OFFLINE, MISSING }

    private record Predicate(Category category, MatchKind kind) {
    }

    private String answerFilter(ResolvedQuery query, List<BranchSnapshot> snapshots) {
        String question = upper(query.getOriginalQuestion());

        // "all systems online" / "all systems are N/A"
        if (question.contains("ALL SYSTEMS")) {
            MatchKind kind = detectKind(question);
            if (kind == null) {
                return null;
            }
            List<String> matches = new ArrayList<>();
            for (BranchSnapshot snapshot : snapshots) {
                boolean all = categories.stream().allMatch(c -> matchesKind(c.stateOf().apply(snapshot), kind));
                if (all) {
                    matches.add(support.branchName(snapshot));
                }
            }
            return renderMatches("all systems " + kind.name().toLowerCase(Locale.ROOT), matches);
        }

        // "at least / more than / N or more active systems"
        Integer threshold = countThreshold(question);
        if (threshold != null) {
            boolean strictlyMore = question.contains("MORE THAN");
            List<String> matches = new ArrayList<>();
            for (BranchSnapshot snapshot : snapshots) {
                long online = categories.stream()
                        .filter(c -> c.stateOf().apply(snapshot) == NormalizedState.ONLINE).count();
                if (strictlyMore ? online > threshold : online >= threshold) {
                    matches.add(support.branchName(snapshot));
                }
            }
            return renderMatches((strictlyMore ? "more than " : "at least ") + threshold + " active systems", matches);
        }

        // Per-system predicates joined by AND / BUT / OR.
        List<Predicate> predicates = parsePredicates(question);
        if (predicates.isEmpty()) {
            return null;
        }
        boolean orSemantics = question.contains(" OR ");
        List<String> matches = new ArrayList<>();
        for (BranchSnapshot snapshot : snapshots) {
            boolean result = orSemantics
                    ? predicates.stream().anyMatch(p -> matchesKind(p.category().stateOf().apply(snapshot), p.kind()))
                    : predicates.stream().allMatch(p -> matchesKind(p.category().stateOf().apply(snapshot), p.kind()));
            if (result) {
                matches.add(support.branchName(snapshot));
            }
        }
        String desc = predicates.stream()
                .map(p -> p.category().label() + " " + p.kind().name().toLowerCase(Locale.ROOT))
                .reduce((a, b) -> a + (orSemantics ? " or " : " and ") + b).orElse("");
        return renderMatches(desc, matches);
    }

    private List<Predicate> parsePredicates(String question) {
        String[] clauses = question.split("\\s+(AND|BUT|OR)\\s+");
        List<Predicate> predicates = new ArrayList<>();
        MatchKind lastKind = null;
        for (String clause : clauses) {
            Category category = matchCategory(clause);
            if (category == null) {
                continue;
            }
            MatchKind kind = detectKind(clause);
            if (kind == null) {
                kind = lastKind; // inherit, e.g. "missing CCTV and ACS"
            } else {
                lastKind = kind;
            }
            if (kind != null) {
                predicates.add(new Predicate(category, kind));
            }
        }
        return predicates;
    }

    /** State asserted in a clause/question, preferring MISSING then OFFLINE then ONLINE. */
    private MatchKind detectKind(String text) {
        if (text.contains("MISSING") || text.contains("WITHOUT") || text.contains("UNAVAILABLE")
                || text.contains("N/A") || text.contains("NOT INSTALLED") || text.contains(" NO ")) {
            return MatchKind.MISSING;
        }
        if (text.contains("OFFLINE") || text.contains("INACTIVE") || text.contains("DOWN")) {
            return MatchKind.OFFLINE;
        }
        if (text.contains("ONLINE") || text.contains("ACTIVE") || text.contains("OPERATIONAL")
                || text.contains("UP")) {
            return MatchKind.ONLINE;
        }
        return null;
    }

    private boolean matchesKind(NormalizedState state, MatchKind kind) {
        return switch (kind) {
            case ONLINE -> state == NormalizedState.ONLINE;
            case OFFLINE -> state == NormalizedState.OFFLINE || state == NormalizedState.FAULT;
            case MISSING -> state == NormalizedState.NOT_INSTALLED || state == NormalizedState.UNKNOWN;
        };
    }

    private Integer countThreshold(String question) {
        boolean cue = question.contains("AT LEAST") || question.contains("OR MORE")
                || question.contains("MORE THAN") || question.contains("MINIMUM");
        if (!cue || !question.contains("SYSTEM")) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\b").matcher(question);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private String renderMatches(String condition, List<String> matches) {
        if (matches.isEmpty()) {
            return "**No branches match (" + condition + ") in your scope.**";
        }
        java.util.Collections.sort(matches);
        return "**" + matches.size() + " branch" + (matches.size() == 1 ? "" : "es")
                + " match (" + condition + "): " + String.join(", ", matches) + ".**";
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT)
                .replace("BOI-", "")
                .replace("BRANCH ", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record Ranked(String name, double value) {
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
