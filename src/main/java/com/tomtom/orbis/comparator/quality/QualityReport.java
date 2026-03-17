package com.tomtom.orbis.comparator.quality;

import java.util.Comparator;
import java.util.List;

public class QualityReport {
    private final double overallScore;
    private final List<DimensionResult> dimensions;
    private final List<Anomaly> allAnomalies;
    private final List<FlaggedPoint> flaggedPoints;
    private final String h3Tile;
    private final String sourceName;
    private final int totalRecords;

    public QualityReport(double overallScore, List<DimensionResult> dimensions,
                         List<FlaggedPoint> flaggedPoints, String h3Tile, String sourceName) {
        this.overallScore = overallScore;
        this.dimensions = dimensions;
        this.flaggedPoints = flaggedPoints;
        this.h3Tile = h3Tile;
        this.sourceName = sourceName;
        this.totalRecords = flaggedPoints.size();
        this.allAnomalies = dimensions.stream()
                .flatMap(d -> d.anomalies().stream())
                .sorted(Comparator.comparing(Anomaly::severity))
                .toList();
    }

    public double getOverallScore() { return overallScore; }
    public List<DimensionResult> getDimensions() { return dimensions; }
    public List<Anomaly> getAllAnomalies() { return allAnomalies; }
    public List<FlaggedPoint> getFlaggedPoints() { return flaggedPoints; }
    public String getH3Tile() { return h3Tile; }
    public String getSourceName() { return sourceName; }
    public int getTotalRecords() { return totalRecords; }

    public long criticalCount() { return allAnomalies.stream().filter(a -> a.severity() == Severity.CRITICAL).count(); }
    public long warningCount() { return allAnomalies.stream().filter(a -> a.severity() == Severity.WARNING).count(); }
    public long infoCount() { return allAnomalies.stream().filter(a -> a.severity() == Severity.INFO).count(); }

    public long cleanRecordCount() {
        return flaggedPoints.stream().filter(fp -> fp.getFlags().isEmpty()).count();
    }

    public long flaggedRecordCount() {
        return flaggedPoints.stream().filter(fp -> !fp.getFlags().isEmpty()).count();
    }

    public String getScoreGrade() {
        if (overallScore >= 95) return "A";
        if (overallScore >= 85) return "B";
        if (overallScore >= 70) return "C";
        if (overallScore >= 50) return "D";
        return "F";
    }
}
