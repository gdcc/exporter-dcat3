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
 * Integration-style unit test for map_empty/map_nonempty functionality.
 */
class MapEmptyNonEmptyIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ResourceConfig loadConfig(String resourcePath) throws Exception {
        try (InputStream in =
                MapEmptyNonEmptyIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in)
                    .as("Test mapping resource should exist: " + resourcePath)
                    .isNotNull();
            return new ResourceConfigLoader().load(in);
        }
    }

    private static JsonNode loadJson(String resourcePath) throws Exception {
        try (InputStream in =
                MapEmptyNonEmptyIntegrationTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
    void map_nonempty_when_json_path_returns_matches() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/map_empty_nonempty/dcat-dataset.properties");
        JsonNode input = loadJson("input/map_empty_nonempty/input_with_restricted.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property accessRights = model.createProperty("http://purl.org/dc/terms/accessRights");
        Literal expected =
                model.createLiteral("http://publications.europa.eu/resource/authority/access-right/RESTRICTED");

        assertThat(model.contains(subj, accessRights, expected))
                .as("When restricted files exist, accessRights should be RESTRICTED")
                .isTrue();
    }

    @Test
    void map_empty_when_json_path_returns_no_matches() throws Exception {
        ResourceConfig cfg = loadConfig("mapping/map_empty_nonempty/dcat-dataset.properties");
        JsonNode input = loadJson("input/map_empty_nonempty/input_no_restricted.json");

        Model model = runMapping(cfg, input);

        Resource subj = model.createResource("https://example.org/ds/1");
        Property accessRights = model.createProperty("http://purl.org/dc/terms/accessRights");
        Literal expected = model.createLiteral("http://publications.europa.eu/resource/authority/access-right/PUBLIC");

        assertThat(model.contains(subj, accessRights, expected))
                .as("When no restricted files exist, accessRights should be PUBLIC")
                .isTrue();
    }
}
