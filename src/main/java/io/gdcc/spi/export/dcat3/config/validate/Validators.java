package io.gdcc.spi.export.dcat3.config.validate;

import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.RootConfig;
import java.util.Map;

public final class Validators {
    private Validators() {}

    public static ValidationReport validateRoot(RootConfig root) {
        ValidationReport report = new ValidationReport();
        report.addAll(new RootConfigValidator().validate(root));
        return report;
    }

    public static ValidationReport validateResource(ResourceConfig rc, Map<String, String> prefixes) {
        ValidationReport report = new ValidationReport();
        report.addAll(new ResourceConfigValidator(prefixes).validate(rc));
        return report;
    }

    /**
     * Validate all resource configs loaded for elements; caller provides a map of
     * elementId->ResourceConfig
     */
    public static ValidationReport validateAll(RootConfig root, Map<String, ResourceConfig> elementConfigs) {
        ValidationReport report = validateRoot(root);
        Map<String, String> prefixes = (root != null) ? root.prefixes() : null;
        if (elementConfigs != null) {
            for (Map.Entry<String, ResourceConfig> e : elementConfigs.entrySet()) {
                report.addAll(new ResourceConfigValidator(prefixes).validate(e.getValue()));
            }
        }
        return report;
    }
}
