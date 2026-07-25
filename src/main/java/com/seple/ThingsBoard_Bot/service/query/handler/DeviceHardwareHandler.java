package com.seple.ThingsBoard_Bot.service.query.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.seple.ThingsBoard_Bot.model.domain.BranchSnapshot;
import com.seple.ThingsBoard_Bot.model.domain.HardwareHealth;
import com.seple.ThingsBoard_Bot.service.query.QueryIntent;
import com.seple.ThingsBoard_Bot.service.query.ResolvedQuery;

/**
 * DEVICE_HARDWARE — gateway host (Raspberry Pi) health from telemetry: CPU %, memory %, disk %,
 * temperature (°C), plus the device firmware/OTA version. These are <b>device-local</b> gateway
 * metrics, not any external storage/S-Vault figure.
 *
 * <p>The same intent serves firmware questions ("current firmware version"): the handler branches on
 * a FIRMWARE/SOFTWARE/VERSION keyword and answers from {@code target_sw_version} / {@code sw_state}.
 */
@Component
public class DeviceHardwareHandler implements AnswerHandler {

    private final AnswerSupport support;

    public DeviceHardwareHandler(AnswerSupport support) {
        this.support = support;
    }

    @Override
    public boolean supports(QueryIntent intent) {
        return intent == QueryIntent.DEVICE_HARDWARE;
    }

    @Override
    public String handle(ResolvedQuery query, List<BranchSnapshot> snapshots, String customerId) {
        String q = query.getOriginalQuestion() == null ? ""
                : query.getOriginalQuestion().toUpperCase(Locale.ROOT);
        boolean firmware = q.contains("FIRMWARE") || q.contains("SOFTWARE") || q.contains("VERSION")
                || q.contains("OTA");

        BranchSnapshot branch = query.getTargetBranch();
        if (branch != null) {
            return firmware ? firmwareForBranch(branch) : hardwareForBranch(branch);
        }
        return firmware ? firmwareForFleet(snapshots) : hardwareForFleet(snapshots);
    }

    // ── Firmware ─────────────────────────────────────────────────────────────

    private String firmwareForBranch(BranchSnapshot branch) {
        String ver = support.firstNonBlank(branch.getRawData(), "target_sw_version", "sw_version", "fw_version");
        if (ver == null) {
            return "**For Branch " + support.branchName(branch) + ", the firmware version is not reported.**";
        }
        String state = support.firstNonBlank(branch.getRawData(), "sw_state");
        return "**For Branch " + support.branchName(branch) + ", the device firmware is v" + ver
                + (state != null ? " (OTA state: " + state + ")" : "") + ".**";
    }

    private String firmwareForFleet(List<BranchSnapshot> snapshots) {
        java.util.Map<String, Integer> byVersion = new java.util.TreeMap<>();
        int reported = 0;
        for (BranchSnapshot s : snapshots) {
            String ver = support.firstNonBlank(s.getRawData(), "target_sw_version", "sw_version", "fw_version");
            if (ver == null) continue;
            reported++;
            byVersion.merge("v" + ver, 1, Integer::sum);
        }
        if (reported == 0) {
            return "No device firmware version is reported across the branches in scope.";
        }
        StringBuilder b = new StringBuilder("**Device firmware across ").append(reported)
                .append(" reporting device(s):**\n");
        byVersion.forEach((ver, count) -> b.append("- ").append(ver).append(": ").append(count).append(" device(s)\n"));
        return b.toString();
    }

    // ── Hardware metrics ─────────────────────────────────────────────────────

    private String hardwareForBranch(BranchSnapshot branch) {
        HardwareHealth h = branch.getHardware();
        if (h == null || (h.getCpu() == null && h.getMemory() == null && h.getDisk() == null && h.getTemperature() == null)) {
            return "**For Branch " + support.branchName(branch)
                    + ", device hardware metrics (CPU/memory/disk/temperature) are not reported.**";
        }
        return "**For Branch " + support.branchName(branch) + ", gateway hardware:** "
                + "CPU " + fmt(h.getCpu(), "%") + ", memory " + fmt(h.getMemory(), "%")
                + ", disk " + fmt(h.getDisk(), "%") + ", temperature " + fmt(h.getTemperature(), "°C") + ".";
    }

    private String hardwareForFleet(List<BranchSnapshot> snapshots) {
        List<double[]> disks = new ArrayList<>(); // [diskPct, index into names]
        List<String> names = new ArrayList<>();
        double cpuSum = 0, memSum = 0, diskSum = 0, tempSum = 0;
        int cpuN = 0, memN = 0, diskN = 0, tempN = 0;
        for (BranchSnapshot s : snapshots) {
            HardwareHealth h = s.getHardware();
            if (h == null) continue;
            if (h.getCpu() != null) { cpuSum += h.getCpu(); cpuN++; }
            if (h.getMemory() != null) { memSum += h.getMemory(); memN++; }
            if (h.getTemperature() != null) { tempSum += h.getTemperature(); tempN++; }
            if (h.getDisk() != null) {
                diskSum += h.getDisk(); diskN++;
                names.add(support.branchName(s));
                disks.add(new double[]{h.getDisk(), names.size() - 1});
            }
        }
        if (cpuN == 0 && memN == 0 && diskN == 0 && tempN == 0) {
            return "No gateway hardware metrics are reported across the branches in scope.";
        }
        StringBuilder b = new StringBuilder("**Gateway hardware across fleet (device-local metrics):**\n");
        b.append("- Avg CPU: ").append(avg(cpuSum, cpuN, "%")).append("\n");
        b.append("- Avg memory: ").append(avg(memSum, memN, "%")).append("\n");
        b.append("- Avg disk: ").append(avg(diskSum, diskN, "%")).append("\n");
        b.append("- Avg temperature: ").append(avg(tempSum, tempN, "°C")).append("\n");
        // Top 5 branches by disk utilization — the ones nearest capacity.
        disks.sort((x, y) -> Double.compare(y[0], x[0]));
        if (!disks.isEmpty()) {
            b.append("\nHighest disk utilization:\n");
            for (int i = 0; i < Math.min(5, disks.size()); i++) {
                b.append("  - ").append(names.get((int) disks.get(i)[1]))
                        .append(": ").append(fmt(disks.get(i)[0], "%")).append("\n");
            }
        }
        return b.toString();
    }

    private static String avg(double sum, int n, String unit) {
        return n == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f%s", sum / n, unit);
    }

    private static String fmt(Double v, String unit) {
        return v == null ? "n/a" : String.format(Locale.ROOT, "%.1f%s", v, unit);
    }
}
