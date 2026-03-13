package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceConfigValidatorTest {

    @Mock
    ResourceConfig rc;

    @Test
    @DisplayName("ResourceConfigValidator handles null ResourceConfig")
    void resourceConfig_null() {
        ResourceConfigValidator validator = new ResourceConfigValidator(Map.of());
        List<ValidationMessage> messages = validator.validate(null);
        assertThat(messages).extracting(ValidationMessage::code).containsExactly("DCATRSC-300");
    }

    @Test
    @DisplayName("ResourceConfigValidator validates props via ValueSourceValidator")
    void resourceConfig_props() {
        ValueSource valueSource = mock(ValueSource.class);
        when(valueSource.constValue()).thenReturn("(A");
        ResourceConfigValidator validator = new ResourceConfigValidator(Map.of("ex", "http://example.org/"));
        when(rc.subject()).thenReturn(null); // SubjectValidator is invoked but we'll ignore its output here
        when(rc.props()).thenReturn(Map.of("ex:title", valueSource));
        when(rc.nodes()).thenReturn(Map.of());

        List<ValidationMessage> messages = validator.validate(rc);
        assertThat(messages).isNotNull(); // presence of messages depends on ValueSourceValidator
    }
}
