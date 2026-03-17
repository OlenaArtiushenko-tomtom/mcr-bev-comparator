package com.tomtom.orbis.comparator.h3;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.util.List;

public class H3TileResolver {
    private final H3Core h3;
    private final GeometryFactory gf = new GeometryFactory();

    public H3TileResolver() throws IOException {
        this.h3 = H3Core.newInstance();
    }

    public List<String> getQueryTiles(String h3Index) {
        return h3.gridDisk(h3Index, 1);
    }

    public Polygon getTileBoundary(String h3Index) {
        List<LatLng> boundary = h3.cellToBoundary(h3Index);
        Coordinate[] coords = new Coordinate[boundary.size() + 1];
        for (int i = 0; i < boundary.size(); i++) {
            coords[i] = new Coordinate(boundary.get(i).lng, boundary.get(i).lat);
        }
        coords[boundary.size()] = coords[0]; // close ring
        return gf.createPolygon(coords);
    }

    public LatLng getTileCenter(String h3Index) {
        return h3.cellToLatLng(h3Index);
    }

    public int getResolution(String h3Index) {
        return h3.getResolution(h3Index);
    }
}
