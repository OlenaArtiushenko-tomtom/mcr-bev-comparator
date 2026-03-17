package com.tomtom.orbis.comparator.spatial;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import com.tomtom.orbis.comparator.model.ComparisonResult.MatchPair;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.*;

public class SpatialMatcher {
    private static final double BUFFER_METERS = 5.0;
    private static final String METRIC_CRS = "EPSG:31287"; // MGI Austria Lambert

    public ComparisonResult match(List<AddressPoint> bevPoints, List<AddressPoint> mcrPoints,
                                  Polygon tileBoundary, String h3Tile,
                                  String product, String licenseZone) throws Exception {
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true);
        CoordinateReferenceSystem metric = CRS.decode(METRIC_CRS, true);
        MathTransform toMetric = CRS.findMathTransform(wgs84, metric, true);

        // Reproject all points to metric
        for (AddressPoint p : bevPoints) {
            p.setMetricGeometry((Point) JTS.transform(p.getGeometry(), toMetric));
        }
        for (AddressPoint p : mcrPoints) {
            p.setMetricGeometry((Point) JTS.transform(p.getGeometry(), toMetric));
        }

        // Build spatial index on MCR points
        STRtree mcrIndex = new STRtree();
        for (AddressPoint mcr : mcrPoints) {
            mcrIndex.insert(mcr.getMetricGeometry().getEnvelopeInternal(), mcr);
        }
        mcrIndex.build();

        // Match BEV -> MCR
        List<MatchPair> matched = new ArrayList<>();
        List<AddressPoint> bevOnly = new ArrayList<>();
        Set<String> matchedMcrIds = new HashSet<>();

        for (AddressPoint bev : bevPoints) {
            Point bevMetric = bev.getMetricGeometry();
            Geometry buffer = bevMetric.buffer(BUFFER_METERS);

            @SuppressWarnings("unchecked")
            List<AddressPoint> candidates = mcrIndex.query(buffer.getEnvelopeInternal());

            AddressPoint bestMatch = null;
            double bestDist = Double.MAX_VALUE;

            for (AddressPoint mcr : candidates) {
                double dist = bevMetric.distance(mcr.getMetricGeometry());
                if (dist <= BUFFER_METERS && dist < bestDist) {
                    bestMatch = mcr;
                    bestDist = dist;
                }
            }

            if (bestMatch != null) {
                matched.add(new MatchPair(bev, bestMatch, bestDist));
                matchedMcrIds.add(bestMatch.getId());
            } else {
                bevOnly.add(bev);
            }
        }

        // Find MCR-only: MCR points in tile not matched to any BEV
        List<AddressPoint> mcrOnly = new ArrayList<>();
        for (AddressPoint mcr : mcrPoints) {
            if (tileBoundary.contains(mcr.getGeometry()) && !matchedMcrIds.contains(mcr.getId())) {
                mcrOnly.add(mcr);
            }
        }

        // Count MCR points in tile for stats
        int mcrInTile = (int) mcrPoints.stream()
                .filter(p -> tileBoundary.contains(p.getGeometry()))
                .count();

        return new ComparisonResult(matched, bevOnly, mcrOnly, h3Tile,
                product, licenseZone, bevPoints.size(), mcrInTile);
    }
}
