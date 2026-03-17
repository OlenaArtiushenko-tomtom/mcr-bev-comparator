# MCR Address Point Comparator

Compare [MCR (Map Central Repository)](https://tomtom.atlassian.net/wiki/spaces/MANA/pages/151013262) address points against an optional ground truth GeoPackage for any location worldwide.

## Prerequisites

- Java 17+
- Maven 3.8+
- VPN connected (GlobalProtect) for MCR access
- Databricks Personal Access Token (PAT)

## Build

```bash
mvn clean package -DskipTests
```

## Usage

### With ground truth (full comparison)

```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --h3-tile 871e15b71ffffff \
  --ground-truth C:/path/to/addresses.gpkg \
  --product nexventura_26120.000 \
  --license-zone AUT \
  --language de-Latn \
  --metric-crs EPSG:31287 \
  --token dapi...
```

### Without ground truth (MCR export only)

```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --h3-tile 871e6384affffff \
  --product nexventura_26120.000 \
  --license-zone UKR \
  --language uk-Cyrl \
  --token dapi...
```

When no ground truth is provided (or the ground truth has no data for the tile), the tool exports MCR data and reports that ground truth is not available.

### Options

| Flag | Required | Description |
|------|----------|-------------|
| `-t, --h3-tile` | Yes | H3 tile index (e.g. `871e15b71ffffff`) |
| `-p, --product` | Yes | MCR product (e.g. `nexventura_26120.000`) |
| `-l, --license-zone` | Yes | License zone (e.g. `AUT`, `UKR`, `DEU`) |
| `-g, --ground-truth` | No | Path to ground truth GeoPackage |
| `--token` | No | Databricks PAT (or `DATABRICKS_TOKEN` env var) |
| `--language` | No | Language suffix for address tags (default: `de-Latn`) |
| `--metric-crs` | No | Metric CRS for buffer matching (default: `EPSG:3857`) |
| `-o, --output-dir` | No | Output directory (default: `./output`) |
| `--gt-layer` | No | GeoPackage layer name (default: first layer) |
| `--host` | No | Databricks host |
| `--http-path` | No | Databricks SQL warehouse HTTP path |

### Common language/CRS combinations

| Country | License Zone | Language | Metric CRS |
|---------|-------------|----------|------------|
| Austria | AUT | de-Latn | EPSG:31287 |
| Germany | DEU | de-Latn | EPSG:25832 |
| Ukraine | UKR | uk-Cyrl | EPSG:32637 |
| France | FRA | fr-Latn | EPSG:2154 |
| Netherlands | NLD | nl-Latn | EPSG:28992 |

## Output

| File | Description |
|------|-------------|
| `comparison_<tile>.html` | Interactive Leaflet map |
| `comparison_<tile>.parquet` | GeoParquet file (open in QGIS) |

### HTML Map

**With ground truth:**
- **Blue markers**: Ground truth addresses missing from MCR (false negatives)
- **Red markers**: MCR addresses not in ground truth (false positives)
- Stats panel shows recall, precision, F1

**Without ground truth:**
- **Green markers**: All MCR address points in the tile
- Stats panel shows point count and "ground truth not available"

### GeoParquet in QGIS

1. Open QGIS
2. Layer > Add Layer > Add Vector Layer
3. Select the `.parquet` file
4. Style by `match_status` field: `matched`, `gt_only`, `mcr_only`, or `mcr`

## How it works

1. Resolves H3 tile + 6 neighbors (`gridDisk(k=1)`) to avoid border artifacts
2. Queries MCR for Orbis `address_point` features (`building`, `land_parcel`, `map_location`, `sub_address`)
3. If ground truth provided: reads GeoPackage, clips to H3 tile boundary
4. If ground truth has data: reprojects to metric CRS for 5m buffer matching using JTS STRtree
5. Reports recall/precision/F1, or "ground truth not available"
6. Outputs HTML map + GeoParquet

## Address Point Definition

Per [Orbis spec](https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/feature/address_point.html):
an address point is a Node with tag `address_point` set to one of: `building`, `land_parcel`, `map_location`, `sub_address`.

### Orbis Address Components

All 30 [address components](https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/namespace/address_component.html) are extracted from MCR tags:

`block`, `building`, `buildingcomplex`, `buildingsection`, `city`, `conscriptionnumber`, `county`, `distance`, `district`, `door`, `floor`, `geographiccode`, `housename`, `housenumber`, `landmark:direction`, `landmark:nearby`, `locationcode`, `neighbourhood`, `place`, `postcode`, `province`, `state`, `street`, `street:dependent`, `street:number`, `streetnumber`, `subaddressarea`, `suburb`, `townland`, `unit`

Plus metadata: `parsed:addr:street`, `osm_identifier`, `layer_id`, `license`, `license_zone`, `supported`, `location_provenance`.
