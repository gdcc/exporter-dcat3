package io.gdcc.spi.export.dcat3.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateFormatterTest {

    private static JaywayJsonFinder finderFor(String json) throws Exception {
        return new JaywayJsonFinder(new ObjectMapper().readTree(json));
    }

    @Test
    @DisplayName("format() replaces ${value} using normalizer")
    void format_replaces_value_with_normalizer() throws Exception {
        JaywayJsonFinder finder = finderFor("{\"x\":\"ignored\"}");
        String out =
                TemplateFormatter.format("https://example.org/${value}/end", "  AbC  ", List.of(), finder, s -> s.trim()
                        .toLowerCase());

        assertThat(out).isEqualTo("https://example.org/abc/end");
    }

    @Test
    @DisplayName("format() replaces indexed ${1}/${2} from jsonPaths")
    void format_replaces_indexed_placeholders() throws Exception {
        JaywayJsonFinder finder = finderFor("{\"a\":\"ONE\",\"b\":\"TWO\"}");
        String out = TemplateFormatter.format("X-${1}-${2}-Y", null, List.of("$.a", "$.b"), finder, s -> s);

        assertThat(out).isEqualTo("X-ONE-TWO-Y");
    }

    @Test
    @DisplayName("format() resolves inline JSONPath placeholders (scoped and root)")
    void format_resolves_inline_json_placeholders() throws Exception {
        JaywayJsonFinder finder = finderFor("{\"env\":{\"apiBaseUrl\":\"https://acc.example/api/\"},\"id\":\"5\"}");

        String out = TemplateFormatter.format(
                "${$$.env.apiBaseUrl}access/datafile/${$.id}", null, List.of(), finder, s -> s);

        assertThat(out).isEqualTo("https://acc.example/api/access/datafile/5");
    }

    @Test
    @DisplayName("resolveInlineJsonPlaceholders leaves unknown tokens intact")
    void inline_unknown_tokens_left_intact() throws Exception {
        JaywayJsonFinder finder = finderFor("{\"id\":\"5\"}");
        String out = TemplateFormatter.resolveInlineJsonPlaceholders("A-${foo}-B", finder);
        assertThat(out).isEqualTo("A-${foo}-B");
    }
}
