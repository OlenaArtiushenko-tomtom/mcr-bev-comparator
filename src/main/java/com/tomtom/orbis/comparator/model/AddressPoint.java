package com.tomtom.orbis.comparator.model;

import org.locationtech.jts.geom.Point;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an address point per Orbis spec: a Node with tag address_point
 * set to building, land_parcel, map_location, or sub_address.
 *
 * Address components per Orbis #Address Component namespace:
 * block, building, buildingcomplex, buildingsection, city, conscriptionnumber,
 * county, distance, district, door, floor, geographiccode, housename,
 * housenumber, landmark:direction, landmark:nearby, locationcode,
 * neighbourhood, place, postcode, province, state, street, street:dependent,
 * street:number, streetnumber, subaddressarea, suburb, townland, unit.
 */
public class AddressPoint {
    private final String id;
    private final String source; // "mcr" or "bev"
    private final Point geometry;
    private Point metricGeometry;

    // Orbis address_point type
    private final String addressPointType; // building, land_parcel, map_location, sub_address

    // Core address components (from tags or top-level columns)
    private final String street;
    private final String houseNumber;
    private final String postcode;
    private final String city;
    private final String suburb;
    private final String district;
    private final String province;
    private final String state;
    private final String county;
    private final String neighbourhood;
    private final String place;
    private final String houseName;
    private final String block;
    private final String floor;
    private final String unit;
    private final String door;
    private final String buildingName;
    private final String buildingComplex;
    private final String buildingSection;
    private final String subaddressArea;
    private final String conscriptionNumber;
    private final String streetNumber;
    private final String streetDependent;
    private final String locationCode;
    private final String geographicCode;
    private final String townland;

    // Parsed street components
    private final String parsedStreet;

    // Metadata
    private final String osmIdentifier;
    private final String layerId;
    private final String license;
    private final String licenseZone;
    private final String supported;
    private final String locationProvenance;

    private AddressPoint(Builder b) {
        this.id = b.id;
        this.source = b.source;
        this.geometry = b.geometry;
        this.addressPointType = b.addressPointType;
        this.street = b.street;
        this.houseNumber = b.houseNumber;
        this.postcode = b.postcode;
        this.city = b.city;
        this.suburb = b.suburb;
        this.district = b.district;
        this.province = b.province;
        this.state = b.state;
        this.county = b.county;
        this.neighbourhood = b.neighbourhood;
        this.place = b.place;
        this.houseName = b.houseName;
        this.block = b.block;
        this.floor = b.floor;
        this.unit = b.unit;
        this.door = b.door;
        this.buildingName = b.buildingName;
        this.buildingComplex = b.buildingComplex;
        this.buildingSection = b.buildingSection;
        this.subaddressArea = b.subaddressArea;
        this.conscriptionNumber = b.conscriptionNumber;
        this.streetNumber = b.streetNumber;
        this.streetDependent = b.streetDependent;
        this.locationCode = b.locationCode;
        this.geographicCode = b.geographicCode;
        this.townland = b.townland;
        this.parsedStreet = b.parsedStreet;
        this.osmIdentifier = b.osmIdentifier;
        this.layerId = b.layerId;
        this.license = b.license;
        this.licenseZone = b.licenseZone;
        this.supported = b.supported;
        this.locationProvenance = b.locationProvenance;
    }

    // Getters
    public String getId() { return id; }
    public String getSource() { return source; }
    public Point getGeometry() { return geometry; }
    public Point getMetricGeometry() { return metricGeometry; }
    public void setMetricGeometry(Point metricGeometry) { this.metricGeometry = metricGeometry; }
    public String getAddressPointType() { return addressPointType; }
    public String getStreet() { return street; }
    public String getHouseNumber() { return houseNumber; }
    public String getPostcode() { return postcode; }
    public String getCity() { return city; }
    public String getSuburb() { return suburb; }
    public String getDistrict() { return district; }
    public String getProvince() { return province; }
    public String getState() { return state; }
    public String getCounty() { return county; }
    public String getNeighbourhood() { return neighbourhood; }
    public String getPlace() { return place; }
    public String getHouseName() { return houseName; }
    public String getBlock() { return block; }
    public String getFloor() { return floor; }
    public String getUnit() { return unit; }
    public String getDoor() { return door; }
    public String getBuildingName() { return buildingName; }
    public String getBuildingComplex() { return buildingComplex; }
    public String getBuildingSection() { return buildingSection; }
    public String getSubaddressArea() { return subaddressArea; }
    public String getConscriptionNumber() { return conscriptionNumber; }
    public String getStreetNumber() { return streetNumber; }
    public String getStreetDependent() { return streetDependent; }
    public String getLocationCode() { return locationCode; }
    public String getGeographicCode() { return geographicCode; }
    public String getTownland() { return townland; }
    public String getParsedStreet() { return parsedStreet; }
    public String getOsmIdentifier() { return osmIdentifier; }
    public String getLayerId() { return layerId; }
    public String getLicense() { return license; }
    public String getLicenseZone() { return licenseZone; }
    public String getSupported() { return supported; }
    public String getLocationProvenance() { return locationProvenance; }

