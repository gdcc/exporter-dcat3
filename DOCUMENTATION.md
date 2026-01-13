
# DCAT-3 Export Properties Mechanism — Quick Reference

This document describes the declarative properties/config files used by the DCAT-3 exporter.
It covers **root config**, **resource config** (elements), **scoping**, **value sources**, and **formatting**.

## 1. Root config (`dcat-root.properties`)
Defines output format, prefixes, elements, and relations.

```properties
trace.enabled = false

# Prefixes for CURIEs used in configs
prefix.dcat   = http://www.w3.org/ns/dcat#
prefix.dct    = http://purl.org/dc/terms/
prefix.foaf   = http://xmlns.com/foaf/0.1/
prefix.vcard  = http://www.w3.org/2006/vcard/ns#
prefix.skos   = http://www.w3.org/2004/02/skos/core#
prefix.rdfs   = http://www.w3.org/2000/01/rdf-schema#
prefix.xsd    = http://www.w3.org/2001/XMLSchema#
prefix.spdx   = http://spdx.org/rdf/terms#

# Elements (each loads its own resource config file)
element.catalog.id    = catalog
element.catalog.type  = dcat:Catalog
element.catalog.file  = dcat-catalog.properties

element.dataset.id    = dataset
element.dataset.type  = dcat:Dataset
element.dataset.file  = dcat-dataset.properties

element.distribution.id   = distribution
element.distribution.type = dcat:Distribution
element.distribution.file = dcat-distribution.properties

# Relations between element subjects
relation.catalog_has_dataset.subject     = catalog
relation.catalog_has_dataset.predicate   = dcat:dataset
relation.catalog_has_dataset.object      = dataset
relation.catalog_has_dataset.cardinality = 0..n

relation.dataset_has_distribution.subject     = dataset
relation.dataset_has_distribution.predicate   = dcat:distribution
relation.dataset_has_distribution.object      = distribution
relation.dataset_has_distribution.cardinality = 0..n
```
### trace option
The trace option can be used to trace the internal data received from Dataverse so that proper JSON queries can be defined.

### harvestable vs availableToUsers

* This exporter provides DCAT serializations in **RDF/XML**, **Turtle**, and **JSON‑LD**. 
* **Dataverse harvesters only support XML formats**, therefore only the RDF/XML variant is harvestable.
* For **Turtle** and **JSON‑LD**, the harvestable property is ignored and effectively overridden to false, regardless of its value in dcat-root.properties.
* The `availableToUsers` flag only controls visibility in the Dataverse UI: when set to true, the format will appear in the Metadata → Export menu for manual export by users.

Example (effective behavior):
```properties

dcat.format.rdfXml.availableToUsers = true
dcat.format.rdfXml.harvestable     = true

dcat.format.turtle.availableToUsers = true
dcat.format.turtle.harvestable      = false   # ignored/overridden

dcat.format.jsonLd.availableToUsers = true
dcat.format.jsonLd.harvestable      = false   # ignored/overridden

```
### relation

The relations describe which entities are relevant in the application profile. Each of the entities can have a file describing that entity.

## 2. Resource config (e.g., `dcat-distribution.properties`)
Controls how to build a **resource model** (subjects, properties, nodes).

### 2.1 Scope
Use `scope.json` to **iterate** over parts of the input JSON:

```properties
# Iterate over each file
scope.json = $.datasetFileDetails[*]
```

> If you accidentally use `$.datasetFileDetails` (no `[*]`), the mapper will auto-iterate the array.

### 2.2 Subject
Define the resource subject IRI:

```properties
# Mint an IRI per file id
subject.iri.json   = $.id
subject.iri.format = https://dataverse.nl/distribution/${value}
```

### 2.3 Properties (ValueSource)
Each `props.<id>.*` block describes **one property**. Supported keys:

- `predicate` – CURIE/IRI of the predicate (resolved against prefixes)
- `as` – `literal` | `iri` | `node-ref`
- `lang` – language tag for literals (optional)
- `datatype` – datatype IRI (CURIE allowed), for typed literals (optional)
- `json` – JSONPath to read a value (supports `$$` for **root** lookup)
- `json.N` – **indexed** JSONPaths; allows composition in `format` using `${1}`, `${2}`, …
- `const` – constant value
- `map.*` – mapping table (e.g., `map.python = text/x-python`)
- `format` – template to compose values. Supports:
  - `${value}` – the base value (from the stream or `json`)
  - `${1}`, `${2}`, … – from `json.1`, `json.2`, …
  - **inline JSONPath** placeholders: `${$.path}` or `${$$.path}`
