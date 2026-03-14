package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static io.gdcc.spi.export.util.TestUtil.readModel;
import static org.assertj.core.api.Assertions.assertThat;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.dcat3.config.loader.RootConfigLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Determinism regression test for issue #40.
 *
 * <p>Goal: exporting the same input twice must yield identical RDF/XML bytes, and all
 * dcat:Distribution resources must consistently reference the same dcat:DataService (env.apiBaseUrl).
 */
class RdfXmlDeterminismTest {

    private String originalRootProp;

    @BeforeEach
    void setUp() {
        originalRootProp = System.getProperty(RootConfigLoader.SYS_PROP);
    }

    @AfterEach
    void tearDown() {
        if (originalRootProp != null) {
            System.setProperty(RootConfigLoader.SYS_PROP, originalRootProp);
        } else {
            System.clearProperty(RootConfigLoader.SYS_PROP);
        }
    }

    @Test
    void rdfXml_export_is_byte_deterministic_and_accessService_is_stable() throws Exception {
        // --- mapping: determinism profile (test resources)
        URL rootUrl = getClass().getClassLoader().getResource("mapping/determinism/dcat-root.properties");
        assertThat(rootUrl)
                .as("Missing determinism mapping root in test resources")
                .isNotNull();
        File rootFile = new File(rootUrl.toURI());
        System.setProperty(RootConfigLoader.SYS_PROP, rootFile.getAbsolutePath());

        // --- input: determinism fixture folder (adjust if you named it differently)
        ExportDataProvider provider =
                getExportDataProvider("src/test/resources/input/determinism/export_data_source_determinism");

        // --- exporter
        Dcat3ExporterRdfXml exporter = new Dcat3ExporterRdfXml();

        // basic contract checks (optional but helpful)
        assertThat(exporter.getFormatName()).isEqualTo("dcat3-rdfxml");
        assertThat(exporter.getDisplayName(Locale.ROOT)).isEqualTo("DCAT-3 (RDF/XML)");
        assertThat(exporter.getMediaType()).isEqualTo("application/rdf+xml");

        // --- export 1
        byte[] bytes1 = exportToBytes(exporter, provider);

        // --- export 2 (same input, same configuration)
        byte[] bytes2 = exportToBytes(exporter, provider);

        // --- primary assertion: byte-for-byte stability
        assertThat(bytes2)
                .as(() -> "RDF/XML bytes must be identical across repeated exports.\n"
                        + "First output:\n" + new String(bytes1, StandardCharsets.UTF_8) + "\n\n"
                        + "Second output:\n" + new String(bytes2, StandardCharsets.UTF_8))
                .isEqualTo(bytes1);

        // --- secondary assertion: semantic stability (defensive)
        Model m1 = readModel(bytes1, Lang.RDFXML);
        Model m2 = readModel(bytes2, Lang.RDFXML);
        assertThat(m2.isIsomorphicWith(m1))
                .as("Graphs must be isomorphic even if serialization changes")
                .isTrue();

        // --- invariants for issue #40: accessService points to env.apiBaseUrl for every distribution
        ExportData exportData = ExportData.builder().provider(provider).build();
        String apiBaseUrl = exportData.env().get("apiBaseUrl").asText();
        assertThat(apiBaseUrl).as("env.apiBaseUrl must be present").isNotBlank();

        assertAllDistributionsReferenceAccessService(m1, apiBaseUrl);
    }

    private static byte[] exportToBytes(Dcat3ExporterRdfXml exporter, ExportDataProvider provider) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.exportDataset(provider, out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes).as("Exporter should write RDF/XML bytes").isNotEmpty();
        return bytes;
    }

    private static void assertAllDistributionsReferenceAccessService(Model model, String apiBaseUrl) {
        // Predicates / types
        Resource dcatDistribution = model.createResource("http://www.w3.org/ns/dcat#Distribution");
        Property rdfType = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Property accessService = model.createProperty("http://www.w3.org/ns/dcat#accessService");

        // The shared service resource (DataService IRI == apiBaseUrl)
        Resource service = model.createResource(apiBaseUrl);

        // Find all distributions by rdf:type
        ResIterator it = model.listResourcesWithProperty(rdfType, dcatDistribution);
        assertThat(it.hasNext())
                .as("Expected at least one dcat:Distribution in output")
                .isTrue();

        while (it.hasNext()) {
            Resource dist = it.next();
            assertThat(model.contains(dist, accessService, service))
                    .as("Each dcat:Distribution must have dcat:accessService pointing to " + apiBaseUrl
                            + " but missing for: " + (dist.isURIResource() ? dist.getURI() : dist.getId()))
                    .isTrue();
        }
    }
}
