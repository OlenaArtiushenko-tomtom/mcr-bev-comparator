# MCR Address Point Comparator

## What this project does
Java CLI tool that compares MCR (Map Central Repository / Orbis) address points against an optional ground truth GeoPackage for a given H3 tile. Works for any location worldwide. Outputs an HTML map (Leaflet.js) and GeoParquet file (QGIS-compatible).

**Two modes:**
- **With ground truth**: full comparison with recall/precision/F1 metrics
- **Without ground truth**: exports MCR address points with a message that ground truth is not available

## Address Point Definition (Orbis spec)
An address point is a Node with tag `address_point` set to one of:
- `building` — building-associated addresses
- `land_parcel` — cadastral survey parcels
- `map_location` — locations with government-assigned codes
- `sub_address` — units/facilities within buildings

### Orbis Address Components (#Address Component namespace)
All 30 component types extracted from MCR tags (`addr:{component}:{language}`):

| Component | Description |
|-----------|-------------|
| `street` | Street name |
| `housenumber` | House number (also top-level column) |
| `postcode` | Postal code |
| `city` | City name |
| `suburb` | Suburb / locality |
| `district` | District |
| `province` | Province |
| `state` | State |
| `county` | County |
| `neighbourhood` | Neighbourhood |
| `place` | Place |
| `housename` | House name (also top-level column) |
| `block` | Block |
| `floor` | Floor |
| `unit` | Unit |
| `door` | Door |
| `building` | Building name |
| `buildingcomplex` | Building complex |
| `buildingsection` | Building section |
| `subaddressarea` | Sub-address area |
| `conscriptionnumber` | Conscription number |
| `streetnumber` | Street number |
| `street:number` | Alternative street number |
| `street:dependent` | Dependent street |
| `locationcode` | Location code |
| `geographiccode` | Geographic code |
| `townland` | Townland |

### Additional properties
- `parsed:addr:street:{lang}` — tokenized street components
- `osm_identifier` — OSM reference
- `layer_id` — Orbis layer identifier
- `license`, `license_zone` — licensing metadata
- `supported` — whether feature is supported
- `location_provenance` — address_point, algorithmic, coordinates, etc.

### Shared Property Groups on Address Point
- Has Address / Has Address Component Parsing
- Has Supported
- Has Orbis Production Info / Has Layer Identifier
- Has QA Info / Has License / Has OSM Identifier
- Has Feedback / Has Internal Source Identifier
- Data On Data Property: Geopolitical
- Feature With Optional Road Access

## Key technical details
- **MCR catalog**: `pu_orbis_platform_prod_catalog.map_central_repository.points`
- **H3 border fix**: queries center tile + `gridDisk(k=1)` neighbors
- **5m buffer matching**: reprojects to configurable metric CRS (default EPSG:3857)
- **Language**: configurable via `--language` (default: `de-Latn`)
- **GeoParquet**: WKB-encoded geometry with GeoParquet 1.1.0 metadata
- **Location independent**: works for any H3 tile / license zone / language

## Build & run
```bash
mvn clean package -DskipTests
```

### With ground truth (full comparison)
```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --h3-tile 871e15b71ffffff \
  --ground-truth /path/to/addresses.gpkg \
  --product nexventura_26120.000 \
  --license-zone AUT \
  --language de-Latn \
  --metric-crs EPSG:31287 \
  --token <DATABRICKS_PAT>
```

### Without ground truth (MCR export only)
```bash
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --h3-tile 871e6384affffff \
  --product nexventura_26120.000 \
  --license-zone UKR \
  --language uk-Cyrl \
  --token <DATABRICKS_PAT>
```

## Common language/CRS combinations
| Country | License Zone | Language | Metric CRS |
|---------|-------------|----------|------------|
| Austria | AUT | de-Latn | EPSG:31287 |
| Germany | DEU | de-Latn | EPSG:25832 |
| Ukraine | UKR | uk-Cyrl | EPSG:32637 |
| France | FRA | fr-Latn | EPSG:2154 |
| Netherlands | NLD | nl-Latn | EPSG:28992 |
| Italy | ITA | it-Latn | EPSG:32632 |

## Project structure
- `App.java` — CLI entry point (picocli), orchestrates both modes
- `mcr/McrClient.java` — Databricks JDBC, extracts all 30 address components
- `bev/BevGeopackageReader.java` — GeoPackage reader (GeoTools), any ground truth
- `h3/H3TileResolver.java` — H3 tile boundary + neighbor resolution
- `spatial/SpatialMatcher.java` — STRtree-indexed 5m buffer matching, configurable CRS
- `output/HtmlMapGenerator.java` — Leaflet.js map (comparison or MCR-only mode)
- `output/GeoParquetWriter.java` — GeoParquet with all address attributes
- `output/ConsoleSummary.java` — Console stats (comparison or MCR-only mode)
- `model/AddressPoint.java` — Full Orbis address model with builder
- `model/ComparisonResult.java` — Supports both comparison and MCR-only modes

## Orbis spec references
- Address point: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/feature/address_point.html
- Properties: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/overview_tables/orbis_properties.html
- Address components: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/namespace/address_component.html
- MCR docs: https://tomtom.atlassian.net/wiki/spaces/MANA/pages/151013262
