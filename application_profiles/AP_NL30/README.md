# DCAT‑AP‑NL 3.0 Mapping Package for Dataverse (GDN)

This package contains mapping configurations to convert Dataverse JSON into **DCAT‑AP‑NL 3.0–compliant RDF**. It covers Dataset, Distribution, DataService, and Catalog metadata. 🗺️

---

⚠️ **Point of Attention**: The mapping to the Dutch AP-NL-3.0 profile is currently incomplete. It does not include all optional or recommended fields, as it has been tailored to the specific user 
context. The intention is to remove any fields and TSV entries that do not apply to your organization. Additional relevant DCAT-AP-NL 3.0 mappings can, of course, be added over time.


## Files Included
- **dcat-root.properties**: Defines prefixes, DCAT elements, and structural relations.
- **dcat-dataset.properties**: Maps Dataverse dataset-level metadata to DCAT fields.
- **dcat-distribution.properties**: Maps file-level metadata to DCAT distributions.
- **dcat-dataservice.properties**: Defines and populates the DataService node for the Dataverse API.
- **dcat-catalog.properties**: Maps catalog-wide metadata including contact and publisher.

---

## How the Mapping Works

### 1. dcat-root.properties
Defines global prefixes, including DCAT, DCT, FOAF, VCARD, SKOS, LOCN, GeoSPARQL, SPDX, RDF, OWL, and ELI. Configures element declarations and their relationships—e.g., linking datasets to catalogs and services.

### 2. dcat-dataset.properties
Translates dataset metadata into key DCAT entities:
- **Identifiers**: DOI mapped to `dct:identifier`.
- **Title/Description**: Uses language-tagged literals (`en`).
- **Landing pages**: Typed as `foaf:Document` (`dcat:landingPage`, `foaf:page`).
- **Language**: Defaults to `ENG` via `dct:LinguisticSystem`.
- **Conformance**: Adds a `dct:Standard` node for `dct:conformsTo`.
- **Access Rights**: Typed as `dct:RightsStatement`.
- **Themes**: Mapped to SKOS Concepts with local `skos:prefLabel` to satisfy SHACL.
- **Creators/Publisher**: Modeled as FOAF Agents; ROR IDs used when available.
- **High-Value Data (HVD) & Legislation**:
   - **Latest Fix**: HVD categories now modeled as **typed SKOS Concepts** using `nodes.hvd` and `props.hvdCategory.node = hvd`. This satisfies:
      - Exporter validation (`node-ref requires nodeRef`).
      - SHACL class constraint (`skos:Concept` expected for `dcatap:hvdCategory`).
   - Maps Dataverse values to official EU HVD category IRIs and attaches local `skos:prefLabel` for offline SHACL validation.
- **Spatial Metadata**: Maps geospatial coverage to `dct:spatial` → `dct:Location` → `locn:Address` → structured address components; optional geometry nodes included.

### 3. dcat-distribution.properties
Translates file metadata into DCAT distributions:
- **Byte size**: `dcat:byteSize` as `xsd:nonNegativeInteger`.
- **Media type**: IANA type mapped to `dct:MediaType`.
- **Access/download URLs**: Typed as `rdfs:Resource`.
- **Rights & License**: Typed per DCT standards.
- **Service**: Links to the API DataService node.
- **Checksum**: Creates a SPDX `Checksum` node with a typed algorithm and hexBinary value. The algorithm is modeled as a blank node with `owl:sameAs` to standard IRIs, satisfying SHACL constraints.

### 4. dcat-dataservice.properties
Captures metadata about the Dataverse API:
- **Title/Description**: Human-readable info.
- **Identifier**: API base URL.
- **Endpoint**: `dcat:endpointURL` as `rdfs:Resource`.
- **Endpoint Description**: Also typed `rdfs:Resource`.
- **Language, Keywords, Access Rights, Theme, License**: Satisfy DCAT‑AP‑NL minCard constraints.
- **Creator, Publisher, Contact**: Modeled similarly to dataset.

### 5. dcat-catalog.properties
Maps the catalog to DCAT:
- **Contact**: vCard `Kind` with full name, email, and URL.
- **Publisher**: FOAF Agent with ROR linking and multilingual names.
- **Creator**: Added as a FOAF Agent, meeting DCAT‑AP‑NL requirements.

---

## Geometry & Spatial Coverage 🚧
Spatial coverage is mapped to addresses via `locn:Address`. Optionally, geometry support creates a `locn:Geometry` node with WKT via `geo:asWKT`, but spatial mapping is **not complete**. Additional forms like bounding boxes or circles are not currently implemented.

### Dataverse Spatial Capabilities
Dataverse supports:
- **Bounding boxes** (min/max lat/lon), subject to validation rules.
- Possibly **point geometry**, but circle/centerpoint models are not clearly supported in the JSON metadata.

This template lays the foundation for geometry support, but further enhancements—like parsing bounding boxes into GeoSPARQL or LOCN geometries, or converting circles into WKT—will require additional logic.

---

## Usage & Next Steps
1. Run your Dataverse export pipeline with JSON input.
2. Apply these mapping files in your exporter to generate DCAT‑AP‑NL RDF.
3. Validate using Geonovum’s SHACL shapes.
4. Review logs and ensure no SHACL violations (geometry is optional).
5. To enhance spatial coverage:
   - Add JSONPath mappings for bounding-box fields.
   - Convert bounding-box values to WKT Polygons (`geo:asWKT`).
   - Expand mapping for circle/center-point data if present.

---

## Latest Updates
- **HVD Category Fix**:
   - Implemented dynamic node approach (`nodes.hvd`) typed as `skos:Concept`.
   - Added `props.hvdCategory.node = hvd` for exporter compliance.
   - Attached local `skos:prefLabel` for SHACL validation without remote fetch.
- **Validation**:
   - Exporter error `DCATRSC-105` resolved.
   - SHACL class constraint for `skos:Concept` satisfied.
- **Alternative Configs**:
   - Option A (nodeRef flag) and Option B (IRI-only) provided for reference, but **Option C (typed node-ref)** is recommended for full compliance.

---

## References
- **DCAT v3 (W3C)**: structure and modeling of DataService, Location, etc.
- **DCAT‑AP 3.0 (SEMIC)**: thematic profile and NL constraints.
- **LOCN Vocabulary**: for structured address modeling.
- **GeoSPARQL**: WKT geometry modeling.
- **SPDX RDF**: checksum typing and algorithm identity.
- **Dataverse Geospatial**: bounding box validation insights.
