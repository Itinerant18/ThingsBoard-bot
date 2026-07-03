package com.seple.ThingsBoard_Bot.bench;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.seple.ThingsBoard_Bot.bench.ThresholdCalibrationSweep.SweepRow;
import com.seple.ThingsBoard_Bot.bench.ThresholdCalibrationSweep.ThresholdCombo;

/**
 * Phase 5, Task 5.1 - prints the calibration sweep table to the build log and asserts the
 * two invariants that matter regardless of which combo ships:
 *
 * <ul>
 *   <li>the CURRENT DEFAULT (0.90 / 0.75 / 0.55, matching FuzzyBranchResolver's @Value
 *       defaults) holds both the 98% entity-resolution gate and zero fabrication</li>
 *   <li>NO combo in the grid - including looser ones - ever lets a made-up branch name
 *       resolve silently; fabrication safety must not be threshold-dependent</li>
 * </ul>
 */
class ThresholdCalibrationSweepTest {

    @Test
    void sweepAndValidateCurrentDefault() throws Exception {
        ThresholdCalibrationSweep sweep = new ThresholdCalibrationSweep();

        List<ThresholdCombo> grid = List.of(
                new ThresholdCombo(0.95, 0.80, 0.60), // stricter
                new ThresholdCombo(0.90, 0.75, 0.55), // current default
                new ThresholdCombo(0.85, 0.70, 0.50), // looser
                new ThresholdCombo(0.80, 0.65, 0.45), // much looser
                new ThresholdCombo(0.90, 0.75, 0.40)  // wider suggestion floor only
        );

        List<SweepRow> rows = sweep.run(grid);

        System.out.println();
        System.out.println("=== THRESHOLD CALIBRATION SWEEP ===");
        System.out.println(sweep.render(rows));

        for (SweepRow row : rows) {
            assertTrue(row.safe(), "fabrication leak at " + row.combo() + ": " + row.fabricationLeaks() + " leaks");
        }

        SweepRow current = rows.stream()
                .filter(r -> r.combo().equals(new ThresholdCombo(0.90, 0.75, 0.55)))
                .findFirst().orElseThrow();
        assertTrue(current.recallRate() >= 0.98,
                "current default entity-resolution recall " + current.recallRate() + " below 98% gate");
    }
}
