package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static org.junit.jupiter.api.Assertions.*;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.dcat3.config.loader.RootConfigLoader;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
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
                getClass().getClassLoader().getResource("issue_49_dataset_access_rights/issue-49-root.properties");
        assertNotNull( dcatRootPropertiesUrl, "Configuration file not found" );

        // Load test data with mixed file restrictions
        ExportDataProvider provider = getExportDataProvider("src/test/resources/input/issue_49_dataset_access_rights");
        assertNotNull( provider, "Export data provider could not be created" );

        // Set system property for configuration - convert URL to proper file path
        String originalProp = System.getProperty(RootConfigLoader.SYS_PROP);
        try {
            // Use toURI() and then toString to handle Windows paths correctly
            String configPath = new java.io.File(dcatRootPropertiesUrl.toURI()).getAbsolutePath();
            System.setProperty(RootConfigLoader.SYS_PROP, configPath);

            // Create exporter and build model
            Dcat3ExporterTurtle exporter = new Dcat3ExporterTurtle();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.exportDataset(provider, out);

            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0, "Exporter should write Turtle bytes");

            // Parse the exported RDF
            Model model = io.gdcc.spi.export.util.TestUtil.readModel(bytes, org.apache.jena.riot.Lang.TURTLE);
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

}
