package com.tomtom.orbis.comparator.quality.check;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.quality.*;

import java.util.*;
import java.util.regex.Pattern;

public class StructuralCheck implements QualityCheck {
    @Override public String dimensionName() { return "structural"; }
    @Override public double weight() { return 10; }

    private static final Pattern SUSPICIOUS_NAME = Pattern.compile(
            "^\\d+$"                    // pure numeric street name
            + "|^.{80,}$"              // excessively long
            + "|[<>{}\\[\\]|\\\\]"     // suspicious characters
    );

    @Override
    public DimensionResult run(List<FlaggedPoint> points, CheckContext ctx) {
        int total = points.size();
        if (total == 0) return new DimensionResult(dimensionName(), 100, weight(), List.of(), Map.of());

        List<Anomaly> anomalies = new ArrayList<>();

        // Street analysis
        Map<String, List<FlaggedPoint>> streetGroups = new HashMap<>();
        for (FlaggedPoint fp : points) {
            String street = fp.getPoint().getStreet();
            if (street != null && !street.isBlank()) {
                streetGroups.computeIfAbsent(street.trim(), k -> new ArrayList<>()).add(fp);
            }
        }

        // Single-address streets
        int singleAddrStreets = 0;
        for (var entry : streetGroups.entrySet()) {
            if (entry.getValue().size() == 1) {
                singleAddrStreets++;
                entry.getValue().get(0).addFlag(QualityFlag.SINGLE_ADDRESS_STREET);
            }
        }

        int totalStreets = streetGroups.size();
        double singlePct = totalStreets > 0 ? (double) singleAddrStreets / totalStreets * 100 : 0;
        if (singlePct > 20) {
            anomalies.add(new Anomaly(Severity.WARNING, dimensionName(), "SINGLE_ADDRESS_STREETS",
                    String.format("%.1f%% of streets (%d/%d) have only 1 address point",
                            singlePct, singleAddrStreets, totalStreets),
                    null, null, null));
        } else if (singleAddrStreets > 0) {
            anomalies.add(new Anomaly(Severity.INFO, dimensionName(), "SINGLE_ADDRESS_STREETS",
                    String.format("%d streets have only 1 address point (%.1f%%)", singleAddrStreets, singlePct),
                    null, null, null));
        }

        // Unusual street names
        int unusualNames = 0;
        for (FlaggedPoint fp : points) {
            String street = fp.getPoint().getStreet();
            if (street != null && SUSPICIOUS_NAME.matcher(street).find()) {
                fp.addFlag(QualityFlag.UNUSUAL_STREET_NAME);
                unusualNames++;
            }
        }
        if (unusualNames > 0) {
            anomalies.add(new Anomaly(Severity.INFO, dimensionName(), "UNUSUAL_STREET_NAME",
                    String.format("%d records have unusual street names", unusualNames), null, null, null));
        }

        // Postcode distribution skew
        Map<String, Integer> pcCounts = new HashMap<>();
        for (FlaggedPoint fp : points) {
            String pc = fp.getPoint().getPostcode();
            if (pc != null && !pc.isBlank()) {
                pcCounts.merge(pc.trim(), 1, Integer::sum);
            }
        }
        if (!pcCounts.isEmpty()) {
            int maxCount = pcCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int totalWithPc = pcCounts.values().stream().mapToInt(Integer::intValue).sum();
            double dominantPct = (double) maxCount / totalWithPc * 100;
            if (dominantPct > 80 && pcCounts.size() > 1) {
                String dominantPc = pcCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("?");
                anomalies.add(new Anomaly(Severity.WARNING, dimensionName(), "POSTCODE_SKEW",
                        String.format("Postcode %s dominates with %.1f%% of records", dominantPc, dominantPct),
                        null, null, null));
            }
        }

        double peculiarityRate = (double) (singleAddrStreets + unusualNames) / Math.max(1, total);
        double score = Math.max(0, (1.0 - peculiarityRate) * 100);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("total_streets", totalStreets);
        details.put("single_address_streets", singleAddrStreets);
        details.put("unusual_street_names", unusualNames);
        details.put("unique_postcodes", pcCounts.size());

        return new DimensionResult(dimensionName(), score, weight(), anomalies, details);
    }
}
