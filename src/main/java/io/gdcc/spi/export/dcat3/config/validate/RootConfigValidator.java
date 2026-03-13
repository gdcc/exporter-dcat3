package io.gdcc.spi.export.dcat3.config.validate;

import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.*;

import io.gdcc.spi.export.dcat3.config.model.Element;
import io.gdcc.spi.export.dcat3.config.model.Relation;
import io.gdcc.spi.export.dcat3.config.model.RootConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RootConfigValidator implements Validator<RootConfig> {
    @Override
    public List<ValidationMessage> validate(RootConfig root) {
        List<ValidationMessage> out = new ArrayList<>();
        if (root == null) {
            out.add(new ValidationMessage(
                    Severity.ERROR, "DCATCFG-000", "root", "RootConfig is null", "Ensure loader returned a config"));
            return out;
        }
        // prefixes
        Map<String, String> prefixes = root.prefixes();
        if (isNullOrEmpty(prefixes)) {
            out.add(new ValidationMessage(
                    Severity.WARNING,
                    "DCATCFG-001",
                    "root.prefixes",
                    "No prefixes configured; CURIE expansion will fail",
                    "Add prefixes like dcat, dct, vcard, foaf"));
        } else {
            prefixes.forEach((key, value) -> {
                if (isBlank(key)) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-002",
                            "root.prefixes",
                            "Prefix key is empty",
                            "Use a non-empty key"));
                }
                if (isBlank(value) || !CurieIriUtils.isIri(value)) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-003",
                            "root.prefixes." + key,
                            "Prefix IRI is missing or not an IRI: " + value,
                            "Provide http(s)/urn IRI"));
                }
            });
        }
        // elements
        if (isNullOrEmpty(root.elements())) {
            out.add(new ValidationMessage(
                    Severity.WARNING,
                    "DCATCFG-010",
                    "root.elements",
                    "No elements configured",
                    "Add at least dataset/distribution elements"));
        } else {
            for (Element element : root.elements()) {
                String path = "root.elements[id=" + (element != null ? element.id() : "null") + "]";
                if (element == null) {
                    out.add(new ValidationMessage(Severity.ERROR, "DCATCFG-011", path, "Element is null", null));
                    continue;
                }
                if (isBlank(element.id())) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-012",
                            path + ".id",
                            "Element id is empty",
                            "Provide a stable identifier"));
                }
                if (isBlank(element.typeCurieOrIri())) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-013",
                            path + ".typeCurieOrIri",
                            "Missing RDF type",
                            "Provide a DCAT type CURIE or IRI"));
                } else {
                    String trimmed = safeTrim(element.typeCurieOrIri());
                    if (CurieIriUtils.isCurie(trimmed) && !CurieIriUtils.curieHasKnownPrefix(trimmed, prefixes)) {
                        out.add(new ValidationMessage(
                                Severity.ERROR,
                                "DCATCFG-014",
                                path + ".typeCurieOrIri",
                                "Unknown CURIE prefix: " + trimmed,
                                "Add prefix '" + trimmed.substring(0, trimmed.indexOf(':')) + "' to root.prefixes"));
                    }
                    if (!CurieIriUtils.isCurie(trimmed) && !CurieIriUtils.isIri(trimmed)) {
                        out.add(new ValidationMessage(
                                Severity.ERROR,
                                "DCATCFG-015",
                                path + ".typeCurieOrIri",
                                "Not a CURIE or IRI: " + trimmed,
                                null));
                    }
                }
                if (isBlank(element.file())) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-016",
                            path + ".file",
                            "Missing element mapping file",
                            "Set element.<id>.file in properties"));
                }
            }
        }
        // relations
        if (!isNullOrEmpty(root.relations())) {
            for (Relation relation : root.relations()) {
                String path = "root.relations["
                        + (relation != null ? relation.subjectElementId() + "->" + relation.objectElementId() : "null")
                        + "]";
                if (relation == null) {
                    out.add(new ValidationMessage(Severity.ERROR, "DCATCFG-020", path, "Relation is null", null));
                    continue;
                }
                if (isBlank(relation.subjectElementId())) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-021",
                            path + ".subjectElementId",
                            "Missing subject element id",
                            null));
                }
                if (isBlank(relation.objectElementId())) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-022",
                            path + ".objectElementId",
                            "Missing object element id",
                            null));
                }
                if (isBlank(relation.predicateCurieOrIri())) {
                    out.add(new ValidationMessage(
                            Severity.ERROR,
                            "DCATCFG-023",
                            path + ".predicateCurieOrIri",
                            "Missing relation predicate",
                            null));
                } else {
                    String p = safeTrim(relation.predicateCurieOrIri());
                    if (CurieIriUtils.isCurie(p) && !CurieIriUtils.curieHasKnownPrefix(p, prefixes)) {
                        out.add(new ValidationMessage(
                                Severity.ERROR,
                                "DCATCFG-024",
                                path + ".predicateCurieOrIri",
                                "Unknown CURIE prefix: " + p,
                                null));
                    }
                    if (!CurieIriUtils.isCurie(p) && !CurieIriUtils.isIri(p)) {
                        out.add(new ValidationMessage(
                                Severity.ERROR,
                                "DCATCFG-025",
                                path + ".predicateCurieOrIri",
                                "Not a CURIE or IRI: " + p,
                                null));
                    }
                }
            }
        }
        return out;
    }
}
