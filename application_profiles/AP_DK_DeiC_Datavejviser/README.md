# DCAT‑AP-DK Mapping for Datavejviser
This package contains mapping configurations to convert JSON metadata from the Danish national [DeiC Dataverse](https://dataverse.deic.dk/) installation into **DCAT‑AP‑DK 3.0–compliant XML/RDF** for transferring metadata to the national [Datavejviser](https://datavejviser.dk/) public data catalog. It covers Dataset, Distribution, and Catalog metadata. DataService is intentionally not modeled; see below. 🗺️

⚠️ **Point of Attention**: The mapping to the Danish AP-DK-3.0 profile is currently incomplete. It does not include all optional or recommended fields, as it has been tailored to the specific user context. The intention is to remove any fields and TSV entries that do not apply to your organization. Additional relevant DCAT-AP-NL 3.0 mappings can, of course, be added over time.


## Files Included
- **dcat-root.properties**: Defines prefixes, DCAT elements, and structural relations.
- **dcat-dataset.properties**: Maps Dataverse dataset-level metadata to DCAT fields.
- **dcat-distribution.properties**: Maps file-level metadata to DCAT distributions.
- **dcat-catalog.properties**: Maps catalog-wide metadata including contact and publisher.

> **No DataService**: this deployment does not expose the Dataverse API as a `dcat:DataService`. `element.dataservice.*` and `relation.dataset_has_service.*` are omitted from `dcat-root.properties`, so no DataService node is built or linked.

## How the Mapping Works

### 1. dcat-root.properties
Defines global prefixes, including DCAT, DCT, FOAF, VCARD, SKOS, LOCN, GeoSPARQL, SPDX, RDF, OWL, and ELI. Configures element declarations and their relationships—e.g., linking datasets to catalogs and distributions.

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
- **Checksum**: Creates a SPDX `Checksum` node with a typed algorithm and hexBinary value. The algorithm is modeled as a blank node with `owl:sameAs` to standard IRIs, satisfying SHACL constraints.

### 4. dcat-catalog.properties
Maps the catalog to DCAT:
- **Contact**: vCard `Kind` with full name, email, and URL.
- **Publisher**: FOAF Agent with ROR linking and multilingual names.
- **Creator**: Added as a FOAF Agent, meeting DCAT‑AP‑NL requirements.
