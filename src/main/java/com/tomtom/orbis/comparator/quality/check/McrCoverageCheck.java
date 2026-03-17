package com.tomtom.orbis.comparator.quality.check;

import com.tomtom.orbis.comparator.model.ComparisonResult;
import com.tomtom.orbis.comparator.quality.*;

import java.util.*;

public class McrCoverageCheck implements QualityCheck {
    @Override public String dimensionName() { return "mcr_coverage"; }
    @Override public double weight() { return 15; }

    @Override
    public DimensionResult run(List<FlaggedPoint> points, CheckContext ctx) {
        ComparisonResult comp = ctx.mcrComparison();
        if (comp == null || !comp.isGroundTruthAvailable()) {
            return new DimensionResult(dimensionName(), 0, 0, List.of(
                    new Anomaly(Severity.INFO, dimensionName(), "MCR_NOT_AVAILABLE",
                            "MCR comparison not available — skipping coverage check", null, null, null)
            ), Map.of("skipped", true));
        }

        List<Anomaly> anomalies = new ArrayList<>();
        double recall = comp.getRecall();
        double precision = comp.getPrecision();
        double f1 = comp.getF1();
        double avgDist = comp.getAvgMatchDistance();

        // Flag unmatched source points
        Set<String> matchedGtIds = new HashSet<>();
        for (var m : comp.getMatched()) {
            matchedGtIds.add(m.groundTruth().getId());
        }
        int unmatched = 0;
        for (FlaggedPoint fp : points) {
            if (!matchedGtIds.contains(fp.getPoint().getId())) {
                // Only flag if this point was part of the comparison (source = ground truth side)
                if ("source".equals(fp.getPoint().getSource()) || "bev".equals(fp.getPoint().getSource())) {
                    fp.addFlag(QualityFlag.MCR_UNMATCHED);
                    unmatched++;
                }
            }
        }

        if (recall < 80) {
            anomalies.add(new Anomaly(Severity.CRITICAL, dimensionName(), "LOW_RECALL",
                    String.format("Only %.1f%% of source addresses found in MCR", recall), null, null, null));
        } else if (recall < 95) {
            anomalies.add(new Anomaly(Severity.WARNING, dimensionName(), "MODERATE_RECALL",
                    String.format("%.1f%% of source addresses found in MCR", recall), null, null, null));
        }

        if (avgDist > 3.0) {
            anomalies.add(new Anomaly(Severity.WARNING, dimensionName(), "HIGH_AVG_DISTANCE",
                    String.format("Average match distance %.2fm (>3m suggests positional issues)", avgDist),
                    null, null, null));
        }

        int extraInMcr = comp.getMcrOnly().size();
        if (extraInMcr > 0) {
            anomalies.add(new Anomaly(Severity.INFO, dimensionName(), "MCR_EXTRA",
                    String.format("%d MCR addresses not in source data", extraInMcr), null, null, null));
        }

        // Score: 70% F1 + 30% positional accuracy
        double posScore = Math.max(0, 100 - avgDist * 20);
        double score = f1 * 0.7 + posScore * 0.3;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("recall_pct", Math.round(recall * 10) / 10.0);
        details.put("precision_pct", Math.round(precision * 10) / 10.0);
        details.put("f1_pct", Math.round(f1 * 10) / 10.0);
        details.put("avg_match_distance_m", Math.round(avgDist * 100) / 100.0);
        details.put("source_unmatched", unmatched);
        details.put("mcr_extra", extraInMcr);

        return new DimensionResult(dimensionName(), score, weight(), anomalies, details);
    }
}
