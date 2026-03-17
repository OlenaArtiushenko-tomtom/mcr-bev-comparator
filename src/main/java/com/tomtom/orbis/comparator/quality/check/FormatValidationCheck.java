package com.tomtom.orbis.comparator.quality.check;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.quality.*;

import java.util.*;
import java.util.regex.Pattern;

public class FormatValidationCheck implements QualityCheck {
    @Override public String dimensionName() { return "format"; }
    @Override public double weight() { return 15; }

    // Valid housenumber patterns
    private static final Pattern HN_VALID = Pattern.compile(
            "^\\d+([a-zA-Z])?$"                    // 5, 5A
            + "|^\\d+[-/]\\d+([a-zA-Z])?$"         // 12-14, 8/1, 8/1A
            + "|^\\d+[-/]\\d+/\\d+$"               // 78-80/2
            + "|^\\d+[a-zA-Z]?/\\d+$"              // 6A/1
    );

    // Postcode patterns by license zone
    private static final Map<String, Pattern> POSTCODE_PATTERNS = Map.of(
            "AUT", Pattern.compile("^\\d{4}$"),
            "DEU", Pattern.compile("^\\d{5}$"),
            "FRA", Pattern.compile("^\\d{5}$"),
            "NLD", Pattern.compile("^\\d{4}\\s?[A-Z]{2}$"),
            "UKR", Pattern.compile("^\\d{5}$"),
            "ITA", Pattern.compile("^\\d{5}$"),
            "CHE", Pattern.compile("^\\d{4}$")
    );

    // Encoding issue indicators
    private static final Pattern ENCODING_ISSUES = Pattern.compile(
            "\\uFFFD"                               // replacement character
            + "|Ã¤|Ã¶|Ã¼|Ã\u009F"                  // common UTF-8 mojibake
            + "|â€[\\x80-\\xbf]"                    // smart quote mojibake
    );

    @Override
    public DimensionResult run(List<FlaggedPoint> points, CheckContext ctx) {
        int total = points.size();
        if (total == 0) return new DimensionResult(dimensionName(), 100, weight(), List.of(), Map.of());

        int invalidHn = 0, invalidPc = 0, encodingIssues = 0, checkedHn = 0, checkedPc = 0;
        List<Anomaly> anomalies = new ArrayList<>();
        String zone = ctx.h3Tile() != null ? "" : "";
        Pattern pcPattern = null;

        // Try to detect license zone from the data
        for (FlaggedPoint fp : points) {
            String lz = fp.getPoint().getLicenseZone();
            if (lz != null && POSTCODE_PATTERNS.containsKey(lz)) {
                pcPattern = POSTCODE_PATTERNS.get(lz);
                break;
            }
        }

        for (FlaggedPoint fp : points) {
            AddressPoint p = fp.getPoint();

            // Housenumber format
            String hn = p.getHouseNumber();
            if (hn != null && !hn.isBlank()) {
                checkedHn++;
                if (!HN_VALID.matcher(hn.trim()).matches()) {
                    invalidHn++;
                    fp.addFlag(QualityFlag.INVALID_HOUSENUMBER_FORMAT);
                }
            }

            // Postcode format
            String pc = p.getPostcode();
            if (pc != null && !pc.isBlank() && pcPattern != null) {
                checkedPc++;
                if (!pcPattern.matcher(pc.trim()).matches()) {
                    invalidPc++;
                    fp.addFlag(QualityFlag.INVALID_POSTCODE_FORMAT);
                }
            }

            // Encoding issues in any text field
            if (hasEncodingIssue(p.getStreet()) || hasEncodingIssue(p.getCity())
                    || hasEncodingIssue(p.getHouseNumber())) {
                encodingIssues++;
                fp.addFlag(QualityFlag.ENCODING_ISSUE);
            }
        }

        if (invalidHn > 0) {
            anomalies.add(new Anomaly(invalidHn > total * 0.1 ? Severity.WARNING : Severity.INFO,
                    dimensionName(), "INVALID_HOUSENUMBER_FORMAT",
                    String.format("%d housenumbers have unusual format (%.1f%%)", invalidHn, pct(invalidHn, checkedHn)),
                    null, null, null));
        }
        if (invalidPc > 0) {
            anomalies.add(new Anomaly(invalidPc > total * 0.1 ? Severity.WARNING : Severity.INFO,
                    dimensionName(), "INVALID_POSTCODE_FORMAT",
                    String.format("%d postcodes have invalid format (%.1f%%)", invalidPc, pct(invalidPc, checkedPc)),
                    null, null, null));
        }
        if (encodingIssues > 0) {
            anomalies.add(new Anomaly(Severity.WARNING, dimensionName(), "ENCODING_ISSUE",
                    String.format("%d records have encoding issues", encodingIssues),
                    null, null, null));
        }

        int totalIssues = invalidHn + invalidPc + encodingIssues;
        double score = Math.max(0, (1.0 - (double) totalIssues / total) * 100);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invalid_housenumber_count", invalidHn);
        details.put("invalid_housenumber_pct", round(pct(invalidHn, checkedHn)));
        details.put("invalid_postcode_count", invalidPc);
        details.put("invalid_postcode_pct", round(pct(invalidPc, checkedPc)));
        details.put("encoding_issue_count", encodingIssues);

        return new DimensionResult(dimensionName(), score, weight(), anomalies, details);
    }

    private boolean hasEncodingIssue(String s) {
        return s != null && ENCODING_ISSUES.matcher(s).find();
    }

    private double pct(int count, int total) { return total > 0 ? (double) count / total * 100 : 0; }
    private double round(double v) { return Math.round(v * 10) / 10.0; }
}
