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
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

/**
 * Integration-style unit test for Issue #49 semantics:
 * fallback values for literal properties (onUnMappedValue and onNoInputValue).
 *
 * This tests the extension of Issue #41 fallback logic to literal values.
 */
class LiteralFallbackIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ResourceConfig loadConfig(String resourcePath) throws Exception {
        try (InputStream in =
                LiteralFallbackIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in)
                    .as("Test mapping resource should exist: " + resourcePath)
                    .isNotNull();
            return new ResourceConfigLoader().load(in);
        }
    }

    private static JsonNode loadJson(String resourcePath) throws Exception {
        try (InputStream in =
                LiteralFallbackIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
    void mapped_literal_value_still_maps_normally_control_case() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/literal_fallback/dcat-dataset.properties");
        JsonNode input = loadJson("input/literal_fallback/input_mapped.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property status = model.createProperty("http://purl.org/dc/terms/status");
        Literal expected = model.createLiteral("published");

        assertThat(model.contains(subj, status, expected))
                .as("Mapped input 'published' should produce 'published' status")
                .isTrue();
    }

    @Test
    void unmapped_literal_input_should_use_onUnMappedValue() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/literal_fallback/dcat-dataset.properties");
        JsonNode input = loadJson("input/literal_fallback/input_unmapped.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property status = model.createProperty("http://purl.org/dc/terms/status");
        Literal expectedFallback = model.createLiteral("unknown");

        assertThat(model.contains(subj, status, expectedFallback))
                .as("Unmapped literal input should use props.<prop>.onUnMappedValue fallback")
                .isTrue();
    }

    @Test
    void no_literal_input_should_use_onNoInputValue() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/literal_fallback/dcat-dataset.properties");
        JsonNode input = loadJson("input/literal_fallback/no_input.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property status = model.createProperty("http://purl.org/dc/terms/status");
        Literal expectedFallback = model.createLiteral("not specified");

        assertThat(model.contains(subj, status, expectedFallback))
                .as("No literal input should use props.<prop>.onNoInputValue fallback")
                .isTrue();
    }
}
