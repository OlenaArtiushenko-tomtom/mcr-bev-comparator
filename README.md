# MCR-BEV Address Point Comparator

Compare [MCR (Map Central Repository)](https://tomtom.atlassian.net/wiki/spaces/MANA/pages/151013262) address points against a BEV GeoPackage ground truth for a given H3 tile.

## Prerequisites

- Java 17+
- Maven 3.8+
- VPN connected (GlobalProtect) for MCR access
- Databricks Personal Access Token (PAT)
- BEV GeoPackage file

## Build

```bash
mvn clean package -DskipTests
```

## Usage

```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --h3-tile 871e15b71ffffff \
  --bev-gpkg C:/path/to/bev.gpkg \
  --product nexventura_26120.000 \
  --license-zone AUT \
  --token dapi...
```

Or use environment variable:

```bash
export DATABRICKS_TOKEN=dapi...
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --h3-tile 871e15b71ffffff \
  --bev-gpkg C:/path/to/bev.gpkg \
  --product nexventura_26120.000 \
  --license-zone AUT
```

### Options

| Flag | Required | Description |
|------|----------|-------------|
| `-t, --h3-tile` | Yes | H3 tile index (e.g. `871e15b71ffffff`) |
| `-b, --bev-gpkg` | Yes | Path to BEV GeoPackage |
| `-p, --product` | Yes | MCR product (e.g. `nexventura_26120.000`) |
| `-l, --license-zone` | Yes | License zone (e.g. `AUT`) |
| `--token` | No | Databricks PAT (or `DATABRICKS_TOKEN` env var) |
| `-o, --output-dir` | No | Output directory (default: `./output`) |
| `--bev-layer` | No | GeoPackage layer name (default: first layer) |
| `--host` | No | Databricks host |
| `--http-path` | No | Databricks SQL warehouse HTTP path |

## Output

| File | Description |
|------|-------------|
| `comparison_<tile>.html` | Interactive Leaflet map showing differences |
| `comparison_<tile>.parquet` | GeoParquet file (open in QGIS) |

### HTML Map

- **Blue markers**: BEV addresses missing from MCR (false negatives)
- **Red markers**: MCR addresses not in BEV (false positives)
- Stats panel in top-right shows recall, precision, F1

### GeoParquet in QGIS

1. Open QGIS
2. Layer > Add Layer > Add Vector Layer
3. Select the `.parquet` file
4. Style by `match_status` field: `matched`, `bev_only`, `mcr_only`

## How it works

1. Resolves H3 tile + 6 neighbors (`gridDisk(k=1)`) to avoid border artifacts
2. Queries MCR for Orbis `address_point` features (`building`, `land_parcel`, `map_location`, `sub_address`)
3. Reads BEV GeoPackage, clips to H3 tile boundary
4. Reprojects to EPSG:31287 (MGI Austria Lambert) for metric 5m buffer matching
5. Uses JTS STRtree spatial index for efficient matching
6. Reports recall/precision/F1 with BEV as ground truth

## Address Point Definition

Per [Orbis spec](https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/feature/address_point.html):
an address point is a Node with tag `address_point` set to one of: `building`, `land_parcel`, `map_location`, `sub_address`.
