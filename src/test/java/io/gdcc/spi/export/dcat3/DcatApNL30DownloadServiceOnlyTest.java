package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.copyDirectory;
import static io.gdcc.spi.export.util.TestUtil.fetchShapesModel;
import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static io.gdcc.spi.export.util.TestUtil.loadProps;
import static io.gdcc.spi.export.util.TestUtil.readModel;
import static io.gdcc.spi.export.util.TestUtil.removeByPrefix;
import static io.gdcc.spi.export.util.TestUtil.storeProps;
import static io.gdcc.spi.export.util.TestUtil.toValidationReport;
import static org.assertj.core.api.Assertions.assertThat;

import io.gdcc.spi.export.ExportDataProvider;
import io.gdcc.spi.export.dcat3.config.loader.RootConfigLoader;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DcatApNL30DownloadServiceOnlyTest
 *
 * This test verifies that the exporter can generate a valid
 * DCAT‑AP‑NL 3.0 baseline profile for repositories that expose
 * datasets exclusively via download URLs (dcat:downloadURL /
 * dcat:accessURL) and do not provide an API-based DataService.
 *
 * The test performs the following steps:
 *
 * 1. A temporary working directory is created.
 * 2. The standard DCAT mapping files (dcat-root.properties,
 *    dcat-distribution.properties, etc.) are copied into the
 *    temporary directory.
 *
 * 3. The mapping is patched at runtime by removing all references to:
 *       - element.dataservice.*
 *       - relation.dataset_has_service.*
 *       - nodes.service.*
 *       - props.accessService.*
 *
 *    This corresponds to a "download-service-only" profile where
 *    no dcat:DataService instance is constructed.
 *
 * 4. The exporter is executed using this modified mapping.
 *
 * 5. The resulting RDF is validated against the Dutch DCAT‑AP‑NL 3.0
 *    baseline SHACL shapes:
 *       - EU baseline shapes
 *       - NL baseline shapes
 *       - Optionaliteit and klassebereik
 *    The recommended layer (dcat-ap-nl-SHACL-aanbevolen.ttl) is
 *    intentionally NOT included, because that layer introduces
 *    additional DataService constraints which are not applicable
 *    for download-only scenarios.
 *
 * 6. The test asserts that the output conforms to the baseline shapes.
 *
 * This mechanism avoids maintaining a second mapping profile on disk.
 * Instead, the runtime patch ensures that the canonical mapping files
 * remain the single source of truth, while still allowing a fully
 * compliant download-only export scenario to be tested.
 */
class DcatApNL30DownloadServiceOnlyTest {

    @TempDir
    Path tmp;

    @Test
    void export_APNL30_lightweight_profile_conforms_baseline_shapes() throws Exception {

        // 1) Copy mapping folder to temp
        URL mappingUrl = getClass().getClassLoader().getResource("AP_NL30/mapping");
        assertThat(mappingUrl).as("Mapping folder not found").isNotNull();

        Path srcMapping = Path.of(mappingUrl.toURI());
        Path dstMapping = tmp.resolve("mapping");
        copyDirectory(srcMapping, dstMapping);

        // 2) Patch root: remove dataservice element + dataset->service relation
        Path rootFile = dstMapping.resolve("dcat-root.properties");
        Properties rootProps = loadProps(rootFile);

        removeByPrefix(rootProps, "element.dataservice.", "relation.dataset_has_service.");

        storeProps(rootFile, rootProps, "Patched for lightweight profile: DataService removed");

        // 3) Patch distribution: remove accessService mapping
        Path distFile = dstMapping.resolve("dcat-distribution.properties");
        Properties distProps = loadProps(distFile);

        removeByPrefix(distProps, "nodes.service.", "props.accessService.");

        storeProps(distFile, distProps, "Patched for lightweight profile: accessService removed");

        // 4) Run export using patched root
        System.setProperty(RootConfigLoader.SYS_PROP, rootFile.toAbsolutePath().toString());

        ExportDataProvider provider = getExportDataProvider("src/test/resources/input/export_data_source_AP_NL30");

        Dcat3ExporterRdfXml exporter = new Dcat3ExporterRdfXml();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.exportDataset(provider, out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes).isNotEmpty();

        Model dataModel = readModel(bytes, Lang.RDFXML);

        // 5) Validate against baseline shapes (excluding "aanbevolen")
        List<String> urls = new ArrayList<>(List.of(
                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-OPT.ttl",
                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-SHACL.ttl",
                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-OPT.ttl",
                // intentionally NOT loading dcat-ap-nl-SHACL-aanbevolen.ttl
                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-SHACL-klassebereik-codelijsten.ttl",
                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-SHACL-klassebereik.ttl",
                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/dcat-ap-nl-SHACL.ttl",
                "https://raw.githubusercontent.com/Geonovum/DCAT-AP-NL30/refs/heads/main/shapes/optionaliteit.ttl"));

        Model shapes = fetchShapesModel(urls);
        ValidationReport report = ShaclValidator.get().validate(shapes.getGraph(), dataModel.getGraph());

        assertThat(report.conforms()).as(toValidationReport(report)).isTrue();
    }
}
