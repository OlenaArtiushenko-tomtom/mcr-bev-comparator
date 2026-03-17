package com.tomtom.orbis.comparator.quality;

import java.util.List;
import java.util.Map;

public record DimensionResult(
        String dimension,
        double score,
        double weight,
        List<Anomaly> anomalies,
        Map<String, Object> details
) {
    public long criticalCount() { return anomalies.stream().filter(a -> a.severity() == Severity.CRITICAL).count(); }
    public long warningCount() { return anomalies.stream().filter(a -> a.severity() == Severity.WARNING).count(); }
    public long infoCount() { return anomalies.stream().filter(a -> a.severity() == Severity.INFO).count(); }
}
