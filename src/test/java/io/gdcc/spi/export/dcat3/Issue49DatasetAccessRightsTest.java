package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static org.junit.jupiter.api.Assertions.*;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.dcat3.config.loader.RootConfigLoader;
import io.gdcc.spi.export.util.TestUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/**
 * Aggregate Access Control Integration Test
 * -
 * Demonstrates the administrator responsibility pattern for handling dataset-level
 * access rights when distributing files have different restriction levels.
 * -
 * See: DOCUMENTATION.md section 7 "Real-World Example: Aggregate Access Control"
 * See: ISSUE_49_SOLUTION.md for detailed technical explanation
 *
 * SCENARIO: Dataset with mixed file restrictions
 * - Some files have restricted=false (public)
 * - Some files have restricted=true (restricted)
 * _
 * EXPECTED BEHAVIOR:
 * - Each Distribution's dct:rights reflects its individual restricted status (automatic)
 * - Dataset's dct:accessRights is configured by administrator via metadata (manual)
 * -
 * APPROACH: Administrator responsibility
 * - Distribution rights are automatic per-file mapping (restricted boolean)
 * - Dataset rights are manual via metadata field (DCATaccessRights)
 * - Admin must ensure metadata reflects the most restrictive access level
 * - No additional DSL required; mapping-only solution
 * - Pattern aligns with DCAT-AP-NL 3.0 separation of concerns
 */
class Issue49DatasetAccessRightsTest {

