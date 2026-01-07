package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static io.gdcc.spi.export.util.TestUtil.readModel;
import static io.gdcc.spi.export.util.TestUtil.toValidationReport;
import static org.assertj.core.api.Assertions.assertThat;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.Exporter;
import io.gdcc.spi.export.dcat3.config.loader.RootConfigLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.Locale;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Dcat3ExporterTest {

    @ParameterizedTest(name = "{index} => {0}")
    @CsvSource({
        // formatKey, expectedFormatName,expectedDisplayName,expectedMediaType,jenaLang
        "rdfxml,dcat3-rdfxml,DCAT-3 (RDF/XML),application/rdf+xml,RDFXML",
        "turtle,dcat3-turtle,DCAT-3 (Turtle) ,text/turtle        ,TURTLE",
        "jsonld,dcat3-jsonld,DCAT-3 (JSON-LD),application/ld+json,JSONLD"
    })
    void exportSet1_allFormats(
            String formatKey,
            String expectedFormatName,
            String expectedDisplayName,
            String expectedMediaType,
            String jenaLangName)
            throws Exception {

        // -- prepare configuration (same as your original)
        URL dcatRootPropertiesUrl =
                getClass().getClassLoader().getResource( "lightweight/mapping/dcat-root.properties" );
        assertThat(dcatRootPropertiesUrl).isNotNull();
        File dcatRootPropetiesFile = new File(dcatRootPropertiesUrl.toURI());
        System.setProperty(RootConfigLoader.SYS_PROP, dcatRootPropetiesFile.getAbsolutePath());

        // -- prepare export data provider (same as your original)
        ExportDataProvider provider =
                getExportDataProvider("src/test/resources/input/export_data_source_lightweight");

        // -- prepare exporter for the requested format
        Exporter exporter = createExporter(formatKey);

        // -- static contract checks per exporter
        assertThat(exporter.getFormatName()).isEqualTo(expectedFormatName);
        assertThat(exporter.getDisplayName(Locale.ROOT)).isEqualTo(expectedDisplayName);
        assertThat(exporter.isAvailableToUsers()).isEqualTo(true);
        assertThat(exporter.isHarvestable()).isEqualTo(true);
        assertThat(exporter.getMediaType()).isEqualTo(expectedMediaType);

        // -- perform export
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.exportDataset(provider, out);
        byte[] bytes = out.toByteArray();

        // -- sanity check
        assertThat(bytes).as("Exporter should write RDF bytes").isNotEmpty();

        // -- parse model using the format-specific Jena Lang
        Lang lang = toJenaLang(jenaLangName);
        Model dataModel = readModel(bytes, lang);

        // -- SHACL validation (same as your original)
        Model shapes = ModelFactory.createDefaultModel();
        shapes.read(
                getClass().getClassLoader().getResourceAsStream( "input/validation_lightweight/shacl.ttl" ),
                null,
                "TURTLE");

        ValidationReport report =
                ShaclValidator.get().validate(shapes.getGraph(), dataModel.getGraph());
        assertThat(report.conforms()).as(toValidationReport(report)).isTrue();
    }

    /** Simple factory mapping the csv 'formatKey' to an exporter instance. */
    private static Dcat3ExporterBase createExporter(String key) {
        return (Dcat3ExporterBase)
                switch (key.toLowerCase(Locale.ROOT)) {
                    case "rdfxml" -> new Dcat3ExporterRdfXml();
                    case "turtle" -> new Dcat3ExporterTurtle();
                    case "jsonld" -> new Dcat3ExporterJsonLd();
                    default -> throw new IllegalArgumentException("Unknown format key: " + key);
                };
    }

    /** Convert csv-provided Jena language token to a Lang. */
    private static Lang toJenaLang(String name) {
        return switch (name) {
            case "RDFXML" -> Lang.RDFXML;
            case "TURTLE" -> Lang.TURTLE;
            case "JSONLD" -> Lang.JSONLD;
            default -> throw new IllegalArgumentException("Unsupported Jena Lang: " + name);
        };
    }
}
