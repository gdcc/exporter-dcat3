package io.gdcc.spi.export.dcat3.config.validate;

import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.*;

import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ResourceConfigValidator implements Validator<ResourceConfig> {
    private final Map<String, String> prefixes;

    public ResourceConfigValidator(Map<String, String> prefixes) {
        this.prefixes = prefixes;
    }

    @Override
    public List<ValidationMessage> validate(ResourceConfig resourceConfig) {
        List<ValidationMessage> out = new ArrayList<>();
        if (resourceConfig == null) {
            out.add(new ValidationMessage(Severity.ERROR, "DCATRSC-300", "resource", "ResourceConfig is null", null));
            return out;
        }

        // subject validation
        out.addAll(new SubjectValidator().validate(resourceConfig.subject()));

        // props: validate only when present and non-empty
        if (!isNullOrEmpty(resourceConfig.props())) {
            for (Map.Entry<String, ValueSource> e : resourceConfig.props().entrySet()) {
                out.addAll(new ValueSourceValidator(prefixes, "props[" + e.getKey() + "]").validate(e.getValue()));
            }
        }

        // nodes: validate only when present and non-empty
        if (!isNullOrEmpty(resourceConfig.nodes())) {
            for (Map.Entry<String, NodeTemplate> e : resourceConfig.nodes().entrySet()) {
                out.addAll(new NodeTemplateValidator(prefixes).validate(e.getValue()));
            }
        }
        // scopeJson is optional
        return out;
    }
}
