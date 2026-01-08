# Lightweight DCAT‑AP‑NL Mapping Files for Dataverse
This set of files provides simplified mappings from Dataverse JSON to DCAT, focusing on essential metadata for Catalog, Dataset, and Distribution. These configurations are designed for minimal compliance and easy customization.

⚠️ Point of Attention
These mappings are lightweight and do not include all optional or recommended DCAT‑AP‑NL fields. They are intended as a starting point for organizations that need a basic export. You can extend them by adding additional properties or nodes as required.

Files Included
## 1. dcat-catalog.txt
   Defines the Catalog node and its core properties:

IRI: Fixed to the Dataverse catalog URL.
Title & Description: Provided in English.
Publisher: Modeled as a foaf:Agent with multilingual names and ROR ID.
Contact Point: vCard node with name, email, and URL.


## 2. dcat-dataset.txt
   Maps Dataset-level metadata:

Identifier: Persistent URL.
Title & Description: Extracted from citation metadata.
Versioning: Combines major/minor version numbers.
Issued & Modified Dates: Typed as xsd:date and xsd:dateTime.
Landing Page: Persistent URL.
Contact Point: vCard node with name and email.
Creators: FOAF Persons with name and affiliation.


## 3. dcat-distribution.txt
   Maps File-level metadata into DCAT Distributions:

Title & Description: Based on filename and description.
Byte Size: Typed as xsd:integer.
Media Type: IANA type as IRI.
Access URL: Dataset persistent URL.
Rights: PUBLIC or RESTRICTED mapped to EU Access Rights IRIs.
License: URI from dataset version.
Checksum: SPDX node with algorithm and value.


## Usage

Integrate these mappings into your Dataverse export pipeline.
Apply them to generate RDF for Catalog, Dataset, and Distribution.
Validate against DCAT‑AP‑NL SHACL shapes.
Extend mappings as needed for additional compliance.


## Next Steps

Add support for DataService and advanced geometry if required.
Include multilingual labels and optional fields for full DCAT‑AP‑NL coverage.
Consider adding themes, keywords, and conformance nodes for richer metadata.


