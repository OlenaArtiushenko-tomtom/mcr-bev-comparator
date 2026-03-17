package com.tomtom.orbis.comparator.mcr;

import com.tomtom.orbis.comparator.model.AddressPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class McrClient implements AutoCloseable {
    private static final String MCR_CATALOG = "pu_orbis_platform_prod_catalog.map_central_repository";
    private final Connection connection;
    private final GeometryFactory gf = new GeometryFactory();

    public McrClient(String jdbcUrl) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl);
    }

    public static String buildJdbcUrl(String host, String httpPath, String token) {
        return "jdbc:databricks://" + host + ":443/default"
                + ";transportMode=http;ssl=1;AuthMech=3"
                + ";httpPath=" + httpPath
                + ";UID=token;PWD=" + token;
    }

    public List<AddressPoint> queryAddressPoints(List<String> h3Tiles, String product,
                                                  String licenseZone) throws SQLException {
        StringBuilder h3Conditions = new StringBuilder();
        for (int i = 0; i < h3Tiles.size(); i++) {
            if (i > 0) h3Conditions.append(" OR ");
            h3Conditions.append("h3_ischildof(h3_index, '").append(h3Tiles.get(i)).append("')");
        }

        String sql = "SELECT orbis_id, name, `addr:housenumber` as housenumber, geometry, h3_index, "
                + "tags['address_point'] as address_point_type, "
                + "tags['addr:street:de-Latn'] as street, "
                + "tags['addr:postcode:de-Latn'] as postcode, "
                + "tags['addr:city:de-Latn'] as city "
                + "FROM " + MCR_CATALOG + ".points "
                + "WHERE product = '" + product + "' "
                + "AND license_zone = '" + licenseZone + "' "
                + "AND h3_index != '0' "
                + "AND tags['address_point'] IN ('building', 'land_parcel', 'map_location', 'sub_address') "
                + "AND (" + h3Conditions + ")";

        List<AddressPoint> points = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Point geom = parseWktPoint(rs.getString("geometry"));
                if (geom == null) continue;

                points.add(new AddressPoint(
                        rs.getString("orbis_id"),
                        rs.getString("street"),
                        rs.getString("housenumber"),
                        rs.getString("postcode"),
                        rs.getString("city"),
                        "mcr",
                        rs.getString("address_point_type"),
                        geom
                ));
            }
        }
        return points;
    }

    private Point parseWktPoint(String wkt) {
        if (wkt == null) return null;
        try {
            String cleaned = wkt.replace("POINT(", "").replace("POINT (", "").replace(")", "").trim();
            String[] parts = cleaned.split("\\s+");
            double lon = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            return gf.createPoint(new Coordinate(lon, lat));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
