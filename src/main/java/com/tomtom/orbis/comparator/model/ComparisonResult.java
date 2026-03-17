package com.tomtom.orbis.comparator.model;

import java.util.List;

public class ComparisonResult {
    private final List<MatchPair> matched;
    private final List<AddressPoint> groundTruthOnly;  // false negatives
    private final List<AddressPoint> mcrOnly;           // false positives
    private final List<AddressPoint> mcrInTile;         // all MCR points in tile (for no-GT mode)
    private final String h3Tile;
    private final String product;
    private final String licenseZone;
    private final int totalGroundTruth;
    private final int totalMcr;
    private final boolean groundTruthAvailable;

    public ComparisonResult(List<MatchPair> matched, List<AddressPoint> groundTruthOnly,
                            List<AddressPoint> mcrOnly, List<AddressPoint> mcrInTile,
                            String h3Tile, String product, String licenseZone,
                            int totalGroundTruth, int totalMcr, boolean groundTruthAvailable) {
        this.matched = matched;
        this.groundTruthOnly = groundTruthOnly;
        this.mcrOnly = mcrOnly;
        this.mcrInTile = mcrInTile;
        this.h3Tile = h3Tile;
        this.product = product;
        this.licenseZone = licenseZone;
        this.totalGroundTruth = totalGroundTruth;
        this.totalMcr = totalMcr;
        this.groundTruthAvailable = groundTruthAvailable;
    }

    /** Creates a result when no ground truth is available (MCR-only exploration). */
    public static ComparisonResult mcrOnly(List<AddressPoint> mcrInTile,
                                            String h3Tile, String product, String licenseZone) {
        return new ComparisonResult(
                List.of(), List.of(), List.of(), mcrInTile,
                h3Tile, product, licenseZone, 0, mcrInTile.size(), false);
    }

    public List<MatchPair> getMatched() { return matched; }
    public List<AddressPoint> getGroundTruthOnly() { return groundTruthOnly; }
    public List<AddressPoint> getMcrOnly() { return mcrOnly; }
    public List<AddressPoint> getMcrInTile() { return mcrInTile; }
    public String getH3Tile() { return h3Tile; }
    public String getProduct() { return product; }
    public String getLicenseZone() { return licenseZone; }
    public int getTotalGroundTruth() { return totalGroundTruth; }
    public int getTotalMcr() { return totalMcr; }
    public boolean isGroundTruthAvailable() { return groundTruthAvailable; }

    public double getRecall() {
        if (!groundTruthAvailable) return 0;
        int tp = matched.size();
        int fn = groundTruthOnly.size();
        return (tp + fn) > 0 ? (double) tp / (tp + fn) * 100 : 0;
    }

    public double getPrecision() {
        if (!groundTruthAvailable) return 0;
        int tp = matched.size();
        int fp = mcrOnly.size();
        return (tp + fp) > 0 ? (double) tp / (tp + fp) * 100 : 0;
    }

    public double getF1() {
        double p = getPrecision();
        double r = getRecall();
        return (p + r) > 0 ? 2 * p * r / (p + r) : 0;
    }

    public double getAvgMatchDistance() {
        return matched.stream().mapToDouble(MatchPair::getDistanceMeters).average().orElse(0);
    }

    public record MatchPair(AddressPoint groundTruth, AddressPoint mcr, double distanceMeters) {
        public double getDistanceMeters() { return distanceMeters; }
    }
}
