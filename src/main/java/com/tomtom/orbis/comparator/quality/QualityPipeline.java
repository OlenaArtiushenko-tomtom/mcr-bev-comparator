package com.tomtom.orbis.comparator.quality;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.quality.check.*;

import java.util.ArrayList;
import java.util.List;

public class QualityPipeline {
    private final List<QualityCheck> checks = new ArrayList<>();

    public QualityPipeline(boolean mcrAvailable) {
        checks.add(new CompletenessCheck());
        checks.add(new FormatValidationCheck());
        checks.add(new DuplicateCheck());
        checks.add(new SpatialAnomalyCheck());
        if (mcrAvailable) {
            checks.add(new McrCoverageCheck());
        }
        checks.add(new StructuralCheck());
    }

    public QualityReport run(List<AddressPoint> sourcePoints, CheckContext context,
                              String h3Tile, String sourceName) {
        List<FlaggedPoint> flagged = sourcePoints.stream()
                .map(FlaggedPoint::new)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        List<DimensionResult> results = new ArrayList<>();
        for (QualityCheck check : checks) {
            System.out.printf("       Running %s check...%n", check.dimensionName());
            results.add(check.run(flagged, context));
        }

        double overall = computeWeightedScore(results);
        return new QualityReport(overall, results, flagged, h3Tile, sourceName);
    }

    private double computeWeightedScore(List<DimensionResult> results) {
        double weightedSum = 0;
        double totalWeight = 0;
        for (DimensionResult r : results) {
            if (r.weight() > 0) {
                weightedSum += r.score() * r.weight();
                totalWeight += r.weight();
            }
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0;
    }
}
