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

/**
 * Tests for the AP-DK (Datavejviser) profile.
 *
 * <p>Unlike AP-NL30/AP-DE30, no machine-readable SHACL shape set is published for DCAT-AP-DK 3.0
 * by the Danish maintainers (digst/DCAT-AP-DK): the {@code releases/v.3.0/validation} folder only
 * contains a screenshot and a README pointing to manual validation tools, not a {@code .ttl} shape
 * file (earlier releases, e.g. v.2.0, did ship one). In the absence of a DK-specific layer, this
 * test instead validates the exported RDF against the official EU DCAT-AP 3.0 baseline shapes,
 * which DCAT-AP-DK 3.0 is built on top of.
 *
 * <p>This profile intentionally omits {@code dcat:DataService} (see profile README), so, like
 * {@link DcatApNL30DownloadServiceOnlyTest}, there is no DataService-specific validation here.
 */
class DcatApDK30ComplianceTest {

    private static final String PROFILE_RESOURCE = "AP_DK_DeiC_Datavejviser/mapping/dcat-root.properties";
    private static final String FIXTURE = "src/test/resources/input/export_data_source_AP_DK_DeiC_Datavejviser";

    // The former init-dcat-ap-de/DCAT-AP mirror (used by DcatApDE30ComplianceTest) has been
    // unreachable since 2026-10-09; SEMICeu/DCAT-AP is the official upstream source, pinned to a
    // commit known to carry a byte-identical copy of the 3.0.0 release shapes.
    private static final String DCAT_AP_BASE_SHACL =
            "https://raw.githubusercontent.com/SEMICeu/DCAT-AP/fff059bd9c14c1adff5abfd6f32ff437eaec3390"
                    + "/releases/3.0.0/shacl/dcat-ap-SHACL.ttl";

    private String originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = System.getProperty(RootConfigLoader.SYS_PROP);
    }

    @AfterEach
    void tearDown() {
        if (originalConfig == null) {
            System.clearProperty(RootConfigLoader.SYS_PROP);
        } else {
            System.setProperty(RootConfigLoader.SYS_PROP, originalConfig);
        }
    }

    @Test
    void export_APDK30_conformsToBaselineShapes() throws Exception {
        boolean enabled = Boolean.parseBoolean(System.getProperty("shacl.online", "false"));
        assumeTrue(enabled || looksOnline(), "Online SHACL validation is disabled or offline");

        URL rootUrl = getClass().getClassLoader().getResource(PROFILE_RESOURCE);
        assertThat(rootUrl).as("AP-DK root mapping not found").isNotNull();
        System.setProperty(RootConfigLoader.SYS_PROP, new File(rootUrl.toURI()).getAbsolutePath());

        ExportDataProvider provider = getExportDataProvider(FIXTURE);
        Dcat3ExporterRdfXml exporter = new Dcat3ExporterRdfXml();

        assertThat(exporter.getFormatName()).isEqualTo("dcat3-rdfxml");
        assertThat(exporter.getDisplayName(Locale.ROOT)).isEqualTo("DCAT-AP (RDF/XML)");
        assertThat(exporter.isAvailableToUsers()).isTrue();
        assertThat(exporter.isHarvestable()).isTrue();
        assertThat(exporter.getMediaType()).isEqualTo("application/rdf+xml");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.exportDataset(provider, out);
        Model data = readModel(out.toByteArray(), Lang.RDFXML);

        Model shapes = fetchShapesModel(List.of(DCAT_AP_BASE_SHACL));

        ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), data.getGraph());
        assertThat(report.conforms()).as(toValidationReport(report)).isTrue();
    }
}
