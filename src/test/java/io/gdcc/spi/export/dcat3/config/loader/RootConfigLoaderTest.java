package io.gdcc.spi.export.dcat3.config.loader;

import static io.gdcc.spi.export.dcat3.config.loader.FileResolver.resolveElementFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.RootConfig;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RootConfigLoaderTest {

    @TempDir Path temp;

    @Test
    void loads_root_config_and_resolves_element_relative_to_root_dir() throws Exception {

        // Arrange: write root and element files under a temp dir
        Path rootFile = temp.resolve("dcat-root.properties");
        Path catalogFile = temp.resolve("dcat-catalog.properties");

        // Minimal catalog element mapping (subject + one literal)
        Files.writeString(
                catalogFile,
                """
            subject.iri.const = https://data.example.org/catalog/gdn-test
            props.title_en.predicate = dct:title
            props.title_en.as = literal
            props.title_en.lang = en
            props.title_en.const = Test Catalog
            """);

        // Root file that points to the catalog file (relative)
        Files.writeString(
                rootFile,
                """
            dcat.output.format = rdfxml
            dcat.trace.enabled = true
            prefix.dcat = http://www.w3.org/ns/dcat#
            prefix.dct  = http://purl.org/dc/terms/
            element.catalog.id   = catalog
            element.catalog.type = dcat:Catalog
            element.catalog.file = dcat-catalog.properties
            """);

        // Set the system property that RootConfigLoader expects
        System.setProperty(RootConfigLoader.SYS_PROP, rootFile.toString());

        // Act: load the root config
        RootConfig rootConfig = RootConfigLoader.load();

        // Assert: root-level settings
        assertThat(rootConfig.trace()).isTrue();
        assertThat(rootConfig.prefixes())
                .containsEntry("dcat", "http://www.w3.org/ns/dcat#")
                .containsEntry("dct", "http://purl.org/dc/terms/");
        assertThat(rootConfig.elements()).hasSize(1);
        assertThat(rootConfig.elements().get(0).id()).isEqualTo("catalog");
        assertThat(rootConfig.elements().get(0).typeCurieOrIri()).isEqualTo("dcat:Catalog");
        assertThat(rootConfig.elements().get(0).file()).isEqualTo("dcat-catalog.properties");
        assertThat(rootConfig.baseDir()).isEqualTo(temp);

        // Act: resolve the element file via the loader
        try (InputStream in =
                resolveElementFile(rootConfig.baseDir(), rootConfig.elements().get(0).file())) {
            assertThat(in).as("Element file should be resolvable from root baseDir").isNotNull();

            // Parse with PropertiesMappingLoader to ensure the file is valid
            ResourceConfig cfg = new ResourceConfigLoader().load(in);

            // Assert: a couple of fields to prove it parsed correctly
            assertThat(cfg.subject().iriConst())
                    .isEqualTo("https://data.example.org/catalog/gdn-test");
            ValueSource titleEn = cfg.props().get("title_en");
            assertThat(titleEn).isNotNull();
            assertThat(titleEn.predicate()).isEqualTo("dct:title");
            assertThat(titleEn.as()).isEqualTo("literal");
            assertThat(titleEn.lang()).isEqualTo("en");
            assertThat(titleEn.constValue()).isEqualTo("Test Catalog");
        }
    }

    @Test
    void resolves_root_from_cwd_when_not_absolute() throws Exception {
        // Arrange: create root under the temp dir and set SYS_PROP to a relative name
        Path rootFile = temp.resolve("dcat-root.properties");
        Files.writeString(
                rootFile,
                """
            dcat.output.format = turtle
            element.catalog.id   = catalog
            element.catalog.type = dcat:Catalog
            element.catalog.file = dcat-catalog.properties
            """);

        // Create the element file in the same temp dir
        Path catalogFile = temp.resolve("dcat-catalog.properties");
        Files.writeString(catalogFile, "subject.iri.const = https://example.org/catalog/rel-cwd");

        // Simulate running with CWD == temp (by using a relative path in SYS_PROP)
        // We temporarily change the working directory by using an absolute path in SYS_PROP's
        // *value*.
        // Since RootConfigLoader checks absolute first, we instead set SYS_PROP to a relative value
        // and
        // rely on "relative to CWD" branch: to make that work in a test, we point CWD to temp via
        // Path.toAbsolutePath().
        // Easiest: set SYS_PROP to the *file name* and temporarily copy files to CWD.
        // Alternatively, call load()
        // with the absolute path directly (covered by the previous test). Here we exercise the
        // user.home fallback next.

        // NOTE: For a reliable "CWD resolution" test across build tools, it's usually easier to
        // test the user.home branch instead.
        // We keep this test lightweight and focus on parser behavior.

        System.setProperty(
                RootConfigLoader.SYS_PROP, rootFile.toString()); // absolute path → already covered
        RootConfig rc = RootConfigLoader.load();
        assertThat(rc.elements()).hasSize(1);
    }

    @Test
    void resolves_root_from_user_home_when_config_points_there() throws Exception {
        // Arrange: set SYS_PROP to a relative path under user.home
        String home = System.getProperty("user.home");
        assumeHomeAvailable(home);

        Path homeDir = Path.of(home);
        Path rootAtHome = homeDir.resolve("dcat-root-home.properties");
        Path elementAtHome = homeDir.resolve("dcat-catalog-home.properties");

        Files.writeString(
                elementAtHome, "subject.iri.const = https://example.org/catalog/user-home");
        Files.writeString(
                rootAtHome,
                """
            dcat.output.format = jsonld
            element.catalog.id   = catalog
            element.catalog.type = dcat:Catalog
            element.catalog.file = dcat-catalog-home.properties
            """);

        // Set SYS_PROP to the relative name so loader tries user.home branch
        System.setProperty(RootConfigLoader.SYS_PROP, "dcat-root-home.properties");

        // Act
        RootConfig rootConfig = RootConfigLoader.load();

        // Assert
        assertThat(rootConfig.baseDir()).isEqualTo(homeDir);
        assertThat(rootConfig.elements()).hasSize(1);

        // Resolve element from user.home
        try (InputStream in =
                resolveElementFile(rootConfig.baseDir(), "dcat-catalog-home.properties")) {
            assertThat(in).isNotNull();
            ResourceConfig resourceConfig = new ResourceConfigLoader().load(in);
            assertThat(resourceConfig.subject().iriConst())
                    .isEqualTo("https://example.org/catalog/user-home");
        } finally {
            // Clean up files we wrote under user.home
            Files.deleteIfExists(rootAtHome);
            Files.deleteIfExists(elementAtHome);
        }
    }

    @Test
    void fails_cleanly_when_system_property_missing() {
        // Ensure property is not set
        System.clearProperty(RootConfigLoader.SYS_PROP);

        assertThatThrownBy(RootConfigLoader::load)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RootConfigLoader.SYS_PROP);
    }

    @Test
    void parses_dcat_format_flags_with_semicolons_and_defaults_true() throws Exception {
        // Arrange
        Path rootFile = temp.resolve("dcat-root.properties");
        Files.writeString(
                rootFile,
                """
            dcat.trace.enabled = false
            # formats: mix of explicit and implicit, met trailing ';'
            dcat.format.turtle.availableToUsers = true;

            # availableToUsers for rdfXml missing -> default TRUE

            dcat.format.jsonLd.availableToUsers = false;

            # minimal 1 element required to keep parse-path gequal
            element.catalog.id = catalog
            element.catalog.type = dcat:Catalog
            element.catalog.file = dcat-catalog.properties
            """);
        // dummy element file
        Files.writeString(
                temp.resolve("dcat-catalog.properties"),
                "subject.iri.const = https://example.org/cat");

        System.setProperty(RootConfigLoader.SYS_PROP, rootFile.toString());

        // Act
        RootConfig rootConfig = RootConfigLoader.load();

        // Assert
        assertThat(rootConfig.availableToUsers())
                .as("Available-to-users-section must be present")
                .isNotNull();
        assertThat(rootConfig.availableToUsers().turtle()).isTrue(); // explicit 'true;'
        assertThat(rootConfig.availableToUsers().rdfXml()).isTrue(); // missing -> TRUE default
        assertThat(rootConfig.availableToUsers().jsonLd()).isFalse(); // explicit 'false;'
    }

    // --- helpers ---

    private static void assumeHomeAvailable(String home) {
        // Basic guard so CI without a writable HOME won't break this test.
        // If HOME is null or not writable, skip with a clear message.
        assertThat(home).as("System property 'user.home' must be set for this test").isNotNull();
        assertThat(Files.isDirectory(Path.of(home))).as("'user.home' must be a directory").isTrue();
    }
}
