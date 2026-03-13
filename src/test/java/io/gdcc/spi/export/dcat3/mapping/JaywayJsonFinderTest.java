package io.gdcc.spi.export.dcat3.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JaywayJsonFinderTest {

    @BeforeAll
    static void silenceJaywayJsonFinderWarnings() {
        Logger logger = Logger.getLogger(JaywayJsonFinder.class.getCanonicalName());
        logger.setLevel(Level.OFF);
    }

    private static JsonNode jsonNode(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(json);
    }

    @Test
    @DisplayName("list() returns stringified values from root scope")
    void list_from_root_scope() throws Exception {
        JsonNode root = jsonNode(
                """
            {"dataset":{"title":"Example","id":123,"tags":["A","B"]},
             "other":{"title":"Else"}}
        """);
        JaywayJsonFinder finder = new JaywayJsonFinder(root);

        List<String> titles = finder.list("$.dataset.title");
        assertThat(titles).containsExactly("Example");

        List<String> ids = finder.list("$.dataset.id");
        assertThat(ids).containsExactly("123");

        List<String> tags = finder.list("$.dataset.tags[*]");
        assertThat(tags).containsExactly("A", "B");
    }

    @Test
    @DisplayName("nodes() returns matching subtrees as JsonNode list")
    void nodes_returns_subtrees() throws Exception {
        JsonNode root = jsonNode(
                """
            {"dataset":{"title":"Example","id":123},
             "other":{"dataset":{"title":"Nested","id":999}}}
        """);
        JaywayJsonFinder finder = new JaywayJsonFinder(root);

        List<com.fasterxml.jackson.databind.JsonNode> nodes = finder.nodes("$..dataset");
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).get("title").asText()).isEqualTo("Example");
        assertThat(nodes.get(1).get("title").asText()).isEqualTo("Nested");
    }

    @Test
    @DisplayName("at() scopes the finder while listRoot() still reads from the original root")
    void at_scopes_finder_and_listRoot_reads_original() throws Exception {
        JsonNode root = jsonNode(
                """
            {"dataset":{"title":"Example","id":123},
             "other":{"title":"Else"}}
        """);
        JaywayJsonFinder finder = new JaywayJsonFinder(root);

        // Scope to the dataset subtree
        List<JsonNode> dsNodes = finder.nodes("$.dataset");
        JaywayJsonFinder scoped = finder.at(dsNodes.get(0));

        // From scoped finder: read relative title
        List<String> scopedTitle = scoped.list("$.title");
        assertThat(scopedTitle).containsExactly("Example");

        // From scoped finder: listRoot() still sees the original root
        List<String> rootTitle = scoped.listRoot("$.other.title");
        assertThat(rootTitle).containsExactly("Else");
    }

    @ParameterizedTest(name = "empty or invalid jsonPath '{0}' returns empty list (list)")
    @ValueSource(strings = {"", "   ", "$.no.such.path"})
    void list_empty_or_invalid_path_returns_empty(String path) throws Exception {
        JsonNode root = jsonNode("{" + "\"dataset\":{\"title\":\"Example\",\"id\":123}" + "}");
        JaywayJsonFinder finder = new JaywayJsonFinder(root);

        List<String> result = finder.list(path);
        assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "empty or invalid jsonPath '{0}' returns empty list (nodes)")
    @ValueSource(strings = {"", "   ", "$.no.such.path"})
    void nodes_empty_or_invalid_path_returns_empty(String path) throws Exception {
        JsonNode root = jsonNode("{" + "\"dataset\":{\"title\":\"Example\",\"id\":123}" + "}");
        JaywayJsonFinder finder = new JaywayJsonFinder(root);

        List<com.fasterxml.jackson.databind.JsonNode> result = finder.nodes(path);
        assertThat(result).isEmpty();
    }
}
