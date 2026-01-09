// src/test/java/io/gdcc/spi/export/dcat3/DcatApNL30RemoteShapesRdfXmlTest.java
package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.fetchShapesModel;
import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static io.gdcc.spi.export.util.TestUtil.looksOnline;
import static io.gdcc.spi.export.util.TestUtil.readModel;
import static io.gdcc.spi.export.util.TestUtil.toValidationReport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.dcat3.config.loader.RootConfigLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DcatApNL30ComplianceTest {

    private String originalProp;

    @BeforeEach
    void setUp() {
        originalProp = System.getProperty(RootConfigLoader.SYS_PROP);
    }

    @AfterEach
    void tearDown() {
        if (originalProp != null) {
            System.setProperty(RootConfigLoader.SYS_PROP, originalProp);
        } else {
            System.clearProperty(RootConfigLoader.SYS_PROP);
        }
    }

    @Test
    void exportSet_APNL30_compliance() throws Exception {
        // Run only when explicitly enabled or internet looks available.
        boolean enabled = Boolean.parseBoolean(System.getProperty("shacl.online", "false"));
        assumeTrue(enabled || looksOnline(), "Online SHACL validation is disabled or offline");

        // -- prepare configuration (same location as in your AP_NL30 tests)
        URL dcatRootPropertiesUrl =
                getClass().getClassLoader().getResource("AP_NL30/mapping/dcat-root.properties");
        assertThat(dcatRootPropertiesUrl).isNotNull();
        File dcatRootPropetiesFile = new File(dcatRootPropertiesUrl.toURI());
        System.setProperty(RootConfigLoader.SYS_PROP, dcatRootPropetiesFile.getAbsolutePath());

        // -- prepare export data provider (your dataset for AP_NL30 tests)
        ExportDataProvider provider =
                getExportDataProvider("src/test/resources/input/export_data_source_AP_NL30");

        // -- prepare exporter (RDF/XML only)
        Dcat3ExporterRdfXml exporter = new Dcat3ExporterRdfXml();

        // -- basic contract checks
        assertThat(exporter.getFormatName()).isEqualTo("dcat3-rdfxml");
        assertThat(exporter.getDisplayName(Locale.ROOT)).isEqualTo("DCAT-3 (RDF/XML)");
        assertThat(exporter.isAvailableToUsers()).isTrue();
        assertThat(exporter.isHarvestable()).isTrue();
        assertThat(exporter.getMediaType()).isEqualTo("application/rdf+xml");

        // -- export dataset
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.exportDataset(provider, out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes).as("Exporter should write RDF bytes").isNotEmpty();
        System.out.println(new String(bytes));

        // -- parse exported RDF/XML
        Model dataModel = readModel(bytes, Lang.RDFXML);

        // -- fetch shapes from canonical online sources (EU baseline + NL specialization)
        Model shapes =
                fetchShapesModel(
                        List.of(
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-OPT.ttl",
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-SHACL.ttl",
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-OPT.ttl",
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-SHACL-aanbevolen.ttl",
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-SHACL-klassebereik-codelijsten.ttl",
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-SHACL-klassebereik.ttl",
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-SHACL.ttl",
                                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/optionaliteit.ttl"));

        // -- SHACL validation
        ValidationReport report =
                ShaclValidator.get().validate(shapes.getGraph(), dataModel.getGraph());

        assertThat(report.conforms()).as(toValidationReport(report)).isTrue();
    }
}
