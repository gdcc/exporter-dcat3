package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.gdcc.spi.export.dcat3.config.model.Element;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.RootConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidatorsTest {

    @Mock
    RootConfig root;

    @Mock
    Element element;

    @Mock
    ResourceConfig rc;

    @Test
    @DisplayName("Validators.validateRoot delegates to RootConfigValidator")
    void validateRoot_delegates() {
        when(root.prefixes()).thenReturn(Map.of());
        when(root.elements()).thenReturn(List.of());
        ValidationReport report = Validators.validateRoot(root);
        assertThat(report.messages()).extracting(ValidationMessage::code).contains("DCATCFG-001", "DCATCFG-010");
    }

    @Test
    @DisplayName("Validators.validateAll returns combined report for root + element configs")
    void validateAll_combined() {
        when(root.prefixes()).thenReturn(Map.of("ex", "http://example.org/"));
        when(element.id()).thenReturn("e1");
        when(root.elements()).thenReturn(List.of(element));

        Map<String, ResourceConfig> configs = Map.of("e1", rc);
        ValidationReport report = Validators.validateAll(root, configs);
        assertThat(report.messages()).isNotNull();
    }
}
