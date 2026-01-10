package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidationUtilTest {

    @ParameterizedTest(name = "isBlank('{0}') => {1}")
    // Encode null/empty in CsvSource for multi-arg tests:
    // - unquoted empty => null
    // - quoted empty ('') => empty string
    // - 'null' token converted to Java null via nullValues
    // spotless:off
    @CsvSource(
            value = {
                "null, true", // null -> blank
                "'', true", // empty string -> blank
                "' ', true", // space -> blank
                "'\t', true", // tab -> blank (real TAB char between quotes)
                "' a ', false", // just a character
                "'0', false"
            },
            nullValues = {"null"})
    // spotless:on
    @DisplayName("isBlank should consider null/empty/whitespace as blank")
    void isBlank_cases(String input, boolean expected) {
        assertThat(ValidationUtil.isBlank(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "hasText('{0}') => {1}")
    // spotless:off
    @CsvSource(
            value = {
                "null, false", // null -> no text
                "'', false", // empty string -> no text
                "' ', false", // space -> no text
                "'\t', false", // tab -> no text
                "' a ', true", // just a character
                "'text', true"
            },
            nullValues = {"null"})
    // spotless:on
    @DisplayName("hasText should be true only for non-blank strings")
    void hasText_cases(String input, boolean expected) {
        assertThat(ValidationUtil.hasText(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "safeTrim('{0}') => '{1}'")
    // spotless:off
    @CsvSource(
            value = {
                "null, ''", // null -> empty string
                "'', ''", // empty -> empty
                "' ', ''", // spaces -> trimmed to empty
                "' a ', 'a'",
                "'text', 'text'"
            },
            nullValues = {"null"})
    // spotless:on
    @DisplayName("safeTrim should trim and return empty string for null")
    void safeTrim_cases(String input, String expected) {
        assertThat(ValidationUtil.safeTrim(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "isNullOrEmpty(Collection) => {0}")
    @CsvSource({"true", "false"})
    void isNullOrEmpty_collection(boolean empty) {
        assertThat(ValidationUtil.isNullOrEmpty(empty ? List.of() : List.of("x"))).isEqualTo(empty);
        assertThat(ValidationUtil.isNullOrEmpty((List<?>) null)).isTrue();
    }

    @ParameterizedTest(name = "isNullOrEmpty(Map) => {0}")
    @CsvSource({"true", "false"})
    void isNullOrEmpty_map(boolean empty) {
        assertThat(ValidationUtil.isNullOrEmpty(empty ? Map.of() : Map.of("k", "v")))
                .isEqualTo(empty);
        assertThat(ValidationUtil.isNullOrEmpty((Map<?, ?>) null)).isTrue();
    }
}
