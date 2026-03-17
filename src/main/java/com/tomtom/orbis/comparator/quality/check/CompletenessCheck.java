package com.tomtom.orbis.comparator.quality.check;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.quality.*;

import java.util.*;

public class CompletenessCheck implements QualityCheck {
    @Override public String dimensionName() { return "completeness"; }
    @Override public double weight() { return 25; }

    @Override
    public DimensionResult run(List<FlaggedPoint> points, CheckContext ctx) {
        int total = points.size();
        if (total == 0) return new DimensionResult(dimensionName(), 100, weight(), List.of(), Map.of());

        int missingStreet = 0, missingHn = 0, missingPc = 0, missingCity = 0;
        List<Anomaly> anomalies = new ArrayList<>();

        for (FlaggedPoint fp : points) {
            AddressPoint p = fp.getPoint();
            if (isBlank(p.getStreet())) { missingStreet++; fp.addFlag(QualityFlag.MISSING_STREET); }
            if (isBlank(p.getHouseNumber())) { missingHn++; fp.addFlag(QualityFlag.MISSING_HOUSENUMBER); }
            if (isBlank(p.getPostcode())) { missingPc++; fp.addFlag(QualityFlag.MISSING_POSTCODE); }
            if (isBlank(p.getCity())) { missingCity++; fp.addFlag(QualityFlag.MISSING_CITY); }
        }

        double streetPct = pct(missingStreet, total);
        double hnPct = pct(missingHn, total);
        double pcPct = pct(missingPc, total);
        double cityPct = pct(missingCity, total);

        addFieldAnomaly(anomalies, "street", streetPct, missingStreet);
        addFieldAnomaly(anomalies, "housenumber", hnPct, missingHn);
        addFieldAnomaly(anomalies, "postcode", pcPct, missingPc);
        addFieldAnomaly(anomalies, "city", cityPct, missingCity);

        double score = (fieldScore(streetPct) + fieldScore(hnPct) + fieldScore(pcPct) + fieldScore(cityPct)) / 4.0;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("total_records", total);
        details.put("street_null_pct", round(streetPct));
        details.put("housenumber_null_pct", round(hnPct));
        details.put("postcode_null_pct", round(pcPct));
        details.put("city_null_pct", round(cityPct));

        return new DimensionResult(dimensionName(), score, weight(), anomalies, details);
    }

    private void addFieldAnomaly(List<Anomaly> anomalies, String field, double pct, int count) {
        if (pct > 50) {
            anomalies.add(new Anomaly(Severity.CRITICAL, dimensionName(),
                    "MISSING_" + field.toUpperCase(),
                    String.format("%s is missing in %.1f%% of records (%d/%d)", field, pct, count, count),
                    null, null, null));
        } else if (pct > 10) {
            anomalies.add(new Anomaly(Severity.WARNING, dimensionName(),
                    "MISSING_" + field.toUpperCase(),
                    String.format("%s is missing in %.1f%% of records (%d)", field, pct, count),
                    null, null, null));
        } else if (pct > 0) {
            anomalies.add(new Anomaly(Severity.INFO, dimensionName(),
                    "MISSING_" + field.toUpperCase(),
                    String.format("%s is missing in %.1f%% of records (%d)", field, pct, count),
                    null, null, null));
        }
    }

    private double fieldScore(double nullPct) { return Math.max(0, 100 - nullPct); }
    private double pct(int count, int total) { return total > 0 ? (double) count / total * 100 : 0; }
    private double round(double v) { return Math.round(v * 10) / 10.0; }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
