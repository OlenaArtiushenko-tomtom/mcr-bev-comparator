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

@Command(name = "mcr-bev-compare", version = "1.0.0",
        description = "Compare MCR (Orbis) address points against BEV GeoPackage ground truth.",
        mixinStandardHelpOptions = true)
public class App implements Callable<Integer> {

    @Option(names = {"-t", "--h3-tile"}, required = true,
            description = "H3 tile index (e.g. 871e15b71ffffff)")
    private String h3Tile;

    @Option(names = {"-b", "--bev-gpkg"}, required = true,
            description = "Path to BEV GeoPackage file")
    private Path bevPath;

    @Option(names = {"-o", "--output-dir"}, defaultValue = "./output",
            description = "Output directory (default: ./output)")
    private Path outputDir;

    @Option(names = {"-p", "--product"}, required = true,
            description = "MCR product (e.g. nexventura_26120.000)")
    private String product;

    @Option(names = {"-l", "--license-zone"}, required = true,
            description = "License zone (e.g. AUT)")
    private String licenseZone;

    @Option(names = {"--host"}, defaultValue = "adb-879908127091742.2.azuredatabricks.net",
            description = "Databricks host")
    private String host;

    @Option(names = {"--http-path"}, defaultValue = "/sql/1.0/warehouses/dad35031bafe9507",
            description = "Databricks HTTP path")
    private String httpPath;

    @Option(names = {"--token"},
            description = "Databricks PAT (or set DATABRICKS_TOKEN env var)")
    private String token;

    @Option(names = {"--bev-layer"},
            description = "GeoPackage layer name (default: first layer)")
    private String bevLayer;

    @Option(names = {"--language"}, defaultValue = "de-Latn",
            description = "Language tag suffix for address components (default: de-Latn)")
    private String language;

    @Override
    public Integer call() throws Exception {
        // Resolve token
        String dbToken = token != null ? token : System.getenv("DATABRICKS_TOKEN");
        if (dbToken == null || dbToken.isBlank()) {
            System.err.println("ERROR: Databricks token required. Use --token or set DATABRICKS_TOKEN env var.");
            return 1;
        }

        // Create output dir
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

        // Step 3: Read BEV
        System.out.println("[3/6] Reading BEV GeoPackage...");
        BevGeopackageReader bevReader = new BevGeopackageReader();
        List<AddressPoint> bevPoints = bevReader.readAddressPoints(bevPath, tileBoundary, bevLayer);
        System.out.printf("       BEV points in tile: %,d%n", bevPoints.size());

        // Step 4: Spatial matching
        System.out.println("[4/6] Spatial matching (5m buffer)...");
        SpatialMatcher matcher = new SpatialMatcher();
        ComparisonResult result = matcher.match(bevPoints, mcrPoints, tileBoundary,
                h3Tile, product, licenseZone);

        // Step 5: Generate outputs
        System.out.println("[5/6] Generating HTML map...");
        Path htmlPath = outputDir.resolve("comparison_" + h3Tile + ".html");
        new HtmlMapGenerator().generate(result, tileBoundary, center.lat, center.lng, htmlPath);
        System.out.printf("       -> %s%n", htmlPath);

        System.out.println("[6/6] Writing GeoParquet...");
        Path parquetPath = outputDir.resolve("comparison_" + h3Tile + ".parquet");
        new GeoParquetWriter().write(result, parquetPath);
        System.out.printf("       -> %s%n", parquetPath);

        // Summary
        new ConsoleSummary().print(result);

        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
