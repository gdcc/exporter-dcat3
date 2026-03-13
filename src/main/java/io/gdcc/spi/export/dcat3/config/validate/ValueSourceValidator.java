package io.gdcc.spi.export.dcat3.config.validate;

import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.hasText;
import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.isBlank;
import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.isNullOrEmpty;
import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.safeTrim;

import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ValueSourceValidator implements Validator<ValueSource> {
    private final Map<String, String> prefixes;
    private final String pathPrefix;

    public ValueSourceValidator(Map<String, String> prefixes, String pathPrefix) {
        this.prefixes = prefixes;
        this.pathPrefix = pathPrefix;
    }

    @Override
    public List<ValidationMessage> validate(ValueSource valueSource) {
        List<ValidationMessage> out = new ArrayList<>();
        String path = pathPrefix;
        if (valueSource == null) {
            out.add(new ValidationMessage(Severity.ERROR, "DCATRSC-100", path, "ValueSource is null", null));
            return out;
        }
        // predicate
        if (isBlank(valueSource.predicate())) {
            out.add(new ValidationMessage(
                    Severity.ERROR, "DCATRSC-101", path + ".predicate", "Missing predicate", null));
        } else {
            String p = safeTrim(valueSource.predicate());
            if (CurieIriUtils.isCurie(p) && !CurieIriUtils.curieHasKnownPrefix(p, prefixes)) {
                out.add(new ValidationMessage(
                        Severity.ERROR, "DCATRSC-102", path + ".predicate", "Unknown CURIE prefix: " + p, null));
            }
            if (!CurieIriUtils.isCurie(p) && !CurieIriUtils.isIri(p)) {
                out.add(new ValidationMessage(
                        Severity.ERROR, "DCATRSC-103", path + ".predicate", "Not a CURIE or IRI: " + p, null));
            }
        }
        // as
        if (hasText(valueSource.as())) {
            String as = safeTrim(valueSource.as());
            if (!(as.equals("literal") || as.equals("iri") || as.equals("node-ref"))) {
                out.add(new ValidationMessage(
                        Severity.ERROR,
                        "DCATRSC-104",
                        path + ".as",
                        "Invalid 'as' value: " + as,
                        "Use literal|iri|node-ref"));
            }
            if (as.equals("node-ref") && isBlank(valueSource.nodeRef())) {
                out.add(new ValidationMessage(
                        Severity.ERROR, "DCATRSC-105", path + ".nodeRef", "node-ref requires nodeRef", null));
            }
        }
        // sources
        boolean hasSource = hasText(valueSource.json())
                || hasText(valueSource.constValue())
                || !isNullOrEmpty(valueSource.jsonPaths())
                || hasText(valueSource.nodeRef());
        if (!hasSource) {
            out.add(new ValidationMessage(
                    Severity.WARNING,
                    "DCATRSC-106",
                    path,
                    "No source configured (json/const/json.*/node) for value",
                    null));
        }
        return out;
    }
}
