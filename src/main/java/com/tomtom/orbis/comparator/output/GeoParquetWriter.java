package com.tomtom.orbis.comparator.output;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import com.tomtom.orbis.comparator.model.ComparisonResult.MatchPair;
import com.tomtom.orbis.comparator.quality.FlaggedPoint;
import com.tomtom.orbis.comparator.quality.QualityReport;
import com.tomtom.orbis.comparator.quality.Severity;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.locationtech.jts.io.WKBWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes comparison results as GeoParquet (1.1.0 spec) with all Orbis address
 * components. Compatible with QGIS, DuckDB, and other GeoParquet readers.
 */
public class GeoParquetWriter {

    private static final String GEO_METADATA = """
            {
              "version": "1.1.0",
              "primary_column": "geometry",
              "columns": {
                "geometry": {
                  "encoding": "WKB",
                  "geometry_types": ["Point"],
                  "crs": {
                    "$schema": "https://proj.org/schemas/v0.7/projjson.schema.json",
                    "type": "GeographicCRS",
                    "name": "WGS 84",
                    "datum": {
                      "type": "GeodeticReferenceFrame",
                      "name": "World Geodetic System 1984",
                      "ellipsoid": {"name": "WGS 84", "semi_major_axis": 6378137, "inverse_flattening": 298.257223563}
                    },
                    "coordinate_system": {
                      "subtype": "ellipsoidal",
                      "axis": [
                        {"name": "Longitude", "abbreviation": "lon", "direction": "east", "unit": "degree"},
                        {"name": "Latitude", "abbreviation": "lat", "direction": "north", "unit": "degree"}
                      ]
                    },
                    "id": {"authority": "EPSG", "code": 4326}
                  },
                  "bbox": [-180, -90, 180, 90]
                }
              }
            }
            """;

