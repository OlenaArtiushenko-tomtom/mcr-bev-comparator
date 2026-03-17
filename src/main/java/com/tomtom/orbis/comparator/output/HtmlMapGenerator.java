package com.tomtom.orbis.comparator.output;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import com.tomtom.orbis.comparator.model.ComparisonResult.MatchPair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HtmlMapGenerator {

    public void generate(ComparisonResult result, Polygon tileBoundary,
                         double centerLat, double centerLng, Path outputPath) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8"/>
                    <title>MCR vs BEV Comparison</title>
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

        // BEV-only layer (blue)
        html.append("var bevOnly = L.layerGroup();\n");
        for (AddressPoint p : result.getBevOnly()) {
            html.append("L.circleMarker([").append(p.getGeometry().getY()).append(",")
                    .append(p.getGeometry().getX()).append("],{radius:4,color:'#2196F3',")
                    .append("fillColor:'#2196F3',fillOpacity:0.7,weight:1})")
                    .append(".bindPopup('").append(escapeJs("MISSING: " + p.getLabel())).append("')")
                    .append(".addTo(bevOnly);\n");
        }
        html.append("bevOnly.addTo(map);\n");

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

        // Layer control
        html.append(String.format("""
                L.control.layers(null, {
                    'Missing in MCR (%d)': bevOnly,
                    'Extra in MCR (%d)': mcrOnly
                }, {collapsed: false}).addTo(map);
                """, result.getBevOnly().size(), result.getMcrOnly().size()));

        // Info panel
        html.append(String.format("""
                var info = L.control({position:'topright'});
                info.onAdd = function(map) {
                    var div = L.DomUtil.create('div','info-panel');
                    div.innerHTML = '<h3>MCR vs BEV Comparison</h3>'
                        + 'H3: %s<br>'
                        + 'Product: %s<br>'
                        + 'BEV (ground truth): %d<br>'
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
                result.getH3Tile(), result.getProduct(),
                result.getTotalBev(), result.getTotalMcr(),
                result.getRecall(), result.getPrecision(), result.getF1(),
                result.getBevOnly().size(), result.getMcrOnly().size()));

        html.append("</script></body></html>");

        Files.writeString(outputPath, html.toString());
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }
}
