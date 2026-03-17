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

    /**
     * Match ground truth points against MCR points using a 5m buffer.
     * @param metricCrs EPSG code for metric CRS (e.g. "EPSG:31287" for Austria, "EPSG:32637" for Ukraine)
     */
    public ComparisonResult match(List<AddressPoint> gtPoints, List<AddressPoint> mcrPoints,
                                  Polygon tileBoundary, String h3Tile,
                                  String product, String licenseZone,
                                  String metricCrs) throws Exception {
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true);
        CoordinateReferenceSystem metric = CRS.decode(metricCrs, true);
        MathTransform toMetric = CRS.findMathTransform(wgs84, metric, true);

        // Reproject all points to metric
        for (AddressPoint p : gtPoints) {
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

        // Match ground truth -> MCR
        List<MatchPair> matched = new ArrayList<>();
        List<AddressPoint> gtOnly = new ArrayList<>();
        Set<String> matchedMcrIds = new HashSet<>();

        for (AddressPoint gt : gtPoints) {
            Point gtMetric = gt.getMetricGeometry();
            Geometry buffer = gtMetric.buffer(BUFFER_METERS);

            @SuppressWarnings("unchecked")
            List<AddressPoint> candidates = mcrIndex.query(buffer.getEnvelopeInternal());

            AddressPoint bestMatch = null;
            double bestDist = Double.MAX_VALUE;

            for (AddressPoint mcr : candidates) {
                double dist = gtMetric.distance(mcr.getMetricGeometry());
                if (dist <= BUFFER_METERS && dist < bestDist) {
                    bestMatch = mcr;
                    bestDist = dist;
                }
            }

            if (bestMatch != null) {
                matched.add(new MatchPair(gt, bestMatch, bestDist));
                matchedMcrIds.add(bestMatch.getId());
            } else {
                gtOnly.add(gt);
            }
        }

        // Find MCR-only: MCR points in tile not matched to any ground truth
        List<AddressPoint> mcrOnly = new ArrayList<>();
        List<AddressPoint> mcrInTile = new ArrayList<>();
        for (AddressPoint mcr : mcrPoints) {
            if (tileBoundary.contains(mcr.getGeometry())) {
                mcrInTile.add(mcr);
                if (!matchedMcrIds.contains(mcr.getId())) {
                    mcrOnly.add(mcr);
                }
            }
        }

        return new ComparisonResult(matched, gtOnly, mcrOnly, mcrInTile, h3Tile,
                product, licenseZone, gtPoints.size(), mcrInTile.size(), true);
    }
}
