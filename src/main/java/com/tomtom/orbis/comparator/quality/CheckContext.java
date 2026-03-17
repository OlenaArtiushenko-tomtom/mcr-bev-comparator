package com.tomtom.orbis.comparator.quality;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

public record CheckContext(
        Polygon tileBoundary,
        String h3Tile,
        String metricCrs,
        List<AddressPoint> mcrPoints,
        ComparisonResult mcrComparison
) {}
