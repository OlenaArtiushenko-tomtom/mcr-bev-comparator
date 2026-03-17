package com.tomtom.orbis.comparator.output;

import com.tomtom.orbis.comparator.quality.*;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HtmlDashboardGenerator {

    public void generate(QualityReport report, Polygon tileBoundary,
                         double centerLat, double centerLng, Path outputPath) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("""
                <!DOCTYPE html><html><head><meta charset="utf-8"/>
                <title>Source Data Quality Report</title>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
                <style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f5f5;color:#333}
                .header{background:#1a237e;color:white;padding:20px 30px;display:flex;align-items:center;gap:30px}
                .score-circle{width:80px;height:80px;border-radius:50%%;display:flex;align-items:center;justify-content:center;font-size:28px;font-weight:bold}
                .score-A,.score-B{background:#4caf50}.score-C{background:#ff9800}.score-D,.score-F{background:#f44336}
                .header h1{font-size:22px;font-weight:400}
                .header .meta{font-size:13px;opacity:0.8}
                .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px;padding:20px 30px}
                .card{background:white;border-radius:8px;padding:16px;box-shadow:0 1px 3px rgba(0,0,0,0.12)}
                .card h3{font-size:14px;color:#666;margin-bottom:8px}
                .card .score{font-size:32px;font-weight:bold}
                .card .bar{height:6px;background:#e0e0e0;border-radius:3px;margin-top:8px}
                .card .bar-fill{height:100%%;border-radius:3px}
                .bar-good{background:#4caf50}.bar-ok{background:#ff9800}.bar-bad{background:#f44336}
                .section{padding:20px 30px}
                .section h2{font-size:18px;margin-bottom:12px;color:#1a237e}
                #map{height:400px;border-radius:8px;margin-bottom:16px}
                table{width:100%%;border-collapse:collapse;background:white;border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.12)}
                th,td{padding:10px 14px;text-align:left;border-bottom:1px solid #eee;font-size:13px}
                th{background:#f5f5f5;font-weight:600}
                .sev-CRITICAL{color:#f44336;font-weight:bold}.sev-WARNING{color:#ff9800}.sev-INFO{color:#9e9e9e}
                .summary-row{display:flex;gap:20px;margin-bottom:16px;flex-wrap:wrap}
                .summary-item{background:white;border-radius:8px;padding:12px 20px;box-shadow:0 1px 3px rgba(0,0,0,0.12)}
                .summary-item .label{font-size:12px;color:#666}.summary-item .value{font-size:20px;font-weight:bold}
                </style></head><body>
                """);

        // Header with overall score
        String grade = report.getScoreGrade();
        h.append(String.format("""
                <div class="header">
                <div class="score-circle score-%s">%s</div>
                <div><h1>Source Data Quality Report</h1>
                <div class="meta">H3: %s | Source: %s | Records: %,d | Score: %.1f/100</div></div></div>
                """, grade, grade, report.getH3Tile(), report.getSourceName(),
                report.getTotalRecords(), report.getOverallScore()));

        // Summary row
        h.append(String.format("""
                <div class="section"><div class="summary-row">
                <div class="summary-item"><div class="label">Clean Records</div><div class="value" style="color:#4caf50">%,d</div></div>
                <div class="summary-item"><div class="label">Flagged Records</div><div class="value" style="color:#ff9800">%,d</div></div>
                <div class="summary-item"><div class="label">Critical</div><div class="value" style="color:#f44336">%d</div></div>
                <div class="summary-item"><div class="label">Warnings</div><div class="value" style="color:#ff9800">%d</div></div>
                <div class="summary-item"><div class="label">Info</div><div class="value" style="color:#9e9e9e">%d</div></div>
                </div></div>
                """, report.cleanRecordCount(), report.flaggedRecordCount(),
                report.criticalCount(), report.warningCount(), report.infoCount()));

        // Dimension cards
        h.append("<div class=\"grid\">");
        for (DimensionResult dim : report.getDimensions()) {
            if (dim.weight() <= 0) continue;
            String barClass = dim.score() >= 85 ? "bar-good" : dim.score() >= 70 ? "bar-ok" : "bar-bad";
            h.append(String.format("""
                    <div class="card"><h3>%s</h3><div class="score">%.0f</div>
                    <div class="bar"><div class="bar-fill %s" style="width:%.0f%%"></div></div>
                    <div style="margin-top:8px;font-size:12px;color:#999">%d issues</div></div>
                    """, capitalize(dim.dimension()), dim.score(), barClass, dim.score(),
                    dim.anomalies().size()));
        }
        h.append("</div>");

        // Map
        h.append("<div class=\"section\"><h2>Spatial View</h2><div id=\"map\"></div></div>");

        // Anomaly table
        h.append("""
                <div class="section"><h2>Anomalies</h2><table>
                <tr><th>Severity</th><th>Dimension</th><th>Code</th><th>Message</th></tr>
                """);
        for (Anomaly a : report.getAllAnomalies()) {
            h.append(String.format("<tr><td class=\"sev-%s\">%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\n",
                    a.severity(), a.severity(), a.dimension(), a.code(), escapeHtml(a.message())));
        }
        h.append("</table></div>");

        // Dimension details
        h.append("<div class=\"section\"><h2>Dimension Details</h2>");
        for (DimensionResult dim : report.getDimensions()) {
            if (dim.details().isEmpty()) continue;
            h.append(String.format("<div class=\"card\" style=\"margin-bottom:12px\"><h3>%s</h3><table>",
                    capitalize(dim.dimension())));
            for (var entry : dim.details().entrySet()) {
                h.append(String.format("<tr><td>%s</td><td><b>%s</b></td></tr>",
                        entry.getKey().replace("_", " "), entry.getValue()));
            }
            h.append("</table></div>");
        }
        h.append("</div>");

        // Leaflet map script
        h.append("<script>\n");
        h.append(String.format("var map=L.map('map').setView([%f,%f],14);\n", centerLat, centerLng));
        h.append("""
                L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',{maxZoom:19}).addTo(map);
                """);

        // Tile boundary
        if (tileBoundary != null) {
            h.append("L.polygon([");
            Coordinate[] coords = tileBoundary.getCoordinates();
            for (int i = 0; i < coords.length - 1; i++) {
                if (i > 0) h.append(",");
                h.append("[").append(coords[i].y).append(",").append(coords[i].x).append("]");
            }
            h.append("],{color:'black',weight:2,fill:false}).addTo(map);\n");
        }

        // Points colored by severity
        h.append("var clean=L.layerGroup(),flagged=L.layerGroup();\n");
        int cleanCount = 0, flagCount = 0;
        for (FlaggedPoint fp : report.getFlaggedPoints()) {
            double lat = fp.getPoint().getGeometry().getY();
            double lon = fp.getPoint().getGeometry().getX();
            String label = escapeJs(fp.getPoint().getLabel());
            if (fp.getFlags().isEmpty()) {
                cleanCount++;
                h.append(String.format(
                        "L.circleMarker([%f,%f],{radius:2,color:'#4caf50',fillOpacity:0.4,weight:0,fillColor:'#4caf50'})"
                                + ".bindPopup('%s').addTo(clean);\n", lat, lon, label));
            } else {
                flagCount++;
                Severity sev = fp.getWorstSeverity();
                String color = sev == Severity.CRITICAL ? "#f44336" : sev == Severity.WARNING ? "#ff9800" : "#2196f3";
                h.append(String.format(
                        "L.circleMarker([%f,%f],{radius:3,color:'%s',fillOpacity:0.7,weight:1,fillColor:'%s'})"
                                + ".bindPopup('%s<br>Flags: %s').addTo(flagged);\n",
                        lat, lon, color, color, label, escapeJs(fp.getFlagsString())));
            }
        }
        h.append("clean.addTo(map);flagged.addTo(map);\n");
        h.append(String.format(
                "L.control.layers(null,{'Clean (%d)':clean,'Flagged (%d)':flagged},{collapsed:false}).addTo(map);\n",
                cleanCount, flagCount));

        h.append("</script></body></html>");

        Files.writeString(outputPath, h.toString());
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("_", " ");
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeJs(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }
}