- `multi` – `true` to emit multiple values from a multi-match JSONPath
- `node` – **node id** for `as=node-ref` (see nodes below)
- `when` – future conditional emission (reserved)

**Examples**

```properties
# Literal title taken from file name
props.title.predicate = dct:title
props.title.as        = literal
props.title.lang      = en
props.title.json      = $.filename

# Typed literal (byte size)
props.byteSize.predicate = dcat:byteSize
props.byteSize.as        = literal
props.byteSize.datatype  = xsd:nonNegativeInteger
props.byteSize.json      = $.filesize

# Media type literal
props.mediaType.predicate = dcat:mediaType
props.mediaType.as        = literal
props.mediaType.json      = $.contentType

# Dataset access URL read from the global root (not per file)
props.accessURL.predicate = dcat:accessURL
props.accessURL.as        = iri
props.accessURL.json      = $$.datasetJson.persistentUrl

# Email IRI using format
nodes.contact.props.email.predicate = vcard:hasEmail
nodes.contact.props.email.as        = iri
nodes.contact.props.email.json      = $..metadataBlocks.citation.fields[?(@.typeName=='datasetContact')].value[0].datasetContactEmail.value
nodes.contact.props.email.format    = mailto:${value}

# Version composed from two JSON paths
props.hasVersion.predicate = dct:hasVersion
props.hasVersion.as        = literal
props.hasVersion.json.1    = $$.datasetJson.datasetVersion.versionNumber
props.hasVersion.json.2    = $$.datasetJson.datasetVersion.versionMinorNumber
props.hasVersion.format    = V${1}.${2}

# Alternate one-liner using inline JSONPaths
# props.hasVersion.format = V${$$.datasetJson.datasetVersion.versionNumber}.${$$.datasetJson.datasetVersion.versionMinorNumber}
```

### 2.4 Nodes
Use `nodes.<id>.*` to describe embedded nodes for `as=node-ref`:

```properties
# checksum node
props.checksum.predicate = spdx:checksum
props.checksum.as        = node-ref
props.checksum.node      = checksum

nodes.checksum.kind = bnode               # or "iri" with nodes.checksum.iri.const
nodes.checksum.type = spdx:Checksum
nodes.checksum.props.checksumValue.predicate = spdx:checksumValue
nodes.checksum.props.checksumValue.as        = literal
nodes.checksum.props.checksumValue.json      = $.checksum.value
```

## 3. Root vs scoped JSONPath
- `$...` – evaluated against the **current scope** (e.g., the file object in `datasetFileDetails[*]`).
- `$$...` – evaluated against the **original document root**.

## 4. Serialization caveats
- RDF/XML requires **absolute IRIs**. Use `format` (e.g., `mailto:${value}`) to make email addresses valid IRIs.
- Turtle will show typed literals with quotes (e.g., `"4026"^^xsd:nonNegativeInteger`). This is correct.

## 5. Troubleshooting
- If a JSONPath fails, enable tracing and check the **scope** you are in; ensure you use `$` vs `$$` appropriately.
- When linking elements (dataset → distribution), ensure the **subjects are minted** (absolute IRIs) and relations are applied after model merging.

## 6. Validation

The following validations are carried out:

### Prefixes

- Empty prefix keys / invalid IRIs → ERROR
- Missing prefixes → WARNING


### Elements

- Missing id, typeCurieOrIri, file → ERROR
- typeCurieOrIri not CURIE/IRI or unknown CURIE prefix → ERROR


### Relations

- Missing subject/object/predicate → ERROR
- Predicate not CURIE/IRI or unknown prefix → ERROR


### Subject

- No minting strategy at all (const/template/json) → WARNING
- iriFormat provided without template or json → ERROR


### ValueSource

- Missing predicate → ERROR
- Bad as value → ERROR
- node-ref without nodeRef → ERROR
- No source (json|const|json.*|node) → WARNING


### NodeTemplate

- Empty id → ERROR
- kind must be bnode or iri → ERROR
- type must be CURIE/IRI; check prefixes → ERROR

---

*This mechanism is designed to be declarative, composable, and profile-friendly for DCAT/DCAT‑AP exports.*