    public String getLabel() {
        String s = street != null ? street : "";
        String h = houseNumber != null ? houseNumber : "";
        String p = postcode != null ? postcode : "";
        return (s + " " + h + ", " + p).trim();
    }

    /** Returns all non-null address attributes as a flat map for output. */
    public Map<String, String> toAttributeMap() {
        Map<String, String> map = new LinkedHashMap<>();
        put(map, "source", source);
        put(map, "address_point_type", addressPointType);
        put(map, "street", street);
        put(map, "house_number", houseNumber);
        put(map, "postcode", postcode);
        put(map, "city", city);
        put(map, "suburb", suburb);
        put(map, "district", district);
        put(map, "province", province);
        put(map, "state", state);
        put(map, "county", county);
        put(map, "neighbourhood", neighbourhood);
        put(map, "place", place);
        put(map, "house_name", houseName);
        put(map, "block", block);
        put(map, "floor", floor);
        put(map, "unit", unit);
        put(map, "door", door);
        put(map, "building_name", buildingName);
        put(map, "building_complex", buildingComplex);
        put(map, "building_section", buildingSection);
        put(map, "subaddress_area", subaddressArea);
        put(map, "conscription_number", conscriptionNumber);
        put(map, "street_number", streetNumber);
        put(map, "street_dependent", streetDependent);
        put(map, "location_code", locationCode);
        put(map, "geographic_code", geographicCode);
        put(map, "townland", townland);
        put(map, "parsed_street", parsedStreet);
        put(map, "osm_identifier", osmIdentifier);
        put(map, "layer_id", layerId);
        put(map, "license", license);
        put(map, "license_zone", licenseZone);
        put(map, "supported", supported);
        put(map, "location_provenance", locationProvenance);
        return map;
    }

    private void put(Map<String, String> map, String key, String val) {
        if (val != null) map.put(key, val);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id, source, addressPointType;
        private Point geometry;
        private String street, houseNumber, postcode, city, suburb, district;
        private String province, state, county, neighbourhood, place, houseName;
        private String block, floor, unit, door, buildingName, buildingComplex;
        private String buildingSection, subaddressArea, conscriptionNumber;
        private String streetNumber, streetDependent, locationCode, geographicCode, townland;
        private String parsedStreet, osmIdentifier, layerId, license, licenseZone;
        private String supported, locationProvenance;

        public Builder id(String v) { this.id = v; return this; }
        public Builder source(String v) { this.source = v; return this; }
        public Builder geometry(Point v) { this.geometry = v; return this; }
        public Builder addressPointType(String v) { this.addressPointType = v; return this; }
        public Builder street(String v) { this.street = v; return this; }
        public Builder houseNumber(String v) { this.houseNumber = v; return this; }
        public Builder postcode(String v) { this.postcode = v; return this; }
        public Builder city(String v) { this.city = v; return this; }
        public Builder suburb(String v) { this.suburb = v; return this; }
        public Builder district(String v) { this.district = v; return this; }
        public Builder province(String v) { this.province = v; return this; }
        public Builder state(String v) { this.state = v; return this; }
        public Builder county(String v) { this.county = v; return this; }
        public Builder neighbourhood(String v) { this.neighbourhood = v; return this; }
        public Builder place(String v) { this.place = v; return this; }
        public Builder houseName(String v) { this.houseName = v; return this; }
        public Builder block(String v) { this.block = v; return this; }
        public Builder floor(String v) { this.floor = v; return this; }
        public Builder unit(String v) { this.unit = v; return this; }
        public Builder door(String v) { this.door = v; return this; }
        public Builder buildingName(String v) { this.buildingName = v; return this; }
        public Builder buildingComplex(String v) { this.buildingComplex = v; return this; }
        public Builder buildingSection(String v) { this.buildingSection = v; return this; }
        public Builder subaddressArea(String v) { this.subaddressArea = v; return this; }
        public Builder conscriptionNumber(String v) { this.conscriptionNumber = v; return this; }
        public Builder streetNumber(String v) { this.streetNumber = v; return this; }
        public Builder streetDependent(String v) { this.streetDependent = v; return this; }
        public Builder locationCode(String v) { this.locationCode = v; return this; }
        public Builder geographicCode(String v) { this.geographicCode = v; return this; }
        public Builder townland(String v) { this.townland = v; return this; }
        public Builder parsedStreet(String v) { this.parsedStreet = v; return this; }
        public Builder osmIdentifier(String v) { this.osmIdentifier = v; return this; }
        public Builder layerId(String v) { this.layerId = v; return this; }
        public Builder license(String v) { this.license = v; return this; }
        public Builder licenseZone(String v) { this.licenseZone = v; return this; }
        public Builder supported(String v) { this.supported = v; return this; }
        public Builder locationProvenance(String v) { this.locationProvenance = v; return this; }
        public AddressPoint build() { return new AddressPoint(this); }
    }
}
