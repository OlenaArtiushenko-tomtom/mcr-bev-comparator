package com.tomtom.orbis.comparator;

import com.tomtom.orbis.comparator.bev.BevGeopackageReader;
import com.tomtom.orbis.comparator.config.AppConfig;
import com.tomtom.orbis.comparator.h3.H3TileResolver;
import com.tomtom.orbis.comparator.mcr.McrClient;
import com.tomtom.orbis.comparator.model.AddressPoint;
import com.tomtom.orbis.comparator.model.ComparisonResult;
import com.tomtom.orbis.comparator.output.ConsoleSummary;
import com.tomtom.orbis.comparator.output.GeoParquetWriter;
import com.tomtom.orbis.comparator.output.HtmlDashboardGenerator;
import com.tomtom.orbis.comparator.output.HtmlMapGenerator;
import com.tomtom.orbis.comparator.quality.CheckContext;
import com.tomtom.orbis.comparator.quality.QualityPipeline;
import com.tomtom.orbis.comparator.quality.QualityReport;
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

@Command(name = "mcr-compare", version = "2.0.0",
        description = "Assess source address data quality and compare against MCR (Orbis).",
        mixinStandardHelpOptions = true)
public class App implements Callable<Integer> {

    @Option(names = {"-s", "--source"}, required = true,
            description = "Path to source address data GeoPackage")
    private Path sourcePath;

    @Option(names = {"-t", "--h3-tile"}, required = true,
            description = "H3 tile index (e.g. 871e15b71ffffff)")
    private String h3Tile;

    @Option(names = {"-o", "--output-dir"}, defaultValue = "./output",
            description = "Output directory (default: ./output)")
    private Path outputDir;

    @Option(names = {"-p", "--product"},
            description = "MCR product for coverage check (e.g. nexventura_26120.000). If omitted, MCR check is skipped.")
    private String product;

    @Option(names = {"-l", "--license-zone"},
            description = "License zone for MCR query (e.g. AUT, UKR)")
    private String licenseZone;

    @Option(names = {"--host"},
            description = "Databricks host")
    private String host;

    @Option(names = {"--http-path"},
            description = "Databricks HTTP path")
    private String httpPath;

    @Option(names = {"--token"},
            description = "Databricks PAT")
    private String token;

    @Option(names = {"--jdbc-url"},
            description = "Full JDBC URL (overrides host/http-path/token)")
    private String jdbcUrl;

    @Option(names = {"-c", "--config"},
            description = "Path to config file")
    private Path configPath;

    @Option(names = {"--source-layer"},
            description = "GeoPackage layer name (default: first layer)")
    private String sourceLayer;

    @Option(names = {"--language"}, defaultValue = "de-Latn",
            description = "Language tag suffix for MCR address components (default: de-Latn)")
    private String language;

    @Option(names = {"--metric-crs"}, defaultValue = "EPSG:3857",
            description = "Metric CRS for buffer matching (default: EPSG:3857)")
    private String metricCrs;

