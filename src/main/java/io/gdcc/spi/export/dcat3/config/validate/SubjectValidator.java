package io.gdcc.spi.export.dcat3.config.validate;

import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.hasText;

import io.gdcc.spi.export.dcat3.config.model.Subject;
import java.util.ArrayList;
import java.util.List;

public final class SubjectValidator implements Validator<Subject> {

    @Override
    public List<ValidationMessage> validate(Subject subject) {
        List<ValidationMessage> out = new ArrayList<>();

        if (subject == null) {
            out.add(new ValidationMessage(Severity.ERROR, "DCATRSC-000", "resource.subject", "Subject is null", null));
            return out;
        }

        boolean hasJsonPaths =
                subject.iriJsonPaths() != null && !subject.iriJsonPaths().isEmpty();

        // If no way to mint IRI is provided, warn
        boolean hasSource = hasText(subject.iriConst())
                || hasText(subject.iriTemplate())
                || hasText(subject.iriJson())
                || hasJsonPaths;

        if (!hasSource) {
            out.add(new ValidationMessage(
                    Severity.WARNING,
                    "DCATRSC-001",
                    "resource.subject",
                    "Subject IRI source not provided (iriConst/iriTemplate/iriJson/iriJsonPaths)",
                    "Provide at least one"));
        }

        // If format is provided, check that template or json exists (or jsonPaths)
        if (hasText(subject.iriFormat())) {
            boolean ok = hasText(subject.iriTemplate()) || hasText(subject.iriJson()) || hasJsonPaths;

            if (!ok) {
                out.add(new ValidationMessage(
                        Severity.ERROR,
                        "DCATRSC-002",
                        "resource.subject.iriFormat",
                        "iriFormat provided but neither iriTemplate nor iriJson nor iriJsonPaths exists",
                        null));
            }
        }

        return out;
    }
}
