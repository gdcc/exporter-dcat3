package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.gdcc.spi.export.dcat3.config.model.Element;
import io.gdcc.spi.export.dcat3.config.model.Relation;
import io.gdcc.spi.export.dcat3.config.model.RootConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RootConfigValidatorTest {

    @Mock
    RootConfig root;

    @Mock
    Element element;

    @Mock
    Relation relation;

    @Test
    @DisplayName("RootConfigValidator warns on missing prefixes and elements")
    void rootConfig_missing_prefixes_elements() {
        RootConfigValidator validator = new RootConfigValidator();
        when(root.prefixes()).thenReturn(Map.of());
        when(root.elements()).thenReturn(List.of());
        when(root.relations()).thenReturn(List.of());

        List<ValidationMessage> messages = validator.validate(root);
        assertThat(messages).extracting(ValidationMessage::code).contains("DCATCFG-001", "DCATCFG-010");
    }

    @Test
    @DisplayName("RootConfigValidator detects invalid element type and missing file")
    void rootConfig_invalid_element() {
        RootConfigValidator validator = new RootConfigValidator();
        when(root.prefixes()).thenReturn(Map.of("dcat", "http://www.w3.org/ns/dcat#"));
        when(element.id()).thenReturn("el1");
        when(element.typeCurieOrIri()).thenReturn("invalid-type");
        when(element.file()).thenReturn(" ");
        when(root.elements()).thenReturn(List.of(element));
        when(root.relations()).thenReturn(List.of());

        List<ValidationMessage> messages = validator.validate(root);
        assertThat(messages).extracting(ValidationMessage::code).contains("DCATCFG-015", "DCATCFG-016");
    }
}
