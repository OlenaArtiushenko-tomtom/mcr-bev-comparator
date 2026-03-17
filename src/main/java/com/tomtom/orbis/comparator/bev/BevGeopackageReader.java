package com.tomtom.orbis.comparator.bev;

import com.tomtom.orbis.comparator.model.AddressPoint;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BevGeopackageReader {
    private final GeometryFactory gf = new GeometryFactory();

    public List<AddressPoint> readAddressPoints(Path gpkgPath, Polygon tileBoundary,
                                                 String layerName) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "geopkg");
        params.put("database", gpkgPath.toString());

        DataStore store = DataStoreFinder.getDataStore(params);
        if (store == null) {
            throw new IOException("Could not open GeoPackage: " + gpkgPath);
        }

        try {
            String typeName = layerName != null ? layerName : store.getTypeNames()[0];
            SimpleFeatureSource source = store.getFeatureSource(typeName);
            SimpleFeatureCollection features = source.getFeatures();

            List<AddressPoint> points = new ArrayList<>();
            try (SimpleFeatureIterator iter = features.features()) {
                int id = 0;
                while (iter.hasNext()) {
                    SimpleFeature f = iter.next();
                    Geometry geom = (Geometry) f.getDefaultGeometry();
                    if (geom == null) continue;

                    // Get centroid as Point, force 2D
                    Coordinate c = geom.getCentroid().getCoordinate();
                    Point pt = gf.createPoint(new Coordinate(c.x, c.y));

                    if (!tileBoundary.contains(pt)) continue;

                    points.add(AddressPoint.builder()
                            .id("bev_" + (id++))
                            .source("bev")
                            .geometry(pt)
                            .addressPointType("building")
                            .street(getAttr(f, "strassenname"))
                            .houseNumber(getAttr(f, "hsn"))
                            .postcode(getAttr(f, "plz"))
                            .city(getAttr(f, "gemeindename"))
                            .suburb(getAttr(f, "ortsname"))
                            .build());
                }
            }
            return points;
        } finally {
            store.dispose();
        }
    }

    private String getAttr(SimpleFeature f, String name) {
        Object val = f.getAttribute(name);
        return val != null ? val.toString() : null;
    }
}
