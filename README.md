# MCR Source Data Quality Assessor

Automatically assess the quality of incoming geocoding source data. Runs structured quality checks, produces a scored report, highlights anomalies and peculiarities, and optionally compares against [MCR (Map Central Repository)](https://tomtom.atlassian.net/wiki/spaces/MANA/pages/151013262).

## Problem

Geocoding teams rely on local address sources with local peculiarities. There is no automatic way to assess source data quality — checks are manual, inconsistent, miss anomalies, and make supplier comparison difficult.

## Solution

A CLI tool that ingests source data (GeoPackage), runs 6 automated quality dimensions, scores overall quality (0-100 with A-F grade), flags per-record anomalies, and presents results in an interactive dashboard.

## Prerequisites

- Java 17+
- Maven 3.8+
- VPN (GlobalProtect) if using MCR coverage check

## Build

```bash
mvn clean package -DskipTests
```

## Configuration

See `.mcr-compare.properties.example`. Credentials load from (first found wins):
1. CLI arguments
2. Environment variables (`DATABRICKS_TOKEN`, `DATABRICKS_HOST`, `DATABRICKS_HTTP_PATH`)
3. `.mcr-compare.properties` in cwd or `~/`

## Usage

### Source data quality check (no MCR needed)

```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --source addresses.gpkg \
  --h3-tile 871e15b71ffffff
```

Runs completeness, format, duplicate, spatial, and structural checks. No Databricks credentials needed.

### With MCR coverage check

```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --source addresses.gpkg \
  --h3-tile 871e15b71ffffff \
  --product nexventura_26120.000 \
  --license-zone AUT \
  --language de-Latn \
  --metric-crs EPSG:31287
```

Adds MCR coverage dimension: match rate, positional accuracy, missing/extra addresses.

### Options

| Flag | Required | Description |
|------|----------|-------------|
| `-s, --source` | Yes | Path to source GeoPackage |
| `-t, --h3-tile` | Yes | H3 tile index |
| `-p, --product` | No | MCR product (enables coverage check) |
| `-l, --license-zone` | No | License zone for MCR query |
| `--language` | No | Language suffix (default: `de-Latn`) |
| `--metric-crs` | No | Metric CRS for matching (default: `EPSG:3857`) |
| `-c, --config` | No | Path to config file |
| `--token` | No | Databricks PAT |
| `--host` | No | Databricks host |
| `--http-path` | No | Databricks HTTP path |
| `--jdbc-url` | No | Full JDBC URL |
| `-o, --output-dir` | No | Output directory (default: `./output`) |
| `--source-layer` | No | GeoPackage layer name |

### Common configurations

| Country | License Zone | Language | Metric CRS |
|---------|-------------|----------|------------|
| Austria | AUT | de-Latn | EPSG:31287 |
| Germany | DEU | de-Latn | EPSG:25832 |
| Ukraine | UKR | uk-Cyrl | EPSG:32637 |
| France | FRA | fr-Latn | EPSG:2154 |
| Netherlands | NLD | nl-Latn | EPSG:28992 |

## Quality Dimensions

| Dimension | Weight | Checks |
|-----------|--------|--------|
| **Completeness** | 25 | Null rate for street, housenumber, postcode, city |
| **Format** | 15 | Housenumber patterns, postcode format per country, encoding issues (mojibake) |
| **Duplicates** | 15 | Exact coordinate duplicates, same address at different locations (>50m) |
| **Spatial** | 20 | Points outside H3 tile, density outliers (H3 res-9 cells), isolated points |
| **MCR Coverage** | 15 | Match rate (5m buffer), positional accuracy, missing/extra vs MCR |
| **Structural** | 10 | Single-address streets, unusual names, postcode distribution skew |

**Scoring:** `overallScore = weightedAverage(dimensionScores)`. Grade: A (95+), B (85+), C (70+), D (50+), F (<50).

MCR Coverage is skipped when `--product` / `--license-zone` are not provided.

## Output

| File | Description |
|------|-------------|
| `quality_<tile>.html` | Interactive dashboard: score gauge, dimension cards, map, anomaly table |
| `quality_<tile>.parquet` | GeoParquet with `quality_flags` and `worst_severity` per record |

### Dashboard

- **Score header**: Overall grade (A-F) with score
- **Summary**: Clean vs flagged record counts, anomaly counts by severity
- **Dimension cards**: Individual scores with progress bars
- **Map**: Points colored by severity (green=clean, orange=warning, red=critical)
- **Anomaly table**: All issues with severity, dimension, code, message
- **Details**: Per-dimension statistics

### GeoParquet in QGIS

1. Open QGIS > Layer > Add Vector Layer > select `.parquet`
2. Style by `worst_severity`: `CLEAN` (green), `INFO` (blue), `WARNING` (orange), `CRITICAL` (red)
3. Filter by `quality_flags` to investigate specific issues

### Per-record quality flags

`MISSING_STREET`, `MISSING_HOUSENUMBER`, `MISSING_POSTCODE`, `MISSING_CITY`,
`INVALID_HOUSENUMBER_FORMAT`, `INVALID_POSTCODE_FORMAT`, `ENCODING_ISSUE`,
`DUPLICATE_COORDINATE`, `DUPLICATE_ADDRESS_TEXT`,
`OUTSIDE_TILE`, `DENSITY_OUTLIER_HIGH`, `ISOLATED_POINT`,
`MCR_UNMATCHED`, `SINGLE_ADDRESS_STREET`, `UNUSUAL_STREET_NAME`

## Supplier comparison

Run the tool on each supplier's data for the same H3 tile. Compare:
- Overall scores and grades
- Dimension breakdowns (which supplier has better completeness? fewer duplicates?)
- Anomaly counts and types
- GeoParquet side-by-side in QGIS
