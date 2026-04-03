# Issue 49: Dataset Access Rights Based on File Restrictions

## Problem Statement (From DANS)

The requirement is to determine Dataset-level `accessRights` based on whether any files in the dataset have restrictions:

- **If ANY file has `restricted=true`** → Dataset `accessRights` should be **RESTRICTED**
- **If NO files have `restricted=true`** → Dataset `accessRights` should be **PUBLIC**

This is a data governance concern where the most restrictive access level across all files should be reflected at the dataset level.

## Current Implementation in AP-NL 3.0

The current mappings handle this at different levels:

### Distribution-Level Rights (Per File)
**File: `dcat-distribution.properties` lines 80-88**
```properties
nodes.rights.kind      = iri
nodes.rights.type      = dct:RightsStatement
nodes.rights.iri.json  = $.restricted
nodes.rights.map.true  = http://publications.europa.eu/resource/authority/access-right/RESTRICTED
nodes.rights.map.false = http://publications.europa.eu/resource/authority/access-right/PUBLIC
```
✅ **Works well**: Each file's `restricted` boolean directly maps to RESTRICTED/PUBLIC

### Dataset-Level Access Rights
**File: `dcat-dataset.properties` lines 57-66**
```properties
nodes.ar.kind                = iri
nodes.ar.type                = dct:RightsStatement
nodes.ar.iri.json            = $..DCATMetadata.fields[?(@.typeName=='DCATaccessRights')].value
nodes.ar.map.public          = http://publications.europa.eu/resource/authority/access-right/PUBLIC
nodes.ar.map.restricted      = http://publications.europa.eu/resource/authority/access-right/RESTRICTED
```
⚠️ **Requires Administrator Configuration**: The value comes from the dataset's metadata field `DCATaccessRights`

## Architectural Decision: Admin Responsibility (Not New DSL)

### Why NOT Add `map_empty`/`map_nonempty` to the DSL?

1. **Keep the DSL Simple**: The mapping system should focus on data transformation, not business logic
2. **Data Governance Responsibility**: Determining which files are restricted and what that means for the dataset is a business/governance decision, not a technical mapping concern
3. **Existing Tools Suffice**: Dataverse already has metadata fields where admins can specify the correct access rights
4. **DCAT-AP-NL 3.0 Compliance**: The spec treats Dataset and Distribution access separately—they can have different access levels legitimately

### How Administrators Handle Issue 49

The solution requires **administrator responsibility** during curation/publication:

1. **Set DCATaccessRights Metadata**
   - When publishing a dataset with restricted files, the administrator/curator must explicitly set the `DCATaccessRights` field in the metadata
   - If ANY file is restricted → select "restricted"
   - If ALL files are public → select "public"

2. **Mapping Handles the Rest**
   - The mapping simply reads this metadata field
   - No new DSL needed
   - Clean separation: business logic (admin decision) vs. mapping (configuration)

## Test Case: Issue49DatasetAccessRightsTest

Location: `src/test/java/io/gdcc/spi/export/dcat3/Issue49DatasetAccessRightsTest.java`

### Test Data Structure

```
src/test/resources/input/issue_49_dataset_access_rights/
├── datasetFileDetails.json      (3 files: 2 public, 1 restricted)
├── datasetJson.json             (metadata with DCATaccessRights=restricted)
├── datasetORE.json
├── dataCiteXml.xml
└── datasetSchemaDotOrg.json
```

### Test Scenario

**Input**:
- 3 files in dataset:
  - `public_file.txt` → restricted=**false**
  - `restricted_file.pdf` → restricted=**true**
  - `another_public_file.csv` → restricted=**false**
- Admin configured metadata: `DCATaccessRights` = **"restricted"**

**Minimal Configuration** (issue-49-root.properties, issue-49-dataset.properties, issue-49-distribution.properties):
- Stripped of irrelevant AP-NL 3.0 mappings
- Shows only essential properties for this use case
- Demonstrates the pattern

**Expected Output**:
```
✅ Dataset dct:accessRights = RESTRICTED
   (Because admin correctly set the metadata to match file restrictions)

✅ Distribution 1 (file 1): dct:rights = PUBLIC
✅ Distribution 2 (file 2): dct:rights = RESTRICTED  
✅ Distribution 3 (file 3): dct:rights = PUBLIC
   (Each file's restricted flag determines its own rights)
```

### Test Methods

1. **`testIssue49MixedFileRestrictionsMapping()`**
   - Verifies the minimal configuration works correctly
   - Checks that dataset and distributions have correct access rights
   - Validates that 1 file is restricted, 2 are public
   - Confirms relationships between dataset and distributions

2. **`testIssue49DocumentationOfAdminResponsibility()`**
   - Documents the architectural decision
   - Explains why this is admin responsibility, not DSL extension
   - References the AP_NL30 configuration to show the pattern

## Running the Test

```bash
# Run just the Issue 49 tests
mvn test -Dtest=Issue49DatasetAccessRightsTest

# Run all tests (112 total)
mvn test
```

## Evidence & Justification

### Why This Approach is Correct

1. **DCAT-AP-NL 3.0 Specification**
   - Treats `dcat:Dataset` and `dcat:Distribution` as separate concerns
   - `dct:accessRights` is a dataset-level property (user/admin configured)
   - `dct:rights` is a distribution-level property (often derived per-file)

2. **Data Governance Best Practice**
   - Access determination is a business/policy decision
   - Admins have metadata fields to express this decision
   - Automation should map what's configured, not override with logic

3. **Current AP-NL 3.0 Evidence**
   - Distribution rights are automatic (file's `restricted` boolean)
   - Dataset rights are manual (admin sets `DCATaccessRights`)
   - This pattern has been in place since AP-NL30 implementation

4. **Extensibility**
   - If organizations need automatic aggregation logic later, they can:
     - Add it at the Dataverse level (before DCAT export)
     - Add it as a separate DSL extension (not breaking the mapping system)
     - Use organizational policies to enforce admin responsibilities

## Conclusion

Issue 49 is properly handled by placing responsibility on the **administrator/curator** to configure dataset metadata correctly, reflecting the actual access restrictions in their data. The mapping system faithfully outputs what has been configured, maintaining clean separation between business logic and configuration-driven mapping.

**No new DSL extensions required** - the existing mapping system is sufficient, and admin responsibility ensures correct data governance practices.