    public void write(ComparisonResult result, Path outputPath) throws IOException {
        Schema schema = SchemaBuilder.record("AddressPointComparison")
                .namespace("com.tomtom.orbis.comparator")
                .fields()
                .requiredString("match_status")
                .requiredBytes("geometry")
                .requiredDouble("longitude")
                .requiredDouble("latitude")
                // Source & type
                .optionalString("source")
                .optionalString("id")
                .optionalString("address_point_type")
                // Core address components (Orbis #Address Component)
                .optionalString("street")
                .optionalString("house_number")
                .optionalString("postcode")
                .optionalString("city")
                .optionalString("suburb")
                .optionalString("district")
                .optionalString("province")
                .optionalString("state")
                .optionalString("county")
                .optionalString("neighbourhood")
                .optionalString("place")
                .optionalString("house_name")
                .optionalString("block")
                .optionalString("floor")
                .optionalString("unit")
                .optionalString("door")
                .optionalString("building_name")
                .optionalString("building_complex")
                .optionalString("building_section")
                .optionalString("subaddress_area")
                .optionalString("conscription_number")
                .optionalString("street_number")
                .optionalString("street_dependent")
                .optionalString("location_code")
                .optionalString("geographic_code")
                .optionalString("townland")
                // Parsed
                .optionalString("parsed_street")
                // Metadata
                .optionalString("osm_identifier")
                .optionalString("layer_id")
                .optionalString("license")
                .optionalString("license_zone")
                .optionalString("supported")
                .optionalString("location_provenance")
                // Match info
                .optionalDouble("match_distance_m")
                .endRecord();

        WKBWriter wkbWriter = new WKBWriter();
        org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(outputPath.toString());

        Configuration conf = new Configuration();
        conf.setBoolean("parquet.avro.write-old-list-structure", false);

        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(hadoopPath)
                .withSchema(schema)
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withExtraMetaData(Map.of("geo", GEO_METADATA))
                .build()) {

            if (result.isGroundTruthAvailable()) {
                for (MatchPair m : result.getMatched()) {
                    writer.write(buildRecord(schema, wkbWriter, "matched", m.groundTruth(), m.getDistanceMeters()));
                }
                for (AddressPoint p : result.getGroundTruthOnly()) {
                    writer.write(buildRecord(schema, wkbWriter, "gt_only", p, null));
                }
                for (AddressPoint p : result.getMcrOnly()) {
                    writer.write(buildRecord(schema, wkbWriter, "mcr_only", p, null));
                }
            } else {
                for (AddressPoint p : result.getMcrInTile()) {
                    writer.write(buildRecord(schema, wkbWriter, "mcr", p, null));
                }
            }
        }
    }

    private GenericRecord buildRecord(Schema schema, WKBWriter wkbWriter,
                                       String status, AddressPoint p, Double matchDist) {
        GenericRecord r = new GenericData.Record(schema);
        r.put("match_status", status);
        r.put("geometry", ByteBuffer.wrap(wkbWriter.write(p.getGeometry())));
        r.put("longitude", p.getGeometry().getX());
        r.put("latitude", p.getGeometry().getY());
        r.put("source", p.getSource());
        r.put("id", p.getId());
        r.put("address_point_type", p.getAddressPointType());
        // Address components
        r.put("street", p.getStreet());
        r.put("house_number", p.getHouseNumber());
        r.put("postcode", p.getPostcode());
        r.put("city", p.getCity());
        r.put("suburb", p.getSuburb());
        r.put("district", p.getDistrict());
        r.put("province", p.getProvince());
        r.put("state", p.getState());
        r.put("county", p.getCounty());
        r.put("neighbourhood", p.getNeighbourhood());
        r.put("place", p.getPlace());
        r.put("house_name", p.getHouseName());
        r.put("block", p.getBlock());
        r.put("floor", p.getFloor());
        r.put("unit", p.getUnit());
        r.put("door", p.getDoor());
        r.put("building_name", p.getBuildingName());
        r.put("building_complex", p.getBuildingComplex());
        r.put("building_section", p.getBuildingSection());
        r.put("subaddress_area", p.getSubaddressArea());
        r.put("conscription_number", p.getConscriptionNumber());
        r.put("street_number", p.getStreetNumber());
        r.put("street_dependent", p.getStreetDependent());
        r.put("location_code", p.getLocationCode());
        r.put("geographic_code", p.getGeographicCode());
        r.put("townland", p.getTownland());
        r.put("parsed_street", p.getParsedStreet());
        // Metadata
        r.put("osm_identifier", p.getOsmIdentifier());
        r.put("layer_id", p.getLayerId());
        r.put("license", p.getLicense());
        r.put("license_zone", p.getLicenseZone());
        r.put("supported", p.getSupported());
        r.put("location_provenance", p.getLocationProvenance());
        r.put("match_distance_m", matchDist);
        return r;
    }

    /** Write quality report with per-record flags to GeoParquet. */
    public void writeQualityReport(QualityReport report, Path outputPath) throws IOException {
        Schema schema = SchemaBuilder.record("QualityFlaggedPoint")
                .namespace("com.tomtom.orbis.comparator")
                .fields()
                .requiredBytes("geometry")
                .requiredDouble("longitude")
                .requiredDouble("latitude")
                .optionalString("id")
                .optionalString("street")
                .optionalString("house_number")
                .optionalString("postcode")
                .optionalString("city")
                .optionalString("suburb")
                .optionalString("source")
                .optionalString("quality_flags")
                .optionalString("worst_severity")
                .endRecord();

        WKBWriter wkbWriter = new WKBWriter();
        org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(outputPath.toString());

        Configuration conf = new Configuration();
        conf.setBoolean("parquet.avro.write-old-list-structure", false);

        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(hadoopPath)
                .withSchema(schema)
                .withConf(conf)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withExtraMetaData(Map.of("geo", GEO_METADATA))
                .build()) {

            for (FlaggedPoint fp : report.getFlaggedPoints()) {
                AddressPoint p = fp.getPoint();
                GenericRecord r = new GenericData.Record(schema);
                r.put("geometry", ByteBuffer.wrap(wkbWriter.write(p.getGeometry())));
                r.put("longitude", p.getGeometry().getX());
                r.put("latitude", p.getGeometry().getY());
                r.put("id", p.getId());
                r.put("street", p.getStreet());
                r.put("house_number", p.getHouseNumber());
                r.put("postcode", p.getPostcode());
                r.put("city", p.getCity());
                r.put("suburb", p.getSuburb());
                r.put("source", p.getSource());
                r.put("quality_flags", fp.getFlagsString());
                Severity sev = fp.getWorstSeverity();
                r.put("worst_severity", sev != null ? sev.name() : "CLEAN");
                writer.write(r);
            }
        }
    }
}
