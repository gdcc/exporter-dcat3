# DCAT‑AP.de 3.0 Mapping Package for Dataverse

This profile maps Dataverse metadata to the German [DCAT‑AP.de 3.0](https://www.dcat-ap.de/def/dcatde/3.0/spec/) application profile. It currently describes a `Catalog`, `Dataset`s, and downloadable `Distribution`s.

## Mapping files

The `mapping/` directory contains:

- `dcat-root.properties`: prefixes, format names, entities, and relationships.
- `dcat-catalog.properties`: the catalog IRI, title, description, publisher, and contact point.
- `dcat-dataset.properties`: dataset identifiers, titles, descriptions, dates, landing page, contact point, creators, and publisher.
- `dcat-distribution.properties`: per-file subjects, access URLs, license, size, media type, and SPDX checksums.

## License mapping

The `Distribution` mapping uses the Dataverse license URI as its primary input and maps known licenses to the [allowed DCAT-AP.de license URIs](https://www.dcat-ap.de/def/licenses/). Unknown licenses fall back to `http://dcat-ap.de/def/licenses/other-closed`.

The mapping currently includes CC0, CC BY 4.0, CC BY-SA 4.0, and CC BY-NC 4.0. Add further URI mappings as required.

## Output formats

The profile provides configured names for all three exporter formats:

- `DCAT-AP.de (RDF/XML)`
- `DCAT-AP.de (Turtle)`
- `DCAT-AP.de (JSON-LD)`

## Validation

The test suite validates the profile against the [official DCAT-AP.de 3.0 validator](https://github.com/GovDataOfficial/DCAT-AP.de-SHACL-Validation) shape set.

Run the tests with:

```bash
mvn test -Dtest="DcatApDE30ComplianceTest"
```

The test fetches the upstream validator shapes, so it is skipped when the test is run offline.

## References

- [DCAT‑AP.de 3.0 specification](https://www.dcat-ap.de/def/dcatde/3.0/spec/)
- [DCAT‑AP.de license list](https://www.dcat-ap.de/def/licenses/)
- [DCAT‑AP.de SHACL validation repository](https://github.com/GovDataOfficial/DCAT-AP.de-SHACL-Validation/tree/master/validator/resources/v3.0/shapes)
