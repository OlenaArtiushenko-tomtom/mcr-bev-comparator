# MCR Source Data Quality Assessor

## What this project does
Java CLI tool that automatically assesses the quality of incoming geocoding source data (GeoPackage). Runs 6 quality dimensions, produces an overall score (0-100), flags anomalies, and optionally compares against MCR (Orbis) as reference. Outputs an interactive HTML dashboard and GeoParquet with per-record quality flags.

**Problem solved:** Geocoding teams lack an automatic way to assess source data quality. Manual checks are inconsistent, miss anomalies, waste time, and make supplier comparison difficult.

## Quality Dimensions

| Dimension | Weight | What it checks |
|-----------|--------|----------------|
| **Completeness** | 25 | Null rate for street, housenumber, postcode, city |
| **Format** | 15 | Housenumber patterns, postcode format per country, encoding issues |
| **Duplicates** | 15 | Exact coordinate duplicates, same address text at different locations |
| **Spatial** | 20 | Points outside tile, density outliers (H3 res-9), isolated points |
| **MCR Coverage** | 15 | Match rate vs MCR, positional accuracy, missing/extra addresses |
| **Structural** | 10 | Single-address streets, unusual names, postcode skew |

MCR Coverage is skipped (weight=0) when MCR credentials or product/license-zone are not provided.

## Scoring
- `overallScore = weightedAverage(dimensionScores)`
- Grade: A (95+), B (85+), C (70+), D (50+), F (<50)
- Per-record flags: `QualityFlag` enum, written to GeoParquet `quality_flags` column

## Quality Flags (per-record)
`MISSING_STREET`, `MISSING_HOUSENUMBER`, `MISSING_POSTCODE`, `MISSING_CITY`,
`INVALID_HOUSENUMBER_FORMAT`, `INVALID_POSTCODE_FORMAT`, `ENCODING_ISSUE`,
`DUPLICATE_COORDINATE`, `DUPLICATE_ADDRESS_TEXT`,
`OUTSIDE_TILE`, `DENSITY_OUTLIER_HIGH`, `ISOLATED_POINT`,
`MCR_UNMATCHED`, `SINGLE_ADDRESS_STREET`, `UNUSUAL_STREET_NAME`

## Build & run
```bash
mvn clean package -DskipTests
```

### Quality check (source data only)
```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --source /path/to/addresses.gpkg \
  --h3-tile 871e15b71ffffff
```

### Quality check with MCR coverage
```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --source /path/to/addresses.gpkg \
  --h3-tile 871e15b71ffffff \
  --product nexventura_26120.000 \
  --license-zone AUT \
  --language de-Latn \
  --metric-crs EPSG:31287
```

## Credentials
Loaded from (first found wins):
1. CLI args (`--token`, `--host`, `--http-path`, `--jdbc-url`)
2. Env vars (`DATABRICKS_TOKEN`, `DATABRICKS_HOST`, `DATABRICKS_HTTP_PATH`)
3. `.mcr-compare.properties` in cwd or `~/`

## Output
- `quality_<tile>.html` — interactive dashboard with score, dimension cards, map, anomaly table
- `quality_<tile>.parquet` — GeoParquet with `quality_flags` and `worst_severity` columns

## Project structure
- `App.java` — CLI, orchestrates quality pipeline
- `quality/QualityPipeline.java` — runs all checks, computes weighted score
- `quality/QualityReport.java` — overall score + dimensions + flagged points
- `quality/check/` — 6 check implementations (CompletenessCheck, FormatValidationCheck, DuplicateCheck, SpatialAnomalyCheck, McrCoverageCheck, StructuralCheck)
- `quality/` — Severity, Anomaly, QualityFlag, FlaggedPoint, DimensionResult, CheckContext
- `mcr/McrClient.java` — Databricks JDBC, all 30 Orbis address components
- `bev/BevGeopackageReader.java` — GeoPackage reader
- `output/HtmlDashboardGenerator.java` — quality dashboard with Chart.js + Leaflet
- `output/GeoParquetWriter.java` — GeoParquet with quality flags

## Orbis spec references
- Address point: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/feature/address_point.html
- Address components: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/namespace/address_component.html
- MCR docs: https://tomtom.atlassian.net/wiki/spaces/MANA/pages/151013262
