# MCR-BEV Comparator

## What this project does
Java CLI tool that compares MCR (Map Central Repository / Orbis) address points against a BEV GeoPackage ground truth file for a given H3 tile. Outputs an HTML map (Leaflet.js) and GeoParquet file (QGIS-compatible).

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
- **5m buffer matching**: reprojects to EPSG:31287 (MGI Austria Lambert)
- **Language**: configurable via `--language` (default: `de-Latn`)
- **GeoParquet**: WKB-encoded geometry with GeoParquet 1.1.0 metadata

## Build & run
```bash
mvn clean package -DskipTests
java -jar target/mcr-bev-comparator-1.0-SNAPSHOT.jar \
  --h3-tile 871e15b71ffffff \
  --bev-gpkg /path/to/bev.gpkg \
  --product nexventura_26120.000 \
  --license-zone AUT \
  --token <DATABRICKS_PAT>
```

## Project structure
- `App.java` — CLI entry point (picocli)
- `mcr/McrClient.java` — Databricks JDBC, extracts all 30 address components from tags
- `bev/BevGeopackageReader.java` — GeoPackage reader (GeoTools)
- `h3/H3TileResolver.java` — H3 tile boundary + neighbor resolution
- `spatial/SpatialMatcher.java` — STRtree-indexed 5m buffer matching
- `output/HtmlMapGenerator.java` — Leaflet.js interactive map
- `output/GeoParquetWriter.java` — GeoParquet with all address attributes
- `output/ConsoleSummary.java` — Console recall/precision/F1
- `model/AddressPoint.java` — Full Orbis address model with builder

## Orbis spec references
- Address point: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/feature/address_point.html
- Properties: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/overview_tables/orbis_properties.html
- Address components: https://specs.tomtomgroup.com/orbis/documentation/platform/daily/specifications/feature_model/namespace/address_component.html
- MCR docs: https://tomtom.atlassian.net/wiki/spaces/MANA/pages/151013262
