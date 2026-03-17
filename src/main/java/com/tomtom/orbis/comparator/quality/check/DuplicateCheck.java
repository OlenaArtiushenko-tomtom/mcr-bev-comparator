package com.tomtom.orbis.comparator.quality.check;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.quality.*;

import java.util.*;

public class DuplicateCheck implements QualityCheck {
    @Override public String dimensionName() { return "duplicates"; }
    @Override public double weight() { return 15; }

    @Override
    public DimensionResult run(List<FlaggedPoint> points, CheckContext ctx) {
        int total = points.size();
        if (total == 0) return new DimensionResult(dimensionName(), 100, weight(), List.of(), Map.of());

        List<Anomaly> anomalies = new ArrayList<>();

        // Exact coordinate duplicates (rounded to 7 decimal places)
        Map<String, List<FlaggedPoint>> coordGroups = new HashMap<>();
        for (FlaggedPoint fp : points) {
            double lon = Math.round(fp.getPoint().getGeometry().getX() * 1e7) / 1e7;
            double lat = Math.round(fp.getPoint().getGeometry().getY() * 1e7) / 1e7;
            String key = lon + "," + lat;
            coordGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(fp);
        }

        int coordDuplicates = 0;
        for (var entry : coordGroups.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (FlaggedPoint fp : entry.getValue()) {
                    fp.addFlag(QualityFlag.DUPLICATE_COORDINATE);
                    coordDuplicates++;
                }
            }
        }

        // Same address text at different coordinates
        Map<String, List<FlaggedPoint>> addrGroups = new HashMap<>();
        for (FlaggedPoint fp : points) {
            AddressPoint p = fp.getPoint();
            String key = normalize(p.getStreet()) + "|" + normalize(p.getHouseNumber())
                    + "|" + normalize(p.getPostcode()) + "|" + normalize(p.getCity());
            if (!key.equals("|||")) {
                addrGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(fp);
            }
        }

        int textDuplicates = 0;
        for (var entry : addrGroups.entrySet()) {
            List<FlaggedPoint> group = entry.getValue();
            if (group.size() > 1 && hasDistantPoints(group, 50)) {
                for (FlaggedPoint fp : group) {
                    fp.addFlag(QualityFlag.DUPLICATE_ADDRESS_TEXT);
                    textDuplicates++;
                }
            }
        }

        if (coordDuplicates > 0) {
            int groups = (int) coordGroups.values().stream().filter(g -> g.size() > 1).count();
            anomalies.add(new Anomaly(coordDuplicates > total * 0.05 ? Severity.WARNING : Severity.INFO,
                    dimensionName(), "DUPLICATE_COORDINATE",
                    String.format("%d records share exact coordinates (%d groups)", coordDuplicates, groups),
                    null, null, null));
        }
        if (textDuplicates > 0) {
            anomalies.add(new Anomaly(Severity.WARNING, dimensionName(), "DUPLICATE_ADDRESS_TEXT",
                    String.format("%d records have same address text at different locations (>50m apart)", textDuplicates),
                    null, null, null));
        }

        double duplicateRate = (double) (coordDuplicates + textDuplicates) / total;
        double score = Math.max(0, (1.0 - duplicateRate) * 100);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("coordinate_duplicates", coordDuplicates);
        details.put("address_text_duplicates", textDuplicates);
        details.put("duplicate_coordinate_groups", (int) coordGroups.values().stream().filter(g -> g.size() > 1).count());

        return new DimensionResult(dimensionName(), score, weight(), anomalies, details);
    }

    private boolean hasDistantPoints(List<FlaggedPoint> group, double thresholdMeters) {
        // Approximate: 1 degree ~ 111km at equator, good enough for >50m check
        double thresholdDeg = thresholdMeters / 111000.0;
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                double dx = group.get(i).getPoint().getGeometry().getX() - group.get(j).getPoint().getGeometry().getX();
                double dy = group.get(i).getPoint().getGeometry().getY() - group.get(j).getPoint().getGeometry().getY();
                if (Math.sqrt(dx * dx + dy * dy) > thresholdDeg) return true;
            }
        }
        return false;
    }

    private String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(); }
}
