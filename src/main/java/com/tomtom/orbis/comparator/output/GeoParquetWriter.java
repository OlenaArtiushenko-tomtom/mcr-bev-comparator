package com.tomtom.orbis.comparator.output;

import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import com.tomtom.orbis.comparator.model.ComparisonResult.MatchPair;
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
                .optionalString("source")
                .optionalString("street")
                .optionalString("house_number")
                .optionalString("postcode")
                .optionalString("city")
                .optionalString("address_point_type")
                .optionalString("id")
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

            // Write matched BEV points
            for (MatchPair m : result.getMatched()) {
                writer.write(buildRecord(schema, wkbWriter, "matched", m.bev(), m.getDistanceMeters()));
            }

            // Write BEV-only (missing from MCR)
            for (AddressPoint p : result.getBevOnly()) {
                writer.write(buildRecord(schema, wkbWriter, "bev_only", p, null));
            }

            // Write MCR-only (extra)
            for (AddressPoint p : result.getMcrOnly()) {
                writer.write(buildRecord(schema, wkbWriter, "mcr_only", p, null));
            }
        }
    }

    private GenericRecord buildRecord(Schema schema, WKBWriter wkbWriter,
                                       String status, AddressPoint p, Double matchDist) {
        GenericRecord record = new GenericData.Record(schema);
        record.put("match_status", status);
        record.put("geometry", ByteBuffer.wrap(wkbWriter.write(p.getGeometry())));
        record.put("longitude", p.getGeometry().getX());
        record.put("latitude", p.getGeometry().getY());
        record.put("source", p.getSource());
        record.put("street", p.getStreet());
        record.put("house_number", p.getHouseNumber());
        record.put("postcode", p.getPostcode());
        record.put("city", p.getCity());
        record.put("address_point_type", p.getAddressPointType());
        record.put("id", p.getId());
        record.put("match_distance_m", matchDist);
        return record;
    }
}
