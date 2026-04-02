package io.gdcc.spi.export.dcat3.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.Subject;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

public class ResourceMapper {

    private final ResourceConfig resourceConfig;
    private final Prefixes prefixes;
    private final String resourceTypeCurieOrIri;

    public ResourceMapper(ResourceConfig resourceConfig, Prefixes prefixes, String resourceTypeCurieOrIri) {
        this.resourceConfig = resourceConfig;
        this.prefixes = prefixes;
        this.resourceTypeCurieOrIri = resourceTypeCurieOrIri;
    }

    public Model build(JaywayJsonFinder finder) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(prefixes.jena());

        List<JsonNode> scopes;
        if (resourceConfig.scopeJson() != null && !resourceConfig.scopeJson().isBlank()) {
            scopes = finder.nodes(resourceConfig.scopeJson());
            if (scopes.isEmpty()) {
                return model;
            }
        } else {
            scopes = Collections.singletonList(null);
        }

        for (JsonNode scopeNode : scopes) {
            JaywayJsonFinder scoped = (scopeNode == null) ? finder : finder.at(scopeNode);
            Resource subject = createSubject(model, scoped);

            if (resourceTypeCurieOrIri != null) {
                subject.addProperty(RDF.type, model.createResource(prefixes.expand(resourceTypeCurieOrIri)));
            }

            resourceConfig.props().forEach((id, valueSource) -> addProperty(model, subject, scoped, valueSource));
        }
        return model;
    }

    /**
     * Create subject resource for each scope.
     *
     * Supported:
     * - iriConst
     * - iriTemplate (treated as fixed string)
     * - iriJson (+ optional iriFormat)
     *
     * Formatting uses TemplateFormatter for consistency and supports inline JSON placeholders.
     */
    private Resource createSubject(Model model, JaywayJsonFinder finder) {
        Subject subjectCfg = resourceConfig.subject();

        String iri = subjectCfg.iriConst();
        if (iri == null && subjectCfg.iriTemplate() != null) {
            iri = subjectCfg.iriTemplate();
        }

        // Gather base for ${value} (optional)
        String base = null;
        if (iri == null && subjectCfg.iriJson() != null && !subjectCfg.iriJson().isBlank()) {
            List<String> values = listScopedOrRoot(finder, subjectCfg.iriJson());
            base = values.isEmpty() ? "" : values.get(0);
            iri = base; // fallback if no format provided
        }

        if (iri == null
                && subjectCfg.iriJsonPaths() != null
                && !subjectCfg.iriJsonPaths().isEmpty()) {
            // If only jsonPaths exist (no iriJson), we still can build via iriFormat
            iri = ""; // placeholder
        }

        if (subjectCfg.iriFormat() != null && !subjectCfg.iriFormat().isBlank()) {
            iri = TemplateFormatter.format(
                    subjectCfg.iriFormat(),
                    base == null ? "" : base,
                    subjectCfg.iriJsonPaths() == null ? java.util.Collections.emptyList() : subjectCfg.iriJsonPaths(),
                    finder,
                    s -> s == null ? "" : s.trim());
        }

        return isBlank(iri) ? model.createResource() : model.createResource(iri);
    }

    private void addProperty(Model model, Resource subject, JaywayJsonFinder finder, ValueSource valueSource) {
        String predicateIri = prefixes.expand(valueSource.predicate());
        if (predicateIri == null) {
            return;
        }
        Property property = model.createProperty(predicateIri);

        for (RDFNode rdfNode : resolveObjects(model, finder, valueSource)) {
            subject.addProperty(property, rdfNode);
        }
    }

    private List<RDFNode> resolveObjects(Model model, JaywayJsonFinder finder, ValueSource valueSource) {
        return switch (valueSource.as()) {
            case "node-ref" -> buildNodeRefs(model, finder, valueSource);
            case "iri" -> valuesFromSource(finder, valueSource).stream()
                    .map(applyMapIfAny(valueSource))
                    .map(applyFormatIfAny(valueSource, finder))
                    .map(ResourceMapper::trimToNull)
                    .filter(Objects::nonNull)
                    .filter(ResourceMapper::looksLikeIri)
                    .map(model::createResource)
                    .collect(Collectors.toList());
            default -> valuesFromSource(finder, valueSource).stream()
                    .map(applyMapIfAny(valueSource))
                    .map(applyFormatIfAny(valueSource, finder))
                    .map(ResourceMapper::trimToNull)
                    .filter(Objects::nonNull)
                    .map(val -> literal(model, val, valueSource.lang(), valueSource.datatype()))
                    .collect(Collectors.toList());
        };
    }

    /**
     * Build node references for a property defined as "node-ref" in the config.
     *
     * Issue #34 fix: do NOT emit empty typed nodes.
     * - kind=iri: if IRI resolves blank/invalid -> omit node (no bnode fallback)
     * - kind=bnode: emit only if at least one nested property emitted
     */
    private List<RDFNode> buildNodeRefs(Model model, JaywayJsonFinder finder, ValueSource vs) {
        NodeTemplate nodeTemplate = resourceConfig.nodes().get(vs.nodeRef());
        if (nodeTemplate == null) {
            // Config error: better to omit than to generate an empty bnode.
            return Collections.emptyList();
        }

        // Gather base values from iriJson (handles multi)
        List<String> bases;
        if ("iri".equals(nodeTemplate.kind())
                && nodeTemplate.iriJson() != null
                && !nodeTemplate.iriJson().isBlank()) {
            bases = listScopedOrRoot(finder, nodeTemplate.iriJson());
            if (!nodeTemplate.multi() && !bases.isEmpty()) {
                bases = Collections.singletonList(bases.get(0));
            }
            // If empty, nothing to emit for IRI nodes.
            if (bases.isEmpty()) {
                return Collections.emptyList();
            }
        } else {
            bases = Collections.singletonList(null);
        }

        List<RDFNode> out = new ArrayList<>(bases.size());

        for (String baseRaw : bases) {
            if ("iri".equals(nodeTemplate.kind())) {
                Resource iriNode = buildIriNode(model, finder, nodeTemplate, baseRaw);
                if (iriNode != null) {
                    out.add(iriNode);
                }
            } else {
                Resource bnode = buildBNode(model, finder, nodeTemplate);
                if (bnode != null) {
                    out.add(bnode);
                }
            }
        }

        return out;
    }

    private Resource buildIriNode(Model model, JaywayJsonFinder finder, NodeTemplate nodeTemplate, String baseRaw) {
        String iri = null;

        // 1) iriConst wins
        if (nodeTemplate.iriConst() != null && !nodeTemplate.iriConst().isBlank()) {
            iri = nodeTemplate.iriConst();
        } else {
            String base = baseRaw == null ? null : baseRaw.trim();

            // 2) node-level map (normalize lookup key; also strip parameters like "; charset=...")
            if (!isBlank(base)
                    && nodeTemplate.iriMap() != null
                    && !nodeTemplate.iriMap().isEmpty()) {
                String key = stripParameters(base).toLowerCase();
                iri = nodeTemplate
                        .iriMap()
                        .getOrDefault(key, nodeTemplate.iriMap().get(base));
            }

            // 3) format: TemplateFormatter (supports ${value} + inline JSON placeholders)
            if (isBlank(iri)
                    && nodeTemplate.iriFormat() != null
                    && !nodeTemplate.iriFormat().isBlank()) {
                iri = TemplateFormatter.format(
                        nodeTemplate.iriFormat(),
                        base,
                        Collections.emptyList(),
                        finder,
                        ResourceMapper::normalizeMediaTypeBase);
            }

            // 4) last resort: only use base as IRI if it looks like an absolute IRI
            if (isBlank(iri) && looksLikeIri(base)) {
                iri = base;
            }
        }

        iri = trimToNull(iri);

        // Issue #34: if we cannot resolve a proper IRI, omit entirely (no blank node fallback).
        if (iri == null || !looksLikeIri(iri)) {
            return null;
        }

        Resource resource = model.createResource(iri);

        // Attach nested properties (if any)
        int emittedProps = emitNestedProps(model, finder, nodeTemplate, resource);

        // rdf:type if provided (even if no nested props; IRI resource is not "empty")
        if (nodeTemplate.type() != null) {
            resource.addProperty(RDF.type, model.createResource(prefixes.expand(nodeTemplate.type())));
        }

        // Note: emittedProps is not used to suppress IRI nodes; the existence of the IRI is enough.
        return resource;
    }

    private Resource buildBNode(Model model, JaywayJsonFinder finder, NodeTemplate nodeTemplate) {
        Resource resource = model.createResource(); // blank node

        int emittedProps = emitNestedProps(model, finder, nodeTemplate, resource);

        // Issue #34: suppress typed-only (or completely empty) bnodes.
        if (emittedProps == 0) {
            // We did not add any statements for this bnode -> omit by returning null.
            // (No need to remove statements, since none were added.)
            return null;
        }

        // rdf:type only if something meaningful exists
        if (nodeTemplate.type() != null) {
            resource.addProperty(RDF.type, model.createResource(prefixes.expand(nodeTemplate.type())));
        }

        return resource;
    }

    private int emitNestedProps(Model model, JaywayJsonFinder finder, NodeTemplate nodeTemplate, Resource resource) {
        int emitted = 0;

        Map<String, ValueSource> nested = nodeTemplate.props();
        if (nested == null || nested.isEmpty()) {
            return 0;
        }

        for (Map.Entry<String, ValueSource> entry : nested.entrySet()) {
            ValueSource pvs = entry.getValue();
            String pred = prefixes.expand(pvs.predicate());
            if (pred == null) {
                continue;
            }
            Property property = model.createProperty(pred);

            List<RDFNode> objs = resolveObjects(model, finder, pvs);
            if (objs == null || objs.isEmpty()) {
                continue;
            }
            for (RDFNode obj : objs) {
                resource.addProperty(property, obj);
                emitted++;
            }
        }

        return emitted;
    }

    /** Strip parameters from a content-type-like value: "text/plain; charset=US-ASCII" -> "text/plain". */
    private static String stripParameters(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        int i = t.indexOf(';');
        return (i >= 0) ? t.substring(0, i).trim() : t;
    }

    /**
     * Normalize a media-type-like base to a safe "type/subtype" token (lowercase, no params/whitespace).
     * Used only when interpolating ${value} into IRIs (e.g., IANA media types).
     */
    private static String normalizeMediaTypeBase(String base) {
        if (base == null) {
            return "";
        }
        String contentType = stripParameters(base);
        contentType = contentType == null ? "" : contentType.trim();
        String[] parts = contentType.split("/");
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return parts[0] + "/" + parts[1];
        }
        return contentType;
    }

    private List<String> valuesFromSource(JaywayJsonFinder finder, ValueSource valueSource) {
        if (valueSource.constValue() != null) {
            return Collections.singletonList(valueSource.constValue());
        }
        if (valueSource.json() != null) {
            List<String> values = listScopedOrRoot(finder, valueSource.json());
            if (valueSource.multi()) {
                return values;
            }
            return values.isEmpty() ? Collections.emptyList() : Collections.singletonList(values.get(0));
        }
        // If format contains inline JSONPaths or indexed placeholders, ensure we have a single base value
        if (valueSource.format() != null && !valueSource.format().isBlank()) {
            return Collections.singletonList("");
        }
        return Collections.emptyList();
    }

    /** If JSONPath starts with "$$", query original root; else, current scope. */
    private List<String> listScopedOrRoot(JaywayJsonFinder finder, String jsonPath) {
        if (jsonPath != null && jsonPath.startsWith("$$")) {
            return finder.listRoot(jsonPath.substring(1)); // strip one '$'
        }
        return finder.list(jsonPath);
    }

    private Function<String, String> applyMapIfAny(ValueSource valueSource) {
        return s -> {
            if (s == null) {
                return null;
            }
            if (valueSource.map() != null && !valueSource.map().isEmpty()) {
                return valueSource.map().getOrDefault(s, null);
            }
            return s;
        };
    }

    /**
     * Unified formatting for literal/iri values. Delegates to TemplateFormatter.
     *
     * - Supports ${value}, ${1..n}, inline ${$.path}/${$$.path}
     * - Keeps existing "media type normalization" behavior for ${value}
     */
    private Function<String, String> applyFormatIfAny(ValueSource valueSource, JaywayJsonFinder finder) {
        return s -> {
            if (valueSource.format() == null || valueSource.format().isBlank()) {
                return s;
            }
            // legacy behavior: if ${value} exists and base is missing, resolve from vs.json
            String base = s;
            if ((base == null || base.isEmpty()) && valueSource.json() != null) {
                List<String> values = listScopedOrRoot(finder, valueSource.json());
                base = values.isEmpty() ? "" : values.get(0);
            }
            return TemplateFormatter.format(
                    valueSource.format(),
                    base,
                    valueSource.jsonPaths(),
                    finder,
                    ResourceMapper::normalizeMediaTypeBase);
        };
    }

    private Literal literal(Model model, String value, String lang, String datatypeIri) {
        // EXPAND CURIE datatypes to full IRIs before TypeMapper lookup
        if (datatypeIri != null && !datatypeIri.isBlank() && !datatypeIri.startsWith("http")) {
            String expanded = prefixes.expand(datatypeIri);
            if (expanded != null) {
                datatypeIri = expanded;
            }
        }
        if (datatypeIri != null && !datatypeIri.isBlank()) {
            RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(datatypeIri);
            return model.createTypedLiteral(value, dt);
        }
        if (lang != null && !lang.isBlank()) {
            return model.createLiteral(value, lang);
        }
        return model.createLiteral(value);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean looksLikeIri(String s) {
        // quick absolute IRI check (scheme ":" ...)
        return s != null && s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }
}
