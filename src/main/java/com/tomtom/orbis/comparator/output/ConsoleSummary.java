package com.tomtom.orbis.comparator.output;

import com.tomtom.orbis.comparator.model.ComparisonResult;

public class ConsoleSummary {

    public void print(ComparisonResult result) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  MCR vs BEV Address Point Comparison");
        System.out.println("=".repeat(60));
        System.out.printf("  H3 Tile:       %s%n", result.getH3Tile());
        System.out.printf("  Product:       %s%n", result.getProduct());
        System.out.printf("  License Zone:  %s%n", result.getLicenseZone());
        System.out.println("-".repeat(60));
        System.out.printf("  BEV (ground truth):     %,d%n", result.getTotalBev());
        System.out.printf("  MCR (in tile):          %,d%n", result.getTotalMcr());
        System.out.println("-".repeat(60));
        System.out.printf("  Matched (TP):           %,d%n", result.getMatched().size());
        System.out.printf("  Missing in MCR (FN):    %,d%n", result.getBevOnly().size());
        System.out.printf("  Extra in MCR (FP):      %,d%n", result.getMcrOnly().size());
        System.out.println("-".repeat(60));
        System.out.printf("  Recall:      %6.1f%%%n", result.getRecall());
        System.out.printf("  Precision:   %6.1f%%%n", result.getPrecision());
        System.out.printf("  F1 Score:    %6.1f%%%n", result.getF1());

        if (!result.getMatched().isEmpty()) {
            System.out.printf("  Avg distance: %5.2fm%n", result.getAvgMatchDistance());
            double maxDist = result.getMatched().stream()
                    .mapToDouble(ComparisonResult.MatchPair::getDistanceMeters).max().orElse(0);
            System.out.printf("  Max distance: %5.2fm%n", maxDist);
        }
        System.out.println("=".repeat(60));
        System.out.println();
    }
}
