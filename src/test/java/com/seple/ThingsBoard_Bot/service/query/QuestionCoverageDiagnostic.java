package com.seple.ThingsBoard_Bot.service.query;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.normalization.BranchSnapshotMapper;
import com.seple.ThingsBoard_Bot.service.normalization.FieldPrecedenceResolver;
import com.seple.ThingsBoard_Bot.service.normalization.FullDataPayloadParser;
import com.seple.ThingsBoard_Bot.service.normalization.ValueNormalizer;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;
import com.seple.ThingsBoard_Bot.service.query.resolve.ManualAliasTable;
import com.seple.ThingsBoard_Bot.support.FixtureLoader;

/**
 * Diagnostic (not an assertion): runs every question in Thingsboard-Data/qustions.txt through the
 * intent resolver and prints the intent distribution plus the GENERAL_LLM questions — those have no
 * deterministic handler, so the bot answers them from the raw LLM (hallucination risk on data).
 * ponytail: throwaway coverage probe, delete once the gaps are triaged.
 */
class QuestionCoverageDiagnostic {

    @Test
    void reportIntentCoverage() throws Exception {
        Path file = Path.of("Thingsboard-Data/qustions.txt");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "questions file absent — skipping diagnostic");
        Pattern numbered = Pattern.compile("^\\s*\\d+\\.\\s*(.+?)\\s*$");
        List<String> questions = Files.readAllLines(file).stream()
                .map(l -> {
                    Matcher m = numbered.matcher(l);
                    return m.matches() ? m.group(1) : null;
                })
                .filter(q -> q != null && !q.isBlank())
                .collect(Collectors.toList());

        QueryIntentResolver resolver = newResolver();
        List<BranchSnapshot> snapshots = loadSnapshots();

        // Theme buckets for the GENERAL_LLM set (first match wins). Splits "needs data the system
        // doesn't have" from "wireable gap" from "LLM is the right answer".
        String[][] themes = {
            {"HEALTH-SCORE / RANKING / TREND (needs score+monthly data)", "SCORE|TREND|MONTH|IMPROV|DECLIN|DISTRIBUTION|BEST|WORST|POOR|AVERAGE|RANK|TOP |BOTTOM"},
            {"REPORT / EXPORT (needs report gen)", "REPORT|EXPORT|GENERATE|SUMMARY"},
            {"RECOMMENDATION / ADVISORY (LLM-appropriate)", "SHOULD|INVESTIGAT|PRIORIT|RECOMMEND|ACTION|ATTENTION|URGENT|RISK"},
            {"MAP / GEO (needs coordinates)", "MAP|ZOOM|NEAR|LOCAT|KOLKATA"},
            {"BRANCH DETAIL / SEARCH / IS-HEALTHY (wireable / fixture-missing)", "DETAIL|SEARCH|IS .* HEALTHY|HEALTHY\\?|CODE "},
            {"DEVICE INVENTORY (wireable)", "INVENTORY|SHOW .*DEVICE|DEVICE COUNT|AVAILABILITY"},
        };

        Map<QueryIntent, Integer> dist = new TreeMap<>();
        Map<String, Integer> themeCounts = new java.util.LinkedHashMap<>();
        for (String[] t : themes) themeCounts.put(t[0], 0);
        themeCounts.put("OTHER", 0);
        int generalCount = 0;
        for (String q : questions) {
            QueryIntent intent;
            try {
                intent = resolver.resolve(q, snapshots, null).getIntent();
            } catch (Exception e) {
                intent = QueryIntent.GENERAL_LLM;
            }
            dist.merge(intent, 1, Integer::sum);
            if (intent == QueryIntent.GENERAL_LLM) {
                generalCount++;
                String up = q.toUpperCase();
                String matched = "OTHER";
                for (String[] t : themes) {
                    if (Pattern.compile(t[1]).matcher(up).find()) { matched = t[0]; break; }
                }
                themeCounts.merge(matched, 1, Integer::sum);
            }
        }
        StringBuilder generalLlm = new StringBuilder();
        themeCounts.forEach((k, v) -> generalLlm.append(String.format("  %-58s %d%n", k, v)));

        System.out.println("\n================ QUESTION COVERAGE ================");
        System.out.println("Total questions: " + questions.size());
        System.out.println("\nIntent distribution:");
        dist.forEach((k, v) -> System.out.printf("  %-28s %d%n", k, v));
        System.out.println("\nGENERAL_LLM (no deterministic handler) — first 120 of " + generalCount + ":");
        System.out.println(generalLlm);
    }

    private QueryIntentResolver newResolver() {
        BranchAliasIndex aliasIndex = new BranchAliasIndex();
        return new QueryIntentResolver(aliasIndex,
                new FuzzyBranchResolver(aliasIndex, new ManualAliasTable(aliasIndex), 0.90, 0.75, 0.55),
                new com.seple.ThingsBoard_Bot.service.query.glossary.GlossaryService(
                        new org.springframework.core.io.ClassPathResource("glossary.json")));
    }

    private List<BranchSnapshot> loadSnapshots() throws Exception {
        FullDataPayloadParser parser = new FullDataPayloadParser();
        BranchSnapshotMapper mapper = new BranchSnapshotMapper(
                new FieldPrecedenceResolver(new ValueNormalizer()), new ValueNormalizer());
        String json = FixtureLoader.load("fixtures/full_data_fixture.json");
        return parser.parse(json).branches().values().stream().map(mapper::map).collect(Collectors.toList());
    }
}
