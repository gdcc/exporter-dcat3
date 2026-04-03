package io.gdcc.spi.export.dcat3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gdcc.spi.export.dcat3.config.loader.ResourceConfigLoader;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.mapping.JaywayJsonFinder;
import io.gdcc.spi.export.dcat3.mapping.Prefixes;
import io.gdcc.spi.export.dcat3.mapping.ResourceMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

/**
 * Integration-style unit test for Issue #41 semantics:
 * distinguish "no input" vs "unmapped" for mapped controlled vocab nodes.
 *
 * This commit adds the test first (expected to fail for the two fallback cases),
 * before implementing loader/model/runtime changes.
 */
class SemanticsFallbackIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ResourceConfig loadConfig(String resourcePath) throws Exception {
        try (InputStream in =
                SemanticsFallbackIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in)
                    .as("Test mapping resource should exist: " + resourcePath)
                    .isNotNull();
            return new ResourceConfigLoader().load(in);
        }
    }

    private static JsonNode loadJson(String resourcePath) throws Exception {
        try (InputStream in =
                SemanticsFallbackIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("Test input JSON should exist: " + resourcePath).isNotNull();
            JsonNode node = MAPPER.readTree(in);
            return node != null ? node : MAPPER.createObjectNode();
        }
    }

    private static Prefixes prefixes() {
        // Minimal prefix set required for this test profile.
        Map<String, String> p = new LinkedHashMap<>();
        p.put("dct", "http://purl.org/dc/terms/");
        p.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        return new Prefixes(p);
    }

    private static Model runMapping(ResourceConfig cfg, JsonNode input) {
        JaywayJsonFinder finder = new JaywayJsonFinder(input);
        ResourceMapper mapper = new ResourceMapper(cfg, prefixes(), "dcat:Dataset");
        return mapper.build(finder);
    }

    @Test
    void mapped_value_still_maps_normally_control_case() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/semantics_fallback/dcat-dataset.properties");
        JsonNode input = loadJson("input/semantics_fallback/input_mapped.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property accessRights = model.createProperty("http://purl.org/dc/terms/accessRights");
        Resource expected =
                model.createResource("http://publications.europa.eu/resource/authority/access-right/PUBLIC");

        assertThat(model.contains(subj, accessRights, expected))
                .as("Mapped input 'public' should produce PUBLIC accessRights")
                .isTrue();
    }

    @Test
    void unmapped_input_should_use_onUnMappedValue() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/semantics_fallback/dcat-dataset.properties");
        JsonNode input = loadJson("input/semantics_fallback/input_unmapped.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property accessRights = model.createProperty("http://purl.org/dc/terms/accessRights");
        Resource expectedFallback = model.createResource("http://example.org/ar/unmapped");

        // Expected TRUE after implementing Issue #41.
        // Expected FALSE right now (before the fix), because the fallback isn't loaded/applied yet.
        assertThat(model.contains(subj, accessRights, expectedFallback))
                .as("Unmapped input should use nodes.<node>.onUnMappedValue fallback")
                .isTrue();
    }

    @Test
    void no_input_should_use_onNoInputValue() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/semantics_fallback/dcat-dataset.properties");
        JsonNode input = loadJson("input/semantics_fallback/no_input.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property accessRights = model.createProperty("http://purl.org/dc/terms/accessRights");
        Resource expectedFallback = model.createResource("http://example.org/ar/no-input");

        // Expected TRUE after implementing Issue #41.
        // Expected FALSE right now (before the fix), because the fallback isn't loaded/applied yet.
        assertThat(model.contains(subj, accessRights, expectedFallback))
                .as("No input should use nodes.<node>.onNoInputValue fallback")
                .isTrue();
    }
}
