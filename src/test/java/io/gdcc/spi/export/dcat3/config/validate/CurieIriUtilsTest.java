package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CurieIriUtilsTest {

    @ParameterizedTest(name = "isCurie('{0}') => {1}")
    @CsvSource({
        "dcat:Dataset, true",
        "ex:Thing, true",
        "http://example.org/Dataset, false",
        "urn:uuid:1234, false",
        "notACurie, false",
        "'  dct:title  ', true"
    })
    @DisplayName("Detect CURIE strings")
    void isCurie_cases(String value, boolean expected) {
        assertThat(CurieIriUtils.isCurie(ValidationUtil.safeTrim(value))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "isIri('{0}') => {1}")
    @CsvSource({
        "http://example.org/Dataset, true",
        "https://example.org/Dataset, true",
        "urn:uuid:1234, true",
        "mailto:someone@example.org, true", // valid mailto
        "mailto:, false", // invalid (empty address)
        "dcat:Dataset, false",
        "notAnIri, false"
    })
    @DisplayName("Detect IRI strings")
    void isIri_cases(String value, boolean expected) {
        assertThat(CurieIriUtils.isIri(value)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "curieHasKnownPrefix('{0}') in {1} => {2}")
    @CsvSource({"dcat:Dataset, dcat, true", "dct:title, dct, true", "ex:Thing, dcat, false", "ex:Thing, ex, true"})
    @DisplayName("Known prefix detection in CURIEs")
    void curieHasKnownPrefix_cases(String curie, String known, boolean expected) {
        Map<String, String> prefixes = Map.of(known, "http://example.org/" + known + "#");
        assertThat(CurieIriUtils.curieHasKnownPrefix(curie, prefixes)).isEqualTo(expected);
    }
}
