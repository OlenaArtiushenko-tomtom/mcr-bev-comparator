package com.tomtom.orbis.comparator.mcr;

import com.tomtom.orbis.comparator.model.AddressPoint;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries MCR (Databricks) for Orbis address_point features, extracting all
 * address components per the Orbis #Address Component namespace.
 *
 * Tag keys follow the pattern: addr:{component}:{language} (e.g. addr:street:de-Latn)
 * Some components also exist as top-level columns (addr:housenumber, addr:housename).
 */
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
                                                  String licenseZone, String language) throws SQLException {
        StringBuilder h3Conditions = new StringBuilder();
        for (int i = 0; i < h3Tiles.size(); i++) {
            if (i > 0) h3Conditions.append(" OR ");
            h3Conditions.append("h3_ischildof(h3_index, '").append(h3Tiles.get(i)).append("')");
        }

        // Language suffix for tag lookups (e.g. "de-Latn")
        String lang = language != null ? language : "de-Latn";

        // Query all Orbis address components from tags + top-level columns
        String sql = "SELECT "
                + "orbis_id, "
                + "osm_identifier, "
                + "layer_id, "
                + "name, "
                + "`addr:housenumber` as col_housenumber, "
                + "`addr:housename` as col_housename, "
                + "geometry, "
                + "h3_index, "
                // address_point type
                + "tags['address_point'] as address_point_type, "
                // Core address components from tags
                + "tags['addr:street:" + lang + "'] as tag_street, "
                + "tags['addr:housenumber:" + lang + "'] as tag_housenumber, "
                + "tags['addr:postcode:" + lang + "'] as tag_postcode, "
                + "tags['addr:city:" + lang + "'] as tag_city, "
                + "tags['addr:suburb:" + lang + "'] as tag_suburb, "
                + "tags['addr:district:" + lang + "'] as tag_district, "
                + "tags['addr:province:" + lang + "'] as tag_province, "
                + "tags['addr:state:" + lang + "'] as tag_state, "
                + "tags['addr:county:" + lang + "'] as tag_county, "
                + "tags['addr:neighbourhood:" + lang + "'] as tag_neighbourhood, "
                + "tags['addr:place:" + lang + "'] as tag_place, "
                + "tags['addr:housename:" + lang + "'] as tag_housename, "
                + "tags['addr:block:" + lang + "'] as tag_block, "
                + "tags['addr:floor:" + lang + "'] as tag_floor, "
                + "tags['addr:unit:" + lang + "'] as tag_unit, "
                + "tags['addr:door:" + lang + "'] as tag_door, "
                + "tags['addr:building:" + lang + "'] as tag_building, "
                + "tags['addr:buildingcomplex:" + lang + "'] as tag_buildingcomplex, "
                + "tags['addr:buildingsection:" + lang + "'] as tag_buildingsection, "
                + "tags['addr:subaddressarea:" + lang + "'] as tag_subaddressarea, "
                + "tags['addr:conscriptionnumber:" + lang + "'] as tag_conscriptionnumber, "
                + "tags['addr:streetnumber:" + lang + "'] as tag_streetnumber, "
                + "tags['addr:street:number:" + lang + "'] as tag_street_number, "
                + "tags['addr:street:dependent:" + lang + "'] as tag_street_dependent, "
                + "tags['addr:locationcode:" + lang + "'] as tag_locationcode, "
                + "tags['addr:geographiccode:" + lang + "'] as tag_geographiccode, "
                + "tags['addr:townland:" + lang + "'] as tag_townland, "
                // Parsed street
                + "tags['parsed:addr:street:" + lang + "'] as parsed_street, "
                // Metadata
                + "tags['license'] as tag_license, "
                + "tags['license_zone'] as tag_license_zone, "
                + "tags['supported'] as tag_supported, "
                + "tags['location_provenance'] as tag_location_provenance "
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

                // Prefer tag value, fall back to top-level column
                String houseNumber = coalesce(rs.getString("tag_housenumber"),
                        rs.getString("col_housenumber"));
                String houseName = coalesce(rs.getString("tag_housename"),
                        rs.getString("col_housename"));
                String streetNumber = coalesce(rs.getString("tag_streetnumber"),
                        rs.getString("tag_street_number"));

                points.add(AddressPoint.builder()
                        .id(rs.getString("orbis_id"))
                        .source("mcr")
                        .geometry(geom)
                        .addressPointType(rs.getString("address_point_type"))
                        // Address components
                        .street(rs.getString("tag_street"))
                        .houseNumber(houseNumber)
                        .postcode(rs.getString("tag_postcode"))
                        .city(rs.getString("tag_city"))
                        .suburb(rs.getString("tag_suburb"))
                        .district(rs.getString("tag_district"))
                        .province(rs.getString("tag_province"))
                        .state(rs.getString("tag_state"))
                        .county(rs.getString("tag_county"))
                        .neighbourhood(rs.getString("tag_neighbourhood"))
                        .place(rs.getString("tag_place"))
                        .houseName(houseName)
                        .block(rs.getString("tag_block"))
                        .floor(rs.getString("tag_floor"))
                        .unit(rs.getString("tag_unit"))
                        .door(rs.getString("tag_door"))
                        .buildingName(rs.getString("tag_building"))
                        .buildingComplex(rs.getString("tag_buildingcomplex"))
                        .buildingSection(rs.getString("tag_buildingsection"))
                        .subaddressArea(rs.getString("tag_subaddressarea"))
                        .conscriptionNumber(rs.getString("tag_conscriptionnumber"))
                        .streetNumber(streetNumber)
                        .streetDependent(rs.getString("tag_street_dependent"))
                        .locationCode(rs.getString("tag_locationcode"))
                        .geographicCode(rs.getString("tag_geographiccode"))
                        .townland(rs.getString("tag_townland"))
                        .parsedStreet(rs.getString("parsed_street"))
                        // Metadata
                        .osmIdentifier(rs.getString("osm_identifier"))
                        .layerId(rs.getString("layer_id"))
                        .license(rs.getString("tag_license"))
                        .licenseZone(rs.getString("tag_license_zone"))
                        .supported(rs.getString("tag_supported"))
                        .locationProvenance(rs.getString("tag_location_provenance"))
                        .build());
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

    private String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
