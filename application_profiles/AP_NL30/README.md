# DCAT‑AP‑NL 3.0 Mapping Package for Dataverse (GDN)

This package contains mapping configurations to convert Dataverse JSON into **DCAT‑AP‑NL 3.0–compliant RDF**. It covers Dataset, Distribution, DataService, and Catalog metadata. 🗺️

## 1. Introduction
This package converts Dataverse JSON metadata into RDF conforming to **DCAT‑AP‑NL 3.0**, the Dutch application profile on top of DCAT‑AP‑EU 3.0. It supports three usage modes:
- Full profile (Dataset + Distribution + Catalog + DataService)
- Download‑only profile (no DataService)
- Customised profiles using SHACL layer selection

## 2. Mapping Overview
Mappings are implemented as `.properties` files which define nodes, predicates, literal rules, and JSONPath extraction.
Files include:
- `dcat-root.properties`
- `dcat-dataset.properties`
- `dcat-distribution.properties`
- `dcat-dataservice.properties`
- `dcat-catalog.properties`

## 3. DCAT‑AP‑NL 3.0 and SHACL Layers
The Geonovum SHACL set contains multiple layers:
- EU baseline shapes
- NL baseline shapes
- KLassebereik + codelijst shapes
- Optionality shapes
- Recommended shapes (`dcat-ap-nl-SHACL-aanbevolen.ttl`)

The *recommended* layer enforces many DataService properties as SHACL `minCount` constraints. Run the complete set of SHACL files to validate output created with these .properties.

## 4. DataService in AP‑NL 3.0
`dcat:accessService` has cardinality `0..1` and optionality **A (Aanbevolen)**.
However, when the recommended SHACL layer is used, `dcat:DataService` becomes effectively mandatory and must include ~10 required properties (title, description, language, keyword, endpointURL, endpointDescription, creator, publisher, rights, contactPoint, etc.).

## 5. Download‑Only Mode
Download‑only environments (no WFS, WMS, SPARQL, OAI‑PMH, etc.) may omit `dcat:DataService`.
Steps:
- Remove or comment out from `dcat-root.properties`:
    - `element.dataservice.*`
    - `relation.dataset_has_service.*`
- Remove or comment out from `dcat-distribution.properties`:
    - `nodes.service.*`
    - `props.accessService.*`
- Omit `dcat-dataservice.properties`
- Exclude SHACL file `dcat-ap-nl-SHACL-aanbevolen.ttl` to validate this profile

## 6. Full Profile
Use this when Dataverse API should be represented as a DataService.
All DataService properties must be populated.

## 7. Testing Strategy
Two testcases:
### `DcatApNL30ComplianceTest`
Validates full mapping against AP‑NL baseline (and optionally recommended) SHACL.

### `DcatApNL30DownloadServiceOnlyTest`
Dynamically copies mapping files to a temp directory, removes DataService-related keys, loads modified config and validates download-only RDF against baseline SHACL.

## 8. Structure of Mapping Files
### `dcat-root.properties`
Defines element templates for Catalog, Dataset, Distribution, and optionally DataService.

### `dcat-dataset.properties`
Maps identifiers, titles, descriptions, landing pages, linguistic system, rights, license, themes, creators, publisher.

### `dcat-distribution.properties`
Maps filesize, mediaType, accessURL/downloadURL, rights, license, checksum, issued.
Includes optional Access Service block.

### `dcat-dataservice.properties`
Complete DataService metadata file (omit for download-only profiles).

### `dcat-catalog.properties`
Publisher, creator, contact, dataset linkage.

## 9. How to Extend
- Add additional vocab-mapped fields
- Add conditional DataService metadata
- Define more SKOS mappings
- Optionally include recommended SHACL as a quality gate

## 10. References
- DCAT v3 W3C
- DCAT‑AP‑EU 3.0
- DCAT‑AP‑NL 3.0 (Geonovum)
- Dataverse JSON structure
