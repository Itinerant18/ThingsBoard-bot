package com.seple.ThingsBoard_Bot.bench;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.service.normalization.BranchAliasIndex;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchDictionary;
import com.seple.ThingsBoard_Bot.service.query.resolve.BranchResolution;
import com.seple.ThingsBoard_Bot.service.query.resolve.FuzzyBranchResolver;
import com.seple.ThingsBoard_Bot.service.query.resolve.ManualAliasTable;
import com.seple.ThingsBoard_Bot.support.FixtureLoader;
import com.seple.ThingsBoard_Bot.support.MockSnapshotStore;

/**
 * Phase 5, Task 5.1 - offline half of threshold calibration. Grid-searches the fuzzy
 * resolver's three bands against two independent signals:
 *
 * <ul>
 *   <li>recall - does the typo/entity-resolution scenario set from the golden bench still
 *       resolve to the RIGHT branch silently (the 98% entity-resolution release gate)?</li>
 *   <li>fabrication safety - does a fixed corpus of made-up, garbage-looking branch names
 *       ever get RESOLVED (the 0% fabrication gate)? These must NEVER silently resolve,
 *       at any threshold in the grid.</li>
 * </ul>
 *
 * The extractor confidence gate cannot be calibrated offline - that half needs
 * {@code extractor.agreement} data from shadow mode running against real traffic
 * (see plans/phase5_calibration_and_handoff.md).
 */
public final class ThresholdCalibrationSweep {

    public record ThresholdCombo(double silent, double confirm, double floor) {
        @Override
        public String toString() {
            return String.format("silent=%.2f confirm=%.2f floor=%.2f", silent, confirm, floor);
        }
    }

    public record SweepRow(ThresholdCombo combo, int recallPassed, int recallTotal, int fabricationLeaks) {
        public double recallRate() {
            return recallTotal == 0 ? 1.0 : (double) recallPassed / recallTotal;
        }

        public boolean safe() {
            return fabricationLeaks == 0;
        }
    }

    /** Known-garbage entity strings - must NEVER resolve to a real branch at any threshold. */
    private static final List<String> FABRICATION_PROBES = List.of(
            "QXZPLW999", "ZZZNOTREAL", "1234XYZ", "asdkfjhalskdjf", "NONEXISTENTBRANCH",
            "FAKEBRANCHNAME", "XXNOWHEREXX");

    private final List<BranchSnapshot> snapshots;
    private final BranchAliasIndex aliasIndex;
    private final ManualAliasTable manualAliasTable;
    private final BranchDictionary dictionary;
    private final List<JsonNode> entityResolutionScenarios;

    public ThresholdCalibrationSweep() throws Exception {
        this.snapshots = MockSnapshotStore.loadDefault();
        this.aliasIndex = new BranchAliasIndex();
        this.manualAliasTable = new ManualAliasTable(aliasIndex);
        this.dictionary = BranchDictionary.fromSnapshots(snapshots, "BOI", aliasIndex);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode all = objectMapper.readTree(FixtureLoader.load("fixtures/golden_questions.json"));
        List<JsonNode> filtered = new ArrayList<>();
        for (JsonNode s : all) {
            if ("entity_resolution".equals(s.path("category").asText()) && s.hasNonNull("matchedBranch")) {
                filtered.add(s);
            }
        }
        this.entityResolutionScenarios = filtered;
    }

    public List<SweepRow> run(List<ThresholdCombo> grid) {
        List<SweepRow> rows = new ArrayList<>();
        for (ThresholdCombo combo : grid) {
            FuzzyBranchResolver resolver = new FuzzyBranchResolver(aliasIndex, manualAliasTable,
                    combo.silent(), combo.confirm(), combo.floor());

            int recallPassed = 0;
            for (JsonNode scenario : entityResolutionScenarios) {
                String question = scenario.path("question").asText();
                String expectedBranch = scenario.path("matchedBranch").asText();
                if (resolvesToExpectedBranch(resolver, question, expectedBranch)) {
                    recallPassed++;
                }
            }

            int fabricationLeaks = 0;
            for (String probe : FABRICATION_PROBES) {
                BranchResolution resolution = resolver.resolve(probe, dictionary);
                if (resolution.status() == BranchResolution.Status.RESOLVED) {
                    fabricationLeaks++;
                }
            }

            rows.add(new SweepRow(combo, recallPassed, entityResolutionScenarios.size(), fabricationLeaks));
        }
        return rows;
    }

    /**
     * Extracts the branch-name-shaped tail of the question and resolves it, mirroring the
     * word-window scan QueryIntentResolver performs in production.
     */
    private boolean resolvesToExpectedBranch(FuzzyBranchResolver resolver, String question, String expectedBranch) {
        String normalized = aliasIndex.normalize(question);
        String[] words = normalized.split("\\s+");
        for (int start = 0; start < words.length; start++) {
            for (int len = 1; len <= 3 && start + len <= words.length; len++) {
                String window = String.join(" ", java.util.Arrays.copyOfRange(words, start, start + len));
                BranchResolution resolution = resolver.resolve(window, dictionary);
                if (resolution.status() == BranchResolution.Status.RESOLVED
                        && expectedBranch.equals(resolution.match().technicalId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String render(List<SweepRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-32s %-16s %-10s %s%n", "Combo", "Recall", "Rate", "Fabrication leaks"));
        sb.append("-".repeat(80)).append(System.lineSeparator());
        for (SweepRow r : rows) {
            sb.append(String.format("%-32s %-16s %-10s %s%n",
                    r.combo().toString(),
                    r.recallPassed() + "/" + r.recallTotal(),
                    String.format("%.1f%%", r.recallRate() * 100),
                    r.safe() ? "0 (safe)" : r.fabricationLeaks() + " (UNSAFE)"));
        }
        return sb.toString();
    }
}
