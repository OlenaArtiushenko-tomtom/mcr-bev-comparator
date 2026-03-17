package com.tomtom.orbis.comparator;

import com.tomtom.orbis.comparator.bev.BevGeopackageReader;
import com.tomtom.orbis.comparator.h3.H3TileResolver;
import com.tomtom.orbis.comparator.mcr.McrClient;
import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import com.tomtom.orbis.comparator.output.ConsoleSummary;
import com.tomtom.orbis.comparator.output.GeoParquetWriter;
import com.tomtom.orbis.comparator.output.HtmlMapGenerator;
import com.tomtom.orbis.comparator.spatial.SpatialMatcher;
import com.uber.h3core.util.LatLng;
import org.locationtech.jts.geom.Polygon;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "mcr-compare", version = "1.1.0",
        description = "Compare MCR (Orbis) address points against an optional ground truth GeoPackage.",
        mixinStandardHelpOptions = true)
public class App implements Callable<Integer> {

    @Option(names = {"-t", "--h3-tile"}, required = true,
            description = "H3 tile index (e.g. 871e15b71ffffff)")
    private String h3Tile;

    @Option(names = {"-g", "--ground-truth"},
            description = "Path to ground truth GeoPackage file (optional — if omitted, MCR data is exported without comparison)")
    private Path groundTruthPath;

    @Option(names = {"-o", "--output-dir"}, defaultValue = "./output",
            description = "Output directory (default: ./output)")
    private Path outputDir;

    @Option(names = {"-p", "--product"}, required = true,
            description = "MCR product (e.g. nexventura_26120.000)")
    private String product;

    @Option(names = {"-l", "--license-zone"}, required = true,
            description = "License zone (e.g. AUT, UKR, DEU)")
    private String licenseZone;

    @Option(names = {"--host"}, defaultValue = "adb-879908127091742.2.azuredatabricks.net",
            description = "Databricks host")
    private String host;

    @Option(names = {"--http-path"}, defaultValue = "/sql/1.0/warehouses/dad35031bafe9507",
            description = "Databricks SQL warehouse HTTP path")
    private String httpPath;

    @Option(names = {"--token"},
            description = "Databricks PAT (or set DATABRICKS_TOKEN env var)")
    private String token;

    @Option(names = {"--gt-layer"},
            description = "GeoPackage layer name (default: first layer)")
    private String gtLayer;

    @Option(names = {"--language"}, defaultValue = "de-Latn",
            description = "Language tag suffix for address components (default: de-Latn)")
    private String language;

    @Option(names = {"--metric-crs"}, defaultValue = "EPSG:3857",
            description = "Metric CRS for buffer matching (default: EPSG:3857). "
                    + "Use EPSG:31287 for Austria, EPSG:32637 for Ukraine, etc.")
    private String metricCrs;

    @Override
    public Integer call() throws Exception {
        // Resolve token
        String dbToken = token != null ? token : System.getenv("DATABRICKS_TOKEN");
        if (dbToken == null || dbToken.isBlank()) {
            System.err.println("ERROR: Databricks token required. Use --token or set DATABRICKS_TOKEN env var.");
            return 1;
        }

        Files.createDirectories(outputDir);

        // Step 1: Resolve H3 tiles
        System.out.println("[1/6] Resolving H3 tiles...");
        H3TileResolver h3Resolver = new H3TileResolver();
        List<String> queryTiles = h3Resolver.getQueryTiles(h3Tile);
        Polygon tileBoundary = h3Resolver.getTileBoundary(h3Tile);
        LatLng center = h3Resolver.getTileCenter(h3Tile);
        System.out.printf("       Center tile: %s (res %d) + %d neighbors%n",
                h3Tile, h3Resolver.getResolution(h3Tile), queryTiles.size() - 1);

        // Step 2: Query MCR
        System.out.println("[2/6] Querying MCR address points...");
        String jdbcUrl = McrClient.buildJdbcUrl(host, httpPath, dbToken);
        List<AddressPoint> mcrPoints;
        try (McrClient mcr = new McrClient(jdbcUrl)) {
            mcrPoints = mcr.queryAddressPoints(queryTiles, product, licenseZone, language);
        }
        System.out.printf("       MCR points (wide area): %,d%n", mcrPoints.size());

        if (mcrPoints.isEmpty()) {
            System.out.println("       No MCR address points found. Check product, license zone, and H3 tile.");
            return 1;
        }

        ComparisonResult result;

        // Step 3: Read ground truth (if provided)
        if (groundTruthPath != null) {
            System.out.println("[3/6] Reading ground truth GeoPackage...");
            BevGeopackageReader gtReader = new BevGeopackageReader();
            List<AddressPoint> gtPoints = gtReader.readAddressPoints(groundTruthPath, tileBoundary, gtLayer);
            System.out.printf("       Ground truth points in tile: %,d%n", gtPoints.size());

            if (gtPoints.isEmpty()) {
                System.out.println("       Ground truth has no data for this tile.");
                System.out.println("       Falling back to MCR-only export.");
                result = buildMcrOnlyResult(mcrPoints, tileBoundary);
            } else {
                // Step 4: Spatial matching
                System.out.println("[4/6] Spatial matching (5m buffer)...");
                SpatialMatcher matcher = new SpatialMatcher();
                result = matcher.match(gtPoints, mcrPoints, tileBoundary,
                        h3Tile, product, licenseZone, metricCrs);
            }
        } else {
            System.out.println("[3/6] No ground truth provided — MCR-only export.");
            result = buildMcrOnlyResult(mcrPoints, tileBoundary);
        }

        // Step 5: Generate HTML
        System.out.println("[5/6] Generating HTML map...");
        Path htmlPath = outputDir.resolve("comparison_" + h3Tile + ".html");
        new HtmlMapGenerator().generate(result, tileBoundary, center.lat, center.lng, htmlPath);
        System.out.printf("       -> %s%n", htmlPath);

        // Step 6: Write GeoParquet
        System.out.println("[6/6] Writing GeoParquet...");
        Path parquetPath = outputDir.resolve("comparison_" + h3Tile + ".parquet");
        new GeoParquetWriter().write(result, parquetPath);
        System.out.printf("       -> %s%n", parquetPath);

        // Summary
        new ConsoleSummary().print(result);

        return 0;
    }

    private ComparisonResult buildMcrOnlyResult(List<AddressPoint> mcrPoints, Polygon tileBoundary) {
        List<AddressPoint> mcrInTile = mcrPoints.stream()
                .filter(p -> tileBoundary.contains(p.getGeometry()))
                .toList();
        return ComparisonResult.mcrOnly(mcrInTile, h3Tile, product, licenseZone);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
