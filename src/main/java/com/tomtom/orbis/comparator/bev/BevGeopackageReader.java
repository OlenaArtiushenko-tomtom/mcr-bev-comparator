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

                    // Check if within tile boundary
                    if (!tileBoundary.contains(pt)) continue;

                    String street = getAttr(f, "strassenname");
                    String hsn = getAttr(f, "hsn");
                    String plz = getAttr(f, "plz");
                    String gemeinde = getAttr(f, "gemeindename");
                    String ort = getAttr(f, "ortsname");

                    points.add(new AddressPoint(
                            "bev_" + (id++),
                            street, hsn, plz,
                            gemeinde != null ? gemeinde : ort,
                            "bev", "building", pt
                    ));
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
