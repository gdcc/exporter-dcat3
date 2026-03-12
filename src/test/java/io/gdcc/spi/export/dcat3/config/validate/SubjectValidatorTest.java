package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;

import io.gdcc.spi.export.dcat3.config.model.Subject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubjectValidatorTest {

    @Test
    @DisplayName("Warns DCATRSC-001 when no subject IRI source is provided")
    void warns_when_no_source() {
        Subject s = new Subject(null, null, null, List.of(), null);

        SubjectValidator v = new SubjectValidator();
        List<ValidationMessage> msgs = v.validate(s);

        assertThat(msgs).anyMatch(m -> "DCATRSC-001".equals(m.code()));
        assertThat(msgs).noneMatch(m -> "DCATRSC-002".equals(m.code()));
    }

    @Test
    @DisplayName("Errors DCATRSC-002 when iriFormat is provided without template/json/jsonPaths")
    void errors_when_format_without_any_source() {
        Subject s = new Subject(null, null, null, List.of(), "${1}x/${2}");

        SubjectValidator v = new SubjectValidator();
        List<ValidationMessage> msgs = v.validate(s);

        assertThat(msgs).anyMatch(m -> "DCATRSC-002".equals(m.code()));
    }

    @Test
    @DisplayName("Does NOT error when iriFormat is provided with iriJsonPaths")
    void ok_when_format_with_jsonpaths() {
        Subject s = new Subject(null, null, null, List.of("$.a", "$.b"), "${1}/${2}");

        SubjectValidator v = new SubjectValidator();
        List<ValidationMessage> msgs = v.validate(s);

        assertThat(msgs).noneMatch(m -> "DCATRSC-002".equals(m.code()));
    }

    @Test
    @DisplayName("Does NOT error when iriFormat is provided with iriJson")
    void ok_when_format_with_json() {
        Subject s = new Subject(null, null, "$.id", List.of(), "http://x/${value}");

        SubjectValidator v = new SubjectValidator();
        List<ValidationMessage> msgs = v.validate(s);

        assertThat(msgs).noneMatch(m -> "DCATRSC-002".equals(m.code()));
    }
}