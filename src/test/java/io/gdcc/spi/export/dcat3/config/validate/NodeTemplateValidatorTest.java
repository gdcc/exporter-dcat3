package io.gdcc.spi.export.dcat3.config.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class NodeTemplateValidatorTest {

    @Mock
    private NodeTemplate node;

    @BeforeEach
    void initMocks() {
        openMocks(this); // classic initialization
    }

    @Test
    @DisplayName("NodeTemplateValidator flags empty id and unknown type prefix")
    void nodeTemplate_invalid_cases() {
        Map<String, String> prefixes = Map.of("dcat", "http://www.w3.org/ns/dcat#");
        NodeTemplateValidator validator = new NodeTemplateValidator(prefixes);

        when(node.id()).thenReturn(" ");
        when(node.kind()).thenReturn("iri");
        when(node.type()).thenReturn("ex:Dataset");
        when(node.props()).thenReturn(Map.<String, ValueSource>of());

        List<ValidationMessage> messages = validator.validate(node);
        assertThat(messages).extracting(ValidationMessage::code).contains("DCATRSC-201", "DCATRSC-203");
    }

    @Test
    @DisplayName("NodeTemplateValidator accepts valid CURIE and bnode kind")
    void nodeTemplate_valid_cases() {
        Map<String, String> prefixes = Map.of("dcat", "http://www.w3.org/ns/dcat#", "ex", "http://example.org/");
        NodeTemplateValidator validator = new NodeTemplateValidator(prefixes);

        when(node.id()).thenReturn("n1");
        when(node.kind()).thenReturn("bnode");
        when(node.type()).thenReturn("ex:Dataset");
        when(node.props()).thenReturn(Map.<String, ValueSource>of());

        List<ValidationMessage> messages = validator.validate(node);
        assertThat(messages).isEmpty();
    }
}
