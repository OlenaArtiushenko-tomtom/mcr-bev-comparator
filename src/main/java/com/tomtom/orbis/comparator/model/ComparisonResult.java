package com.tomtom.orbis.comparator.model;

import java.util.List;

public class ComparisonResult {
    private final List<MatchPair> matched;
    private final List<AddressPoint> bevOnly;   // false negatives
    private final List<AddressPoint> mcrOnly;   // false positives
    private final String h3Tile;
    private final String product;
    private final String licenseZone;
    private final int totalBev;
    private final int totalMcr;

    public ComparisonResult(List<MatchPair> matched, List<AddressPoint> bevOnly,
                            List<AddressPoint> mcrOnly, String h3Tile,
                            String product, String licenseZone,
                            int totalBev, int totalMcr) {
        this.matched = matched;
        this.bevOnly = bevOnly;
        this.mcrOnly = mcrOnly;
        this.h3Tile = h3Tile;
        this.product = product;
        this.licenseZone = licenseZone;
        this.totalBev = totalBev;
        this.totalMcr = totalMcr;
    }

    public List<MatchPair> getMatched() { return matched; }
    public List<AddressPoint> getBevOnly() { return bevOnly; }
    public List<AddressPoint> getMcrOnly() { return mcrOnly; }
    public String getH3Tile() { return h3Tile; }
    public String getProduct() { return product; }
    public String getLicenseZone() { return licenseZone; }
    public int getTotalBev() { return totalBev; }
    public int getTotalMcr() { return totalMcr; }

    public double getRecall() {
        int tp = matched.size();
        int fn = bevOnly.size();
        return (tp + fn) > 0 ? (double) tp / (tp + fn) * 100 : 0;
    }

    public double getPrecision() {
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

    public record MatchPair(AddressPoint bev, AddressPoint mcr, double distanceMeters) {
        public double getDistanceMeters() { return distanceMeters; }
    }
}