    @Test
    void testIssue49MixedFileRestrictionsMapping() throws Exception {
        // Set up configuration
        URL dcatRootPropertiesUrl =
                getClass().getClassLoader().getResource("mapping/issue_49_dataset_access_rights/issue-49-root.properties");
        assertNotNull( dcatRootPropertiesUrl, "Configuration file not found" );

        // Load test data with mixed file restrictions
        ExportDataProvider provider = getExportDataProvider("src/test/resources/input/issue_49_dataset_access_rights");
        assertNotNull( provider, "Export data provider could not be created" );

        // Set system property for configuration - convert URL to proper file path
        String originalProp = System.getProperty(RootConfigLoader.SYS_PROP);
        try {
            // Use toURI() and then toString to handle Windows paths correctly
            String configPath = new File(dcatRootPropertiesUrl.toURI()).getAbsolutePath();
            System.setProperty(RootConfigLoader.SYS_PROP, configPath);

            // Create exporter and build model
            Dcat3ExporterTurtle exporter = new Dcat3ExporterTurtle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.exportDataset(provider, out);

            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0, "Exporter should write Turtle bytes");

            // Parse the exported RDF
            Model model = TestUtil.readModel(bytes, Lang.TURTLE);
            assertNotNull( model, "Model should not be null" );

            // Verify Dataset exists
            ResIterator datasets = model.listResourcesWithProperty(RDF.type, DCAT.Dataset);
            assertTrue(datasets.hasNext(), "Should have at least one Dataset");
            Resource dataset = datasets.next();
            assertNotNull(dataset);

            // VERIFY: Dataset has accessRights set to RESTRICTED
            // This is because admin correctly configured metadata to reflect
            // the fact that at least one file is restricted
            assertTrue(dataset.hasProperty(DCTerms.accessRights), "Dataset should have dct:accessRights property");
            Resource datasetAccessRights =
                    dataset.getProperty(DCTerms.accessRights).getResource();
            assertTrue(
                    datasetAccessRights.getURI().contains("RESTRICTED"),
                    "Dataset accessRights should be RESTRICTED (admin configured this correctly to match file restrictions)");

            // Verify Distributions exist (one per file)
            ResIterator distributions = model.listResourcesWithProperty(RDF.type, DCAT.Distribution);
            assertTrue(distributions.hasNext(), "Should have at least one Distribution");

            int distributionCount = 0;
            int restrictedDistributions = 0;
            int publicDistributions = 0;

            while (distributions.hasNext()) {
                distributionCount++;
                Resource distribution = distributions.next();

                // Each distribution should have rights
                assertTrue(
                        distribution.hasProperty(DCTerms.rights),
                        "Distribution " + distributionCount + " should have dct:rights");
                Resource distRights = distribution.getProperty(DCTerms.rights).getResource();

                if (distRights.getURI().contains("RESTRICTED")) {
                    restrictedDistributions++;
                } else if (distRights.getURI().contains("PUBLIC")) {
                    publicDistributions++;
                }
            }

            // Verify we have 3 distributions (3 files)
            assertEquals(3, distributionCount, "Should have 3 distributions (one per file)");

            // Verify we have mixed restrictions: 1 restricted, 2 public
            assertEquals(1, restrictedDistributions, "Should have 1 restricted distribution");
            assertEquals(2, publicDistributions, "Should have 2 public distributions");

            // Verify dataset is linked to distributions
            assertTrue(dataset.hasProperty(DCAT.distribution), "Dataset should have dcat:distribution property");
        } finally {
            if (originalProp != null) {
                System.setProperty(RootConfigLoader.SYS_PROP, originalProp);
            } else {
                System.clearProperty(RootConfigLoader.SYS_PROP);
            }
        }
    }

    @Test
    void testIssue49GreenfieldScenarioWithMetadataAccessRights() throws Exception {
        // Greenfield scenario: administrator explicitly sets DCATaccessRights to PUBLIC
        // Files are all public -> Dataset accessRights should be PUBLIC (from metadata, not derived)
        URL dcatRootPropertiesUrl =
                getClass().getClassLoader().getResource("mapping/issue_49_dataset_access_rights/issue-49-root.properties");
        assertNotNull( dcatRootPropertiesUrl, "Configuration file not found" );

        ExportDataProvider provider = getExportDataProvider("src/test/resources/input/issue_49_greenfield_metadata");
        assertNotNull( provider, "Export data provider could not be created" );

        String originalProp = System.getProperty(RootConfigLoader.SYS_PROP);
        try {
            String configPath = new File(dcatRootPropertiesUrl.toURI()).getAbsolutePath();
            System.setProperty(RootConfigLoader.SYS_PROP, configPath);

            Dcat3ExporterTurtle exporter = new Dcat3ExporterTurtle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.exportDataset(provider, out);

            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0, "Exporter should write Turtle bytes");

            // Parse the exported RDF
            Model model = TestUtil.readModel(bytes, Lang.TURTLE);

            ResIterator datasets = model.listResourcesWithProperty(RDF.type, DCAT.Dataset);
            assertTrue(datasets.hasNext(), "Should have at least one Dataset");
            Resource dataset = datasets.next();

            // Verify: Dataset accessRights is PUBLIC (from metadata, greenfield mode)
            assertTrue(dataset.hasProperty(DCTerms.accessRights), "Dataset should have dct:accessRights property");
            Resource datasetAccessRights = dataset.getProperty(DCTerms.accessRights).getResource();
            assertTrue(
                    datasetAccessRights.getURI().contains("PUBLIC"),
                    "Greenfield: Dataset accessRights should be PUBLIC (from explicit metadata)");

            // Verify distributions are all PUBLIC (all files are public)
            ResIterator distributions = model.listResourcesWithProperty(RDF.type, DCAT.Distribution);
            int publicDistCount = 0;
            int restrictedDistCount = 0;
            int totalDistCount = 0;

            while (distributions.hasNext()) {
                totalDistCount++;
                Resource distribution = distributions.next();
                if (distribution.hasProperty(DCTerms.rights)) {
                    Resource distRights = distribution.getProperty(DCTerms.rights).getResource();
                    if (distRights.getURI().contains("PUBLIC")) {
                        publicDistCount++;
                    } else if (distRights.getURI().contains("RESTRICTED")) {
                        restrictedDistCount++;
                    }
                }
            }

            assertEquals(2, totalDistCount, "Should have 2 distributions (2 public files)");
            assertEquals(2, publicDistCount, "Should have 2 public distributions");
            assertEquals(0, restrictedDistCount, "Should have 0 restricted distributions");

        } finally {
            if (originalProp != null) {
                System.setProperty(RootConfigLoader.SYS_PROP, originalProp);
            } else {
                System.clearProperty(RootConfigLoader.SYS_PROP);
            }
        }
    }

    @Test
    void testIssue49AdaptationScenarioAllPublicFilesNoDerivedMetadata() throws Exception {
        // Adaptation scenario: no metadata field -> derive from files
        // All files public -> Dataset accessRights should be PUBLIC (derived)
        URL dcatRootPropertiesUrl =
                getClass().getClassLoader().getResource("mapping/issue_49_dataset_access_rights/issue-49-root.properties");
        assertNotNull( dcatRootPropertiesUrl, "Configuration file not found" );

        ExportDataProvider provider = getExportDataProvider("src/test/resources/input/issue_49_adaptation_allpublic");
        assertNotNull( provider, "Export data provider could not be created" );

        String originalProp = System.getProperty(RootConfigLoader.SYS_PROP);
        try {
            String configPath = new File(dcatRootPropertiesUrl.toURI()).getAbsolutePath();
            System.setProperty(RootConfigLoader.SYS_PROP, configPath);

            Dcat3ExporterTurtle exporter = new Dcat3ExporterTurtle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.exportDataset(provider, out);

            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0, "Exporter should write Turtle bytes");

            Model model = TestUtil.readModel(bytes, Lang.TURTLE);
            assertNotNull( model, "Model should not be null" );

            ResIterator datasets = model.listResourcesWithProperty(RDF.type, DCAT.Dataset);
            assertTrue(datasets.hasNext(), "Should have at least one Dataset");
            Resource dataset = datasets.next();

            // Verify: Dataset accessRights is PUBLIC (derived from all-public files)
            assertTrue(dataset.hasProperty(DCTerms.accessRights), "Dataset should have dct:accessRights property");
            Resource datasetAccessRights = dataset.getProperty(DCTerms.accessRights).getResource();
            assertTrue(
                    datasetAccessRights.getURI().contains("PUBLIC"),
                    "Adaptation mode: Dataset accessRights should be PUBLIC (derived - all files public)");

            // Verify distributions match files
            ResIterator distributions = model.listResourcesWithProperty(RDF.type, DCAT.Distribution);
            int publicDistCount = 0;
            int totalDistCount = 0;

            while (distributions.hasNext()) {
                totalDistCount++;
                Resource distribution = distributions.next();
                if (distribution.hasProperty(DCTerms.rights)) {
                    Resource distRights = distribution.getProperty(DCTerms.rights).getResource();
                    if (distRights.getURI().contains("PUBLIC")) {
                        publicDistCount++;
                    }
                }
            }

            assertEquals(2, totalDistCount, "Should have 2 distributions (2 files)");
            assertEquals(2, publicDistCount, "Should have 2 public distributions");

        } finally {
            if (originalProp != null) {
                System.setProperty(RootConfigLoader.SYS_PROP, originalProp);
            } else {
                System.clearProperty(RootConfigLoader.SYS_PROP);
            }
        }
    }

    @Test
    void testIssue49AdaptationScenarioMixedRestrictedFilesNoDerivedMetadata() throws Exception {
        // Adaptation scenario: no metadata field -> derive from files
        // Mixed files (1 public + 1 restricted) -> Dataset accessRights should be RESTRICTED (derived from ANY restricted)
        URL dcatRootPropertiesUrl =
                getClass().getClassLoader().getResource("mapping/issue_49_dataset_access_rights/issue-49-root.properties");
        assertNotNull( dcatRootPropertiesUrl, "Configuration file not found" );

        ExportDataProvider provider = getExportDataProvider("src/test/resources/input/issue_49_adaptation_mixed");
        assertNotNull( provider, "Export data provider could not be created" );

        String originalProp = System.getProperty(RootConfigLoader.SYS_PROP);
        try {
            String configPath = new File(dcatRootPropertiesUrl.toURI()).getAbsolutePath();
            System.setProperty(RootConfigLoader.SYS_PROP, configPath);

            Dcat3ExporterTurtle exporter = new Dcat3ExporterTurtle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.exportDataset(provider, out);

            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0, "Exporter should write Turtle bytes");

            Model model = TestUtil.readModel(bytes, Lang.TURTLE);
            assertNotNull( model, "Model should not be null" );

            ResIterator datasets = model.listResourcesWithProperty(RDF.type, DCAT.Dataset);
            assertTrue(datasets.hasNext(), "Should have at least one Dataset");
            Resource dataset = datasets.next();

            // Verify: Dataset accessRights is RESTRICTED (derived from any-restricted files)
            assertTrue(dataset.hasProperty(DCTerms.accessRights), "Dataset should have dct:accessRights property");
            Resource datasetAccessRights = dataset.getProperty(DCTerms.accessRights).getResource();
            assertTrue(
                    datasetAccessRights.getURI().contains("RESTRICTED"),
                    "Adaptation mode: Dataset accessRights should be RESTRICTED (derived - ANY file restricted)");

            // Verify distributions reflect individual file restrictions
            ResIterator distributions = model.listResourcesWithProperty(RDF.type, DCAT.Distribution);
            int publicDistCount = 0;
            int restrictedDistCount = 0;
            int totalDistCount = 0;

            while (distributions.hasNext()) {
                totalDistCount++;
                Resource distribution = distributions.next();
                if (distribution.hasProperty(DCTerms.rights)) {
                    Resource distRights = distribution.getProperty(DCTerms.rights).getResource();
                    if (distRights.getURI().contains("PUBLIC")) {
                        publicDistCount++;
                    } else if (distRights.getURI().contains("RESTRICTED")) {
                        restrictedDistCount++;
                    }
                }
            }

            assertEquals(2, totalDistCount, "Should have 2 distributions (2 files)");
            assertEquals(1, publicDistCount, "Should have 1 public distribution");
            assertEquals(1, restrictedDistCount, "Should have 1 restricted distribution");

        } finally {
            if (originalProp != null) {
                System.setProperty(RootConfigLoader.SYS_PROP, originalProp);
            } else {
                System.clearProperty(RootConfigLoader.SYS_PROP);
            }
        }
    }

    @Test
    void testIssue49AdaptationScenarioNonPublicMixedFilesNoDerivedMetadata() throws Exception {
        // Adaptation scenario: no metadata field -> derive from files
        // Mixed files (1 public + 1 restricted) -> Dataset accessRights should be RESTRICTED (ANY restricted = most restrictive)
        // Note: NON_PUBLIC maps to restricted=true in Dataverse, so this tests the general case
        URL dcatRootPropertiesUrl =
                getClass().getClassLoader().getResource("mapping/issue_49_dataset_access_rights/issue-49-root.properties");
        assertNotNull( dcatRootPropertiesUrl, "Configuration file not found" );

        ExportDataProvider provider = getExportDataProvider("src/test/resources/input/issue_49_adaptation_nonpublic");
        assertNotNull( provider, "Export data provider could not be created" );

        String originalProp = System.getProperty(RootConfigLoader.SYS_PROP);
        try {
            String configPath = new File(dcatRootPropertiesUrl.toURI()).getAbsolutePath();
            System.setProperty(RootConfigLoader.SYS_PROP, configPath);

            Dcat3ExporterTurtle exporter = new Dcat3ExporterTurtle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.exportDataset(provider, out);

            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0, "Exporter should write Turtle bytes");

            Model model = TestUtil.readModel(bytes, Lang.TURTLE);
            assertNotNull( model, "Model should not be null" );

            ResIterator datasets = model.listResourcesWithProperty(RDF.type, DCAT.Dataset);
            assertTrue(datasets.hasNext(), "Should have at least one Dataset");
            Resource dataset = datasets.next();

            // Verify: Dataset accessRights is RESTRICTED (public + non-public = most restrictive is restricted)
            assertTrue(dataset.hasProperty(DCTerms.accessRights), "Dataset should have dct:accessRights property");
            Resource datasetAccessRights = dataset.getProperty(DCTerms.accessRights).getResource();
            assertTrue(
                    datasetAccessRights.getURI().contains("RESTRICTED"),
                    "Adaptation mode: Dataset accessRights should be RESTRICTED (ANY file restricted/non-public)");

            // Verify distributions reflect individual file restrictions
            ResIterator distributions = model.listResourcesWithProperty(RDF.type, DCAT.Distribution);
            int publicDistCount = 0;
            int restrictedDistCount = 0;
            int totalDistCount = 0;

            while (distributions.hasNext()) {
                totalDistCount++;
                Resource distribution = distributions.next();
                if (distribution.hasProperty(DCTerms.rights)) {
                    Resource distRights = distribution.getProperty(DCTerms.rights).getResource();
                    if (distRights.getURI().contains("PUBLIC")) {
                        publicDistCount++;
                    } else if (distRights.getURI().contains("RESTRICTED")) {
                        restrictedDistCount++;
                    }
                }
            }

            assertEquals(2, totalDistCount, "Should have 2 distributions (2 files)");
            assertEquals(1, publicDistCount, "Should have 1 public distribution");
            assertEquals(1, restrictedDistCount, "Should have 1 restricted distribution");

        } finally {
            if (originalProp != null) {
                System.setProperty(RootConfigLoader.SYS_PROP, originalProp);
            } else {
                System.clearProperty(RootConfigLoader.SYS_PROP);
            }
        }
    }

}
