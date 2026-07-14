package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IRIFixerTest {

    @ParameterizedTest(name = "buildValidUri(''{0}'') => ''{1}''")
    // spotless:off
    @CsvSource({
        "http://example.org/simple, http://example.org/simple",
        "'https://example.org/a path?q=x y#z z', 'https://example.org/a%20path?q=x%20y#z%20z'",
        "'https://user:pass@[2001:db8::1]:8080/p', 'https://user:pass@[2001:db8::1]:8080/p'",
        "'urn:uuid:550e8400-e29b-41d4-a716-446655440000', 'urn:uuid:550e8400-e29b-41d4-a716-446655440000'",
        "'https://example.org/café', 'https://example.org/caf%C3%A9'",
        "'https://example.org/a%20b', 'https://example.org/a%2520b'",
        "'', ''"
    })
    // spotless:on
    @DisplayName("Encode URI/IRI components using RFC 3986 safe characters")
    void buildValidUri_cases(String raw, String expected) {
        assertThat(IRIFixer.buildValidUri(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "buildValidIri(''{0}'') => ''{1}''")
    // spotless:off
    @CsvSource({
        "'https://example.org/café', 'https://example.org/café'",
        "'https://example.org/東京?q=naïve#crème brûlée', 'https://example.org/東京?q=naïve#crème%20brûlée'",
        "'https://example.org/a path?q=x y#z z', 'https://example.org/a%20path?q=x%20y#z%20z'",
        "'https://example.org/a%20b', 'https://example.org/a%2520b'",
        "'https://user:pass@[2001:db8::1]:8080/δοκιμή', 'https://user:pass@[2001:db8::1]:8080/δοκιμή'"
    })
    // spotless:on
    @DisplayName("Encode IRI components while preserving UCS characters")
    void buildValidIri_cases(String raw, String expected) {
        assertThat(IRIFixer.buildValidIri(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "query and fragment keep '/' and '?' in ''{0}''")
    @CsvSource({
        "'https://example.org/p?x=/a?b#f/a?b', 'https://example.org/p?x=/a?b#f/a?b'",
        "'https://example.org/p?x=a b#f g', 'https://example.org/p?x=a%20b#f%20g'"
    })
    @DisplayName("Apply query/fragment encoding rules")
    void buildValidUri_queryAndFragmentCases(String raw, String expected) {
        assertThat(IRIFixer.buildValidUri(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "isValidUri(''{0}'') => {1}")
    // spotless:off
    @CsvSource(
            value = {
                "http://example.org/path, true",
                "https://example.org, true",
                "urn:uuid:550e8400-e29b-41d4-a716-446655440000, true",
                "mailto:user@example.org, true",
                "ftp://ftp.example.org/file.txt, true",
                "http://example.org:8080/valid, true",
                "'http://example.org/path/with/slashes', true",
                "'http://example.org/path?query=a&b=c', true",
                "'http://example.org/path#fragment/test', true",
                "null, false",
                "'', false",
                "' ', false",
                "example.org/path, false",
                "://example.org, false",
                ":invalid, false",
                "1invalid:path, false",
                "http://, false",
                "'http://example.org/café', false",
                "'http://example.org/path?query=hello world', false",
                "'http://example.org/path%2', false",
                "'http://example.org/path%Z', false"
            },
            nullValues = {"null"})
    // spotless:on
    @DisplayName("Validate URI format with component-specific character checking")
    void isValidUri_cases(String iri, boolean expected) {
        assertThat(IRIFixer.isValidUri(iri)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "isValidIri(''{0}'') => {1}")
    // spotless:off
    @CsvSource(
            value = {
                "http://example.org/path, true",
                "'http://example.org/café', true",
                "'https://example.org/東京?q=naïve#crème', true",
                "'https://example.org/δοκιμή?κλειδί=τιμή#ενότητα', true",
                "'http://example.org/path?query=hello world', false",
                "'http://example.org/path%2', false",
                "'http://example.org/path%Z', false",
                "example.org/path, false",
                "http://, false",
                "null, false",
                "'', false"
            },
            nullValues = {"null"})
    // spotless:on
    @DisplayName("Validate IRI format with Unicode-aware component checking")
    void isValidIri_cases(String iri, boolean expected) {
        assertThat(IRIFixer.isValidIri(iri)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Component illegal chars: path=''{0}'' => {1}")
    @CsvSource({
        "'simple/path', true",
        "'path%20with%20spaces', true",
        "'path%2Fencoded', true",
        "'café', false",
        "'path with spaces', false",
        "'path%2', false",
        "'path%ZZ', false"
    })
    @DisplayName("Validate path component with proper encoding")
    void componentPath_validation(String path, boolean shouldBeValid) {
        String uri = "http://example.org/" + path;
        assertThat(IRIFixer.isValidUri(uri)).isEqualTo(shouldBeValid);
    }

    @ParameterizedTest(name = "IRI path chars: path=''{0}'' => {1}")
    @CsvSource({
        "'café', true",
        "'東京', true",
        "'path with spaces', false",
        "'path%2', false"
    })
    @DisplayName("Validate IRI path component with Unicode support")
    void componentPath_iriValidation(String path, boolean shouldBeValid) {
        String iri = "http://example.org/" + path;
        assertThat(IRIFixer.isValidIri(iri)).isEqualTo(shouldBeValid);
    }

    @ParameterizedTest(name = "Component illegal chars: query=''{0}'' => {1}")
    @CsvSource({
        "'key=value', true",
        "'a=1&b=2', true",
        "'key=value%20with%20encoded', true",
        "'path/with/slash', true",
        "'nested?query', true",
        "'café', false",
        "'unencoded space', false",
        "'%2', false"
    })
    @DisplayName("Validate query component with proper encoding")
    void componentQuery_validation(String query, boolean shouldBeValid) {
        String uri = "http://example.org/path?" + query;
        assertThat(IRIFixer.isValidUri(uri)).isEqualTo(shouldBeValid);
    }

    @ParameterizedTest(name = "IRI query chars: query=''{0}'' => {1}")
    @CsvSource({
        "'café', true",
        "'κλειδί=τιμή', true",
        "'unencoded space', false",
        "'%2', false"
    })
    @DisplayName("Validate IRI query component with Unicode support")
    void componentQuery_iriValidation(String query, boolean shouldBeValid) {
        String iri = "http://example.org/path?" + query;
        assertThat(IRIFixer.isValidIri(iri)).isEqualTo(shouldBeValid);
    }

    @ParameterizedTest(name = "Component illegal chars: fragment=''{0}'' => {1}")
    @CsvSource({
        "'section', true",
        "'section/subsection', true",
        "'section?param=value', true",
        "'section%20encoded', true",
        "'café', false",
        "'section with spaces', false",
        "'%2', false",
        "'%GG', false"
    })
    @DisplayName("Validate fragment component with proper encoding")
    void componentFragment_validation(String fragment, boolean shouldBeValid) {
        String uri = "http://example.org/path#" + fragment;
        assertThat(IRIFixer.isValidUri(uri)).isEqualTo(shouldBeValid);
    }

    @ParameterizedTest(name = "IRI fragment chars: fragment=''{0}'' => {1}")
    @CsvSource({
        "'café', true",
        "'節', true",
        "'section with spaces', false",
        "'%GG', false"
    })
    @DisplayName("Validate IRI fragment component with Unicode support")
    void componentFragment_iriValidation(String fragment, boolean shouldBeValid) {
        String iri = "http://example.org/path#" + fragment;
        assertThat(IRIFixer.isValidIri(iri)).isEqualTo(shouldBeValid);
    }
}