    @Override
    public Integer call() throws Exception {
        Files.createDirectories(outputDir);

        // Step 1: Resolve H3 tile
        System.out.println("[1/5] Resolving H3 tiles...");
        H3TileResolver h3Resolver = new H3TileResolver();
        List<String> queryTiles = h3Resolver.getQueryTiles(h3Tile);
        Polygon tileBoundary = h3Resolver.getTileBoundary(h3Tile);
        LatLng center = h3Resolver.getTileCenter(h3Tile);
        System.out.printf("       Tile: %s (res %d) + %d neighbors%n",
                h3Tile, h3Resolver.getResolution(h3Tile), queryTiles.size() - 1);

        // Step 2: Read source data
        System.out.println("[2/5] Reading source GeoPackage...");
        BevGeopackageReader reader = new BevGeopackageReader();
        List<AddressPoint> sourcePoints = reader.readAddressPoints(sourcePath, tileBoundary, sourceLayer);
        System.out.printf("       Source points in tile: %,d%n", sourcePoints.size());

        if (sourcePoints.isEmpty()) {
            System.out.println("       No source data found in this tile.");
            return 1;
        }

        // Step 3: Optionally query MCR for coverage check
        List<AddressPoint> mcrPoints = List.of();
        ComparisonResult mcrComparison = null;
        boolean mcrAvailable = false;

        if (product != null && licenseZone != null) {
            String finalJdbcUrl = resolveJdbcUrl();
            if (finalJdbcUrl != null) {
                System.out.println("[3/5] Querying MCR for coverage check...");
                try (McrClient mcr = new McrClient(finalJdbcUrl)) {
                    mcrPoints = mcr.queryAddressPoints(queryTiles, product, licenseZone, language);
                }
                System.out.printf("       MCR points (wide area): %,d%n", mcrPoints.size());

                if (!mcrPoints.isEmpty()) {
                    mcrAvailable = true;
                    SpatialMatcher matcher = new SpatialMatcher();
                    mcrComparison = matcher.match(sourcePoints, mcrPoints, tileBoundary,
                            h3Tile, product, licenseZone, metricCrs);
                }
            } else {
                System.out.println("[3/5] No Databricks credentials — skipping MCR coverage check.");
            }
        } else {
            System.out.println("[3/5] No --product/--license-zone — skipping MCR coverage check.");
        }

        // Step 4: Run quality pipeline
        System.out.println("[4/5] Running quality checks...");
        CheckContext context = new CheckContext(tileBoundary, h3Tile, metricCrs, mcrPoints, mcrComparison);
        QualityPipeline pipeline = new QualityPipeline(mcrAvailable);
        String sourceName = sourcePath.getFileName().toString();
        QualityReport report = pipeline.run(sourcePoints, context, h3Tile, sourceName);

        // Step 5: Generate outputs
        System.out.println("[5/5] Generating outputs...");

        Path dashPath = outputDir.resolve("quality_" + h3Tile + ".html");
        new HtmlDashboardGenerator().generate(report, tileBoundary, center.lat, center.lng, dashPath);
        System.out.printf("       Dashboard -> %s%n", dashPath);

        Path parquetPath = outputDir.resolve("quality_" + h3Tile + ".parquet");
        new GeoParquetWriter().writeQualityReport(report, parquetPath);
        System.out.printf("       GeoParquet -> %s%n", parquetPath);

        // Console summary
        printQualitySummary(report);

        return 0;
    }

    private String resolveJdbcUrl() throws Exception {
        AppConfig config = configPath != null ? AppConfig.loadFrom(configPath) : AppConfig.load();
        if (config.isLoaded()) {
            System.out.printf("       Config: %s%n", config.getLoadedFrom());
        }

        String url = coalesce(jdbcUrl, System.getenv("DATABRICKS_JDBC_URL"), config.getJdbcUrl());
        if (url != null) return url;

        String t = coalesce(token, System.getenv("DATABRICKS_TOKEN"), config.getToken());
        String h = coalesce(host, System.getenv("DATABRICKS_HOST"), config.getHost());
        String p = coalesce(httpPath, System.getenv("DATABRICKS_HTTP_PATH"), config.getHttpPath());

        if (t != null && h != null && p != null) {
            return McrClient.buildJdbcUrl(h, p, t);
        }
        return null;
    }

    private void printQualitySummary(QualityReport report) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.printf("  Source Data Quality Report  [%s]  %.1f/100%n",
                report.getScoreGrade(), report.getOverallScore());
        System.out.println("=".repeat(60));
        System.out.printf("  Source: %s%n", report.getSourceName());
        System.out.printf("  H3 Tile: %s%n", report.getH3Tile());
        System.out.printf("  Records: %,d (clean: %,d, flagged: %,d)%n",
                report.getTotalRecords(), report.cleanRecordCount(), report.flaggedRecordCount());
        System.out.println("-".repeat(60));
        for (var dim : report.getDimensions()) {
            if (dim.weight() <= 0) continue;
            System.out.printf("  %-20s %5.1f  (%d issues)%n",
                    dim.dimension(), dim.score(), dim.anomalies().size());
        }
        System.out.println("-".repeat(60));
        System.out.printf("  Anomalies: %d critical, %d warnings, %d info%n",
                report.criticalCount(), report.warningCount(), report.infoCount());
        System.out.println("=".repeat(60));
        System.out.println();
    }

    private static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
