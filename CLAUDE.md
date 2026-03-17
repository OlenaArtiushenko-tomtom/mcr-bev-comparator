# MCR-BEV Comparator

## What this project does
Java CLI tool that compares MCR (Map Central Repository / Orbis) address points against a BEV GeoPackage ground truth file for a given H3 tile. Outputs an HTML map (Leaflet.js) and GeoParquet file (QGIS-compatible).

## Key concepts
- **Address point definition**: Orbis spec `address_point` tag with values: `building`, `land_parcel`, `map_location`, `sub_address`
- **MCR**: Databricks SQL warehouse at `pu_orbis_platform_prod_catalog.map_central_repository.points`
- **BEV**: Austrian Federal Office of Metrology and Surveying address register (GeoPackage)
- **H3 border fix**: queries center tile + `gridDisk(k=1)` neighbors to avoid edge-effect false negatives
- **5m buffer matching**: reprojects to EPSG:31287 (MGI Austria Lambert) for metric buffer

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
- `mcr/McrClient.java` — Databricks JDBC queries
- `bev/BevGeopackageReader.java` — GeoPackage reader (GeoTools)
- `h3/H3TileResolver.java` — H3 tile boundary + neighbor resolution
- `spatial/SpatialMatcher.java` — STRtree-indexed 5m buffer matching
- `output/` — HTML map (Leaflet), GeoParquet (WKB), console summary
