package com.tomtom.orbis.comparator.model;

import org.locationtech.jts.geom.Point;

public class AddressPoint {
    private final String id;
    private final String street;
    private final String houseNumber;
    private final String postcode;
    private final String city;
    private final String source; // "mcr" or "bev"
    private final String addressPointType; // building, land_parcel, map_location, sub_address
    private final Point geometry;
    private Point metricGeometry;

    public AddressPoint(String id, String street, String houseNumber, String postcode,
                        String city, String source, String addressPointType, Point geometry) {
        this.id = id;
        this.street = street;
        this.houseNumber = houseNumber;
        this.postcode = postcode;
        this.city = city;
        this.source = source;
        this.addressPointType = addressPointType;
        this.geometry = geometry;
    }

    public String getId() { return id; }
    public String getStreet() { return street; }
    public String getHouseNumber() { return houseNumber; }
    public String getPostcode() { return postcode; }
    public String getCity() { return city; }
    public String getSource() { return source; }
    public String getAddressPointType() { return addressPointType; }
    public Point getGeometry() { return geometry; }
    public Point getMetricGeometry() { return metricGeometry; }
    public void setMetricGeometry(Point metricGeometry) { this.metricGeometry = metricGeometry; }

    public String getLabel() {
        String s = street != null ? street : "";
        String h = houseNumber != null ? houseNumber : "";
        String p = postcode != null ? postcode : "";
        return (s + " " + h + ", " + p).trim();
    }
}
