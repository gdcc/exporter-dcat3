
# DCAT‑3 Exporter for Dataverse — DCAT‑AP‑NL 3.0

> **Status:** Prototype with automated validation. Produces RDF for DCAT 3.0 and DCAT‑AP‑NL 3.0.

This exporter turns Dataverse metadata into DCAT‑compliant RDF using a **declarative mapping** in `.properties` files. It is configuration‑driven, supports multiple RDF serializations, and is built to be validated automatically (SHACL) in CI.

- **Formats:** Turtle (`text/turtle`), JSON‑LD (`application/ld+json`), RDF/XML (`application/rdf+xml`).
- **Dataverse:** External exporter plugin for Dataverse 6.x (best on 6.2+).
- **Profiles:** DCAT 3.0 and DCAT‑AP‑NL 3.0.

For in‑depth details, see:
- **Documentation (properties, JSONPath, nodes, validation rules):** [DOCUMENTATION.md](DOCUMENTATION.md)
- **Design (architecture, loaders, mapping, writers):** [DESIGN.md](DESIGN.md)

---

## Installation

1. **Build**
   ```bash
   mvn clean package
   ```
2. **Install exporter JAR** into the Dataverse SPI exporters directory and restart Payara.
    - As a JVM option (non‑Docker):
      ```xml
      <jvm-options>-Ddataverse.spi.exporters.directory=/path/to/exporters</jvm-options>
      ```
    - Docker (compose):
      ```yaml
      environment:
        DATAVERSE_SPI_EXPORTERS_DIRECTORY: "/dv/exporters"
      # copy your exporter JAR into that directory
      ```
3. On a published dataset, go to **Metadata → Export Metadata** and choose **DCAT3**.

> Batch exports: use the Admin API (`/api/admin/metadata/reExportAll` or `.../reExportDataset?persistentId=...`).

---

## Configuration (where to put the `.properties` files)

The exporter reads mapping files such as:

- `dcat-root.properties`
- `dcat-dataset.properties`
- `dcat-distribution.properties`
- `dcat-dataservice.properties`
- `dcat-catalog.properties`

This plugin builds a DCAT entity-relational model based on:
1. dcat-root.properties – defines the core DCAT structure.
2. Entity-specific .properties files – each file represents a DCAT entity and its relationships.

Under the application_profiles directory, you will find several ready-to-use profiles:

### Lightweight Application Profile
A minimal configuration that does not require additional metadata. Can act as a base for own / further development.

### [Dutch National Application Profile (DCAT-AP-NL 3.0)](https://docs.geostandaarden.nl/dcat/dcat-ap-nl30/)
The official Dutch profile, including mappings for all mandatory fields. This is completed with the Tab Separated Value definition of required additional fields.

### Why Application Profiles?
Application profiles define how DCAT is applied in a specific context—national, sectoral, or domain-specific. They ensure interoperability and consistency when publishing metadata.

### How to Contribute: 
We welcome contributions of national, sectoral, or other reusable application profiles. To add a new profile:

Create a new subdirectory under application_profiles/ (e.g., application_profiles/<profile_name>).
Add a mapping/ folder containing:
* dcat-root.properties - describing the structure
* One .properties file per DCAT entity (e.g., dcat-catalog.properties, dcat-dataset.properties, etc.).

Include: 
* a short README.md in the profile directory with:
* The purpose and context of the profile.
* A link to the official specification (if available).

Example Structure:
```text
application_profiles/
   my-profile/
      mapping/
         dcat-root.properties
         dcat-catalog.properties
         dcat-dataset.properties
      my-profile.tsv - definition of additional metadata block(s)
      my-profile.properties - language bundle(s)   
   ...
   README.md
```

