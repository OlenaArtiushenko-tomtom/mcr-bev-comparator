package com.tomtom.orbis.comparator.quality.check;

import com.tomtom.orbis.comparator.quality.*;
import com.uber.h3core.H3Core;

import java.util.*;

public class SpatialAnomalyCheck implements QualityCheck {
    @Override public String dimensionName() { return "spatial"; }
    @Override public double weight() { return 20; }

    @Override
    public DimensionResult run(List<FlaggedPoint> points, CheckContext ctx) {
        int total = points.size();
        if (total == 0) return new DimensionResult(dimensionName(), 100, weight(), List.of(), Map.of());

        List<Anomaly> anomalies = new ArrayList<>();
        int outsideTile = 0;
        int densityOutliers = 0;

        // Outside tile boundary
        for (FlaggedPoint fp : points) {
            if (ctx.tileBoundary() != null && !ctx.tileBoundary().contains(fp.getPoint().getGeometry())) {
                fp.addFlag(QualityFlag.OUTSIDE_TILE);
                outsideTile++;
            }
        }

        if (outsideTile > 0) {
            anomalies.add(new Anomaly(Severity.CRITICAL, dimensionName(), "OUTSIDE_TILE",
                    String.format("%d points are outside the H3 tile boundary", outsideTile),
                    null, null, null));
        }

        // Density outliers using H3 resolution 9
        try {
            H3Core h3 = H3Core.newInstance();
            Map<String, List<FlaggedPoint>> cells = new HashMap<>();

            for (FlaggedPoint fp : points) {
                if (fp.getFlags().contains(QualityFlag.OUTSIDE_TILE)) continue;
                double lat = fp.getPoint().getGeometry().getY();
                double lon = fp.getPoint().getGeometry().getX();
                String cell = h3.latLngToCellAddress(lat, lon, 9);
                cells.computeIfAbsent(cell, k -> new ArrayList<>()).add(fp);
            }

            if (!cells.isEmpty()) {
                double mean = cells.values().stream().mapToInt(List::size).average().orElse(0);
                double variance = cells.values().stream()
                        .mapToDouble(g -> Math.pow(g.size() - mean, 2)).average().orElse(0);
                double stddev = Math.sqrt(variance);
                double threshold = mean + 3 * stddev;

                for (var entry : cells.entrySet()) {
                    if (entry.getValue().size() > threshold && threshold > 0) {
                        for (FlaggedPoint fp : entry.getValue()) {
                            fp.addFlag(QualityFlag.DENSITY_OUTLIER_HIGH);
                            densityOutliers++;
                        }
                    }
                }

                if (densityOutliers > 0) {
                    anomalies.add(new Anomaly(Severity.WARNING, dimensionName(), "DENSITY_OUTLIER_HIGH",
                            String.format("%d points in abnormally dense H3-res9 cells (threshold: %.0f per cell, mean: %.1f)",
                                    densityOutliers, threshold, mean),
                            null, null, null));
                }
            }
        } catch (Exception e) {
            anomalies.add(new Anomaly(Severity.INFO, dimensionName(), "H3_ERROR",
                    "Could not perform density analysis: " + e.getMessage(), null, null, null));
        }

        double anomalyRate = (double) (outsideTile * 5 + densityOutliers) / (total * 5);
        double score = Math.max(0, (1.0 - Math.min(1.0, anomalyRate)) * 100);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outside_tile", outsideTile);
        details.put("density_outliers", densityOutliers);

        return new DimensionResult(dimensionName(), score, weight(), anomalies, details);
    }
}
