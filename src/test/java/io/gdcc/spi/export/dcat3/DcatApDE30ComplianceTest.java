package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.copyDirectory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the AP-DE30 profile. */
class DcatApDE30ComplianceTest {

    private static final String PROFILE_RESOURCE = "AP_DE30/mapping/dcat-root.properties";
    private static final String FIXTURE = "src/test/resources/input/export_data_source_AP_DE30";

    private static final String DCAT_AP_BASE_SHACL =
            "https://raw.githubusercontent.com/init-dcat-ap-de/DCAT-AP/master/releases/3.0.0/shacl/dcat-ap-SHACL.ttl";
    private static final String DCAT_AP_DE_SHACL =
            "https://raw.githubusercontent.com/GovDataOfficial/DCAT-AP.de-SHACL-Validation/master/validator/resources/v3.0/shapes/";

    private static final String SPDX_ONTOLOGY =
            "https://raw.githubusercontent.com/spdx/spdx-spec/v2.3/ontology/spdx-ontology.owl.xml";
    private static final String SPDX_CHECKSUM_ALGORITHM =
            "http://spdx.org/rdf/terms#ChecksumAlgorithm";

    private static final String DCAT_AP_DE_LICENSES =
            "https://www.dcat-ap.de/def/licenses/20210721.rdf";
    private static final String DCAT_AP_DE_LICENSE_SCHEME = "http://dcat-ap.de/def/licenses";
    private static final String SKOS_IN_SCHEME = "http://www.w3.org/2004/02/skos/core#inScheme";
    private static final String DCAT_AP_DE_CC0_LICENSE_URI = "http://dcat-ap.de/def/licenses/cc-zero";
    private static final String DCAT_AP_DE_OTHER_CLOSED_LICENSE_URI = "http://dcat-ap.de/def/licenses/other-closed";

    private String originalConfig;

    @TempDir
    Path temp;

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
    void export_APDE30_conformsToOfficialShapes() throws Exception {
        boolean enabled = Boolean.parseBoolean(System.getProperty("shacl.online", "false"));
        assumeTrue(enabled || looksOnline(), "Online SHACL validation is disabled or offline");

        URL rootUrl = getClass().getClassLoader().getResource(PROFILE_RESOURCE);
        assertThat(rootUrl).as("AP-DE30 root mapping not found").isNotNull();
        System.setProperty(RootConfigLoader.SYS_PROP, new File(rootUrl.toURI()).getAbsolutePath());

        ExportDataProvider provider = getExportDataProvider(FIXTURE);
        Dcat3ExporterRdfXml exporter = new Dcat3ExporterRdfXml();

        assertThat(exporter.getFormatName()).isEqualTo("dcat3-rdfxml");
        assertThat(exporter.getDisplayName(Locale.ROOT)).isEqualTo("DCAT-AP.de (RDF/XML)");
        assertThat(exporter.isAvailableToUsers()).isTrue();
        assertThat(exporter.isHarvestable()).isTrue();
        assertThat(exporter.getMediaType()).isEqualTo("application/rdf+xml");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.exportDataset(provider, out);
        Model data = readModel(out.toByteArray(), Lang.RDFXML);

        // Load the SPDX ontology. It defines the types of SPDX algorithm IRIs, which are checked by the DE checksum
        // shape using `sh:class spdx:ChecksumAlgorithm`. We copy only those necessary statements
        Model spdxOntology = ModelFactory.createDefaultModel();
        RDFParser.create().source(SPDX_ONTOLOGY).lang(Lang.RDFXML).parse(spdxOntology.getGraph());
        data.add(spdxOntology.listStatements(
                null,
                RDF.type,
                spdxOntology.createResource(SPDX_CHECKSUM_ALGORITHM)));
        // Load the official DCAT-AP.de license vocabulary. It supplies the skos:inScheme statements required by the DE
        // license shape. We copy only those necessary statements
        Model licenseVocabulary = ModelFactory.createDefaultModel();
        RDFParser.create().source(DCAT_AP_DE_LICENSES).lang(Lang.RDFXML).parse(licenseVocabulary.getGraph());
        data.add(licenseVocabulary.listStatements(
                null,
                licenseVocabulary.createProperty(SKOS_IN_SCHEME),
                licenseVocabulary.createResource(DCAT_AP_DE_LICENSE_SCHEME)));

        Model shapes = fetchShapesModel(List.of(
                DCAT_AP_BASE_SHACL,
                DCAT_AP_DE_SHACL + "dcat-ap-SHACL-DE.ttl",
                DCAT_AP_DE_SHACL + "dcat-ap-de-controlledvocabularies.ttl",
                DCAT_AP_DE_SHACL + "dcat-ap-de-deprecated.ttl",
                DCAT_AP_DE_SHACL + "dcat-ap-de-imports.ttl",
                DCAT_AP_DE_SHACL + "dcat-ap-spec-german-additions.ttl"));

        ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), data.getGraph());
        assertThat(report.conforms()).as(toValidationReport(report)).isTrue();
    }

    /**
     * Test for the license mapping.
     * The `Distribution` mapping uses the Dataverse license URI as its primary input and maps known licenses to the [allowed DCAT-AP.de license URIs](https://www.dcat-ap.de/def/licenses/).
     * Unknown licenses should fall back to `http://dcat-ap.de/def/licenses/other-closed`.
     */
    @Test
    void export_APDE30_licenseMapping() throws Exception {
        URL rootUrl = getClass().getClassLoader().getResource(PROFILE_RESOURCE);
        assertThat(rootUrl).as("AP-DE30 root mapping not found").isNotNull();
        System.setProperty(RootConfigLoader.SYS_PROP, new File(rootUrl.toURI()).getAbsolutePath());

        Dcat3ExporterRdfXml exporter = new Dcat3ExporterRdfXml();

        // Known Dataverse license: verify that CC0 is mapped to the DCAT-AP.de license URI
        ByteArrayOutputStream knownOut = new ByteArrayOutputStream();
        exporter.exportDataset(getExportDataProvider(FIXTURE), knownOut);
        Model knownData = readModel(knownOut.toByteArray(), Lang.RDFXML);
        Resource license = knownData.createResource(DCAT_AP_DE_CC0_LICENSE_URI);
        assertThat(knownData.contains(null, DCTerms.license, license)).isTrue();
        assertThat(knownData.contains(license, RDF.type, DCTerms.LicenseDocument)).isTrue();

        // Replace license URI with unknown URI
        Path fixture = temp.resolve("fixture");
        copyDirectory(Path.of(FIXTURE), fixture);
        Path datasetJson = fixture.resolve("datasetJson.json");
        Files.writeString(
                datasetJson,
                Files.readString(datasetJson).replace(
                        "http://creativecommons.org/publicdomain/zero/1.0",
                        "https://licenses.example.test/unknown"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.exportDataset(getExportDataProvider(fixture.toString()), out);

        // Check that the RDF/XML export contains a statement for the fallback license
        Model data = readModel(out.toByteArray(), Lang.RDFXML);
        Resource fallback = data.createResource(DCAT_AP_DE_OTHER_CLOSED_LICENSE_URI);
        assertThat(data.contains(null, DCTerms.license, fallback)).isTrue();
    }
}
