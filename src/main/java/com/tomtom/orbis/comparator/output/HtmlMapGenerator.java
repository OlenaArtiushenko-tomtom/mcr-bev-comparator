package com.tomtom.orbis.comparator.output;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HtmlMapGenerator {

    public void generate(ComparisonResult result, Polygon tileBoundary,
                         double centerLat, double centerLng, Path outputPath) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8"/>
                    <title>MCR Address Point Comparison</title>
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                    <style>
                        body { margin: 0; padding: 0; }
                        #map { width: 100%%; height: 100vh; }
                        .info-panel {
                            background: white; padding: 12px 16px; border-radius: 8px;
                            box-shadow: 0 2px 8px rgba(0,0,0,0.25); font-family: monospace;
                            font-size: 13px; line-height: 1.6;
                        }
                        .info-panel h3 { margin: 0 0 8px 0; font-size: 14px; }
                    </style>
                </head>
                <body>
                <div id="map"></div>
                <script>
                """);

        html.append("var map = L.map('map').setView([")
                .append(centerLat).append(",").append(centerLng).append("], 14);\n");
        html.append("""
                L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
                    attribution: '&copy; OSM &amp; CartoDB', maxZoom: 19
                }).addTo(map);
                """);

        // H3 tile boundary
        html.append("var tileBoundary = L.polygon([");
        Coordinate[] coords = tileBoundary.getCoordinates();
        for (int i = 0; i < coords.length - 1; i++) {
            if (i > 0) html.append(",");
            html.append("[").append(coords[i].y).append(",").append(coords[i].x).append("]");
        }
        html.append("], {color:'black',weight:3,fill:true,fillColor:'grey',fillOpacity:0.05}).addTo(map);\n");

        if (result.isGroundTruthAvailable()) {
            // Comparison mode: show differences only
            generateComparisonLayers(html, result);
        } else {
            // MCR-only mode: show all MCR address points
            generateMcrOnlyLayer(html, result);
        }

        // Info panel
        generateInfoPanel(html, result);

        html.append("</script></body></html>");
        Files.writeString(outputPath, html.toString());
    }

    private void generateComparisonLayers(StringBuilder html, ComparisonResult result) {
        // Ground-truth-only layer (blue)
        html.append("var gtOnly = L.layerGroup();\n");
        for (AddressPoint p : result.getGroundTruthOnly()) {
            html.append("L.circleMarker([").append(p.getGeometry().getY()).append(",")
                    .append(p.getGeometry().getX()).append("],{radius:4,color:'#2196F3',")
                    .append("fillColor:'#2196F3',fillOpacity:0.7,weight:1})")
                    .append(".bindPopup('").append(escapeJs("MISSING: " + p.getLabel())).append("')")
                    .append(".addTo(gtOnly);\n");
        }
        html.append("gtOnly.addTo(map);\n");

        // MCR-only layer (red)
        html.append("var mcrOnly = L.layerGroup();\n");
        for (AddressPoint p : result.getMcrOnly()) {
            html.append("L.circleMarker([").append(p.getGeometry().getY()).append(",")
                    .append(p.getGeometry().getX()).append("],{radius:4,color:'#F44336',")
                    .append("fillColor:'#F44336',fillOpacity:0.7,weight:1})")
                    .append(".bindPopup('").append(escapeJs("EXTRA: " + p.getLabel())).append("')")
                    .append(".addTo(mcrOnly);\n");
        }
        html.append("mcrOnly.addTo(map);\n");

        html.append(String.format("""
                L.control.layers(null, {
                    'Missing in MCR (%d)': gtOnly,
                    'Extra in MCR (%d)': mcrOnly
                }, {collapsed: false}).addTo(map);
                """, result.getGroundTruthOnly().size(), result.getMcrOnly().size()));
    }

    private void generateMcrOnlyLayer(StringBuilder html, ComparisonResult result) {
        html.append("var mcrAll = L.layerGroup();\n");
        for (AddressPoint p : result.getMcrInTile()) {
            html.append("L.circleMarker([").append(p.getGeometry().getY()).append(",")
                    .append(p.getGeometry().getX()).append("],{radius:3,color:'#4CAF50',")
                    .append("fillColor:'#4CAF50',fillOpacity:0.6,weight:1})")
                    .append(".bindPopup('").append(escapeJs("MCR: " + p.getLabel())).append("')")
                    .append(".addTo(mcrAll);\n");
        }
        html.append("mcrAll.addTo(map);\n");

        html.append(String.format("""
                L.control.layers(null, {
                    'MCR Address Points (%d)': mcrAll
                }, {collapsed: false}).addTo(map);
                """, result.getMcrInTile().size()));
    }

    private void generateInfoPanel(StringBuilder html, ComparisonResult result) {
        if (result.isGroundTruthAvailable()) {
            html.append(String.format("""
                    var info = L.control({position:'topright'});
                    info.onAdd = function(map) {
                        var div = L.DomUtil.create('div','info-panel');
                        div.innerHTML = '<h3>MCR vs Ground Truth</h3>'
                            + 'H3: %s<br>'
                            + 'Product: %s<br>'
                            + 'License Zone: %s<br>'
                            + 'Ground truth: %d<br>'
                            + 'MCR in tile: %d<br>'
                            + '<b>Recall: %.1f%%</b><br>'
                            + '<b>Precision: %.1f%%</b><br>'
                            + '<b>F1: %.1f%%</b><br>'
                            + 'Missing: <span style="color:#2196F3">%d</span> | '
                            + 'Extra: <span style="color:#F44336">%d</span>';
                        return div;
                    };
                    info.addTo(map);
                    """,
                    result.getH3Tile(), result.getProduct(), result.getLicenseZone(),
                    result.getTotalGroundTruth(), result.getTotalMcr(),
                    result.getRecall(), result.getPrecision(), result.getF1(),
                    result.getGroundTruthOnly().size(), result.getMcrOnly().size()));
        } else {
            html.append(String.format("""
                    var info = L.control({position:'topright'});
                    info.onAdd = function(map) {
                        var div = L.DomUtil.create('div','info-panel');
                        div.innerHTML = '<h3>MCR Address Points</h3>'
                            + 'H3: %s<br>'
                            + 'Product: %s<br>'
                            + 'License Zone: %s<br>'
                            + 'MCR address points: %d<br>'
                            + '<i>Ground truth not available</i>';
                        return div;
                    };
                    info.addTo(map);
                    """,
                    result.getH3Tile(), result.getProduct(), result.getLicenseZone(),
                    result.getTotalMcr()));
        }
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }
}
