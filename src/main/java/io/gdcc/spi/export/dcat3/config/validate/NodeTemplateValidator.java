package io.gdcc.spi.export.dcat3.config.validate;

import static io.gdcc.spi.export.dcat3.config.validate.ValidationUtil.*;

import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NodeTemplateValidator implements Validator<NodeTemplate> {
    private final Map<String, String> prefixes;

    public NodeTemplateValidator(Map<String, String> prefixes) {
        this.prefixes = prefixes;
    }

    @Override
    public List<ValidationMessage> validate(NodeTemplate nodeTemplate) {
        List<ValidationMessage> out = new ArrayList<>();
        if (nodeTemplate == null) {
            out.add(new ValidationMessage(Severity.ERROR, "DCATRSC-200", "node", "NodeTemplate is null", null));
            return out;
        }

        String base = "nodes[id=" + nodeTemplate.id() + "]";

        // id must have text
        if (isBlank(nodeTemplate.id())) {
            out.add(new ValidationMessage(Severity.ERROR, "DCATRSC-201", base + ".id", "Node id is empty", null));
        }

        // kind must be either bnode or iri when provided
        if (nodeTemplate.kind() != null
                && !(nodeTemplate.kind().equals("bnode") || nodeTemplate.kind().equals("iri"))) {
            out.add(new ValidationMessage(
                    Severity.ERROR,
                    "DCATRSC-202",
                    base + ".kind",
                    "Invalid kind: " + nodeTemplate.kind(),
                    "Use bnode/iri"));
        }

        // type must be CURIE or IRI when provided
        if (hasText(nodeTemplate.type())) {
            String t = safeTrim(nodeTemplate.type());
            if (CurieIriUtils.isCurie(t) && !CurieIriUtils.curieHasKnownPrefix(t, prefixes)) {
                out.add(new ValidationMessage(
                        Severity.ERROR, "DCATRSC-203", base + ".type", "Unknown CURIE prefix: " + t, null));
            }
            if (!CurieIriUtils.isCurie(t) && !CurieIriUtils.isIri(t)) {
                out.add(new ValidationMessage(
                        Severity.ERROR, "DCATRSC-204", base + ".type", "Not a CURIE or IRI: " + t, null));
            }
        }

        // props: validate only when present and non-empty
        if (!isNullOrEmpty(nodeTemplate.props())) {
            for (Map.Entry<String, ValueSource> e : nodeTemplate.props().entrySet()) {
                out.addAll(
                        new ValueSourceValidator(prefixes, base + ".props[" + e.getKey() + "]").validate(e.getValue()));
            }
        }
        return out;
    }
}