> **Location:** place all mapping files in a directory pointed to the JVM system property:
>
> ```java
> public static final String SYS_PROP = "dataverse.dcat3.config";
> ```
>
> Configure it similar to the exporters' directory:
>
> ```xml
> <jvm-options>-Ddataverse.dcat3.config=/path/to/dcat3-config/dcat-root.properties</jvm-options>
> ```
>
> In containers, you can inject the same as a Java option (e.g., via `PAYARA_JAVA_OPTS` or your runtime’s JVM opts mechanism). The exporter resolves files from this directory first and may fall back to classpath resources if not found.

**What’s inside the configs** (brief):
- **Root**: prefixes, element list, relations, optional tracing.
- **Per‑element**: subject minting, properties (`literal | iri | node-ref`), nodes, JSONPath sources, mappings, datatypes, lang tags, and multi‑value emission.
- **Relations**: n:m links between element subjects applied after model merge.

See [DOCUMENTATION.md](DOCUMENTATION.md) for the full reference.
See also [this](application_profiles/AP_NL30/README.md) example for a mapping example.

---

## Running

### From the UI
- **Metadata → Export Metadata → DCAT3** on a published dataset.

### From the API (admin)
```bash
curl http://<host>:8080/api/admin/metadata/reExportAll
curl "http://<host>:8080/api/admin/metadata/:persistentId/reExportDataset?persistentId=doi:10.5072/FK2/ABC123"
```

### Output formats
Serializer is selected by the exporter variant you choose (Turtle, JSON‑LD, RDF/XML). All share the same mapping logic; only the writer changes.

---

## Validation & Testing

This project includes automated tests to keep mappings and output compliant:

- **Unit tests**
    - Build the RDF model(s) from fixture JSON using the mapping files.
    - Run **SHACL validation** against DCAT‑AP / DCAT‑AP‑NL shape graphs.
    - Assert zero violations (warnings may be allowed per profile policy).

- **Integration test (example)**
    - Executes the full export for a representative dataset and validates the combined graph against **DCAT‑AP‑NL 3.0**.

- **How to run**
  ```bash
  mvn test
  # optional helper
  ./validate.sh   # if provided, validates produced RDF artifacts
  ```

Place SHACL shapes under `src/test/resources/shacl/` and wire them in the tests. See **Documentation** for guidance on typical violations and how to fix them in mapping.

---

## Development workflow

- **Edit mapping**: change your `*.properties` files under the config directory and re‑run tests.
- **Update expected outputs**: if tests compare RDF to fixtures, use your helper script (e.g., `update-expected.sh`) to refresh them after intended changes.
- **Coding style**:
  ```bash
  mvn spotless:apply
  mvn pomchecker:check-maven-central
  ```
- **Packaging**:
  ```bash
  mvn package
  ```

---

## Troubleshooting

- **Exporter not visible in Dataverse**
    - Confirm the JAR is in `dataverse.spi.exporters.directory` and restart Payara.
    - Check server logs for SPI discovery messages.

- **“node‑ref requires nodeRef/node” errors**
    - In your property block, `as=node-ref` requires a `node=<nodeId>` or a configuration pattern that the validator recognizes as a node reference. Review the corresponding `nodes.<id>.*` template and ensure it declares `kind` and `type` correctly.

- **IRIs vs literals**
    - Ensure email/URLs are emitted as *absolute IRIs* (e.g., `mailto:${value}`) especially for RDF/XML.

- **JSONPath surprises**
    - Use `$.…` for the current scope (e.g., iterated file item) and `$$.…` to reach the original root. Enable tracing if available in your root config.

---

## Roadmap / Extensibility

- Additional DCAT elements and relations can be added by extending the root config and adding new per‑element files.
- New output formats can be added by subclassing the exporter base and wiring a different Jena writer.

See [DESIGN.md](DESIGN.md) for the architectural split (Loaders → Mapping → Validation → Writing) and SPI registration details.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

---

## License

See [LICENSE](LICENSE).

---

## References

- Dataverse external exporters & admin guides (exporters directory, re‑export API)
- DCAT‑AP 3.0 & DCAT‑AP‑NL 3.0 (specs & SHACL)

