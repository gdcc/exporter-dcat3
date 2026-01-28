package io.gdcc.spi.export.dcat3.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public ResourceMapper(
            ResourceConfig resourceConfig, Prefixes prefixes, String resourceTypeCurieOrIri) {
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
                subject.addProperty(
                        RDF.type, model.createResource(prefixes.expand(resourceTypeCurieOrIri)));
            }
            resourceConfig
                    .props()
                    .forEach((id, valueSource) -> addProperty(model, subject, scoped, valueSource));
        }
        return model;
    }

    private Resource createSubject(Model model, JaywayJsonFinder finder) {
        String iri = resourceConfig.subject().iriConst();
        if (iri == null && resourceConfig.subject().iriTemplate() != null) {
            iri = resourceConfig.subject().iriTemplate();
        }
        if (iri == null
                && resourceConfig.subject().iriFormat() != null
                && resourceConfig.subject().iriJson() != null) {
            List<String> values = listScopedOrRoot(finder, resourceConfig.subject().iriJson());
            String value = values.isEmpty() ? null : values.get(0);
            if (value != null) {
                iri = resourceConfig.subject().iriFormat().replace("${value}", value);
            }
        }
        if (iri == null && resourceConfig.subject().iriJson() != null) {
            List<String> values = listScopedOrRoot(finder, resourceConfig.subject().iriJson());
            iri = values.isEmpty() ? null : values.get(0);
        }
        return (iri == null || iri.isBlank()) ? model.createResource() : model.createResource(iri);
    }

    private void addProperty(
            Model model, Resource subject, JaywayJsonFinder finder, ValueSource valueSource) {
        String predicateIri = prefixes.expand(valueSource.predicate());
        if (predicateIri == null) {
            return;
        }
        Property property = model.createProperty(predicateIri);
        for (RDFNode rdfNode : resolveObjects(model, finder, valueSource)) {
            subject.addProperty(property, rdfNode);
        }
    }

    private List<RDFNode> resolveObjects(
            Model model, JaywayJsonFinder finder, ValueSource valueSource) {
        return switch (valueSource.as()) {
            case "node-ref" -> buildNodeRefs(model, finder, valueSource);
            case "iri" -> valuesFromSource(finder, valueSource).stream()
                    .map(applyMapIfAny(valueSource))
                    .map(applyFormatIfAny(valueSource, finder)) // ${value}, ${1}, inline JSONPaths
                    .filter(s -> s != null && !s.isBlank()) // guard: avoid <rdfs:Resource/>
                    .map(model::createResource)
                    .collect(Collectors.toList());
            default -> valuesFromSource(finder, valueSource).stream()
                    .map(applyMapIfAny(valueSource))
                    .map(applyFormatIfAny(valueSource, finder))
                    .filter(Objects::nonNull)
                    .map(val -> literal(model, val, valueSource.lang(), valueSource.datatype()))
                    .collect(Collectors.toList());
        };
    }

    /**
     * Build node references for a property defined as "node-ref" in the config. This version
     * normalizes ${value} when used inside iriFormat, so that values like "text/plain;
     * charset=US-ASCII" never end up inside IRIs.
     */
    private List<RDFNode> buildNodeRefs(Model model, JaywayJsonFinder finder, ValueSource vs) {
        NodeTemplate nodeTemplate = resourceConfig.nodes().get(vs.nodeRef());
        if (nodeTemplate == null) {
            return Collections.singletonList(model.createResource()); // bnode
        }

        // Gather base values from iriJson (handles multi)
        List<String> bases;
        if ("iri".equals(nodeTemplate.kind())
                && nodeTemplate.iriJson() != null
                && !nodeTemplate.iriJson().isBlank()) {
            bases = listScopedOrRoot(finder, nodeTemplate.iriJson());
            if (!nodeTemplate.multi() && !bases.isEmpty()) {
                bases = Collections.singletonList(bases.get(0)); // collapse to single if not multi
            }
            if (bases.isEmpty()) {
                bases = Collections.singletonList(null);
            }
        } else {
            bases = Collections.singletonList(null);
        }

        List<RDFNode> out = new ArrayList<>(bases.size());
        for (String baseRaw : bases) {
            String iri = null;
            Resource resource;

            if ("iri".equals(nodeTemplate.kind())) {
                // 1) iriConst wins
                if (nodeTemplate.iriConst() != null && !nodeTemplate.iriConst().isBlank()) {
                    iri = nodeTemplate.iriConst();
                } else {
                    // Normalize the base candidate early (trims, strips parameters for media types)
                    String base = baseRaw == null ? null : baseRaw.trim();

                    // 2) node-level map (normalize lookup key; also strip parameters like ";
                    // charset=...")
                    if (base != null
                            && nodeTemplate.iriMap() != null
                            && !nodeTemplate.iriMap().isEmpty()) {
                        String key = stripParameters( base).toLowerCase();
                        // Try normalized key first, then the original (for backward compatibility)
                        iri =
                                nodeTemplate
                                        .iriMap()
                                        .getOrDefault(key, nodeTemplate.iriMap().get(base));
                    }

                    // 3) format: ${value} + inline JSONPath placeholders
                    if ((iri == null || iri.isBlank())
                            && nodeTemplate.iriFormat() != null
                            && !nodeTemplate.iriFormat().isBlank()) {
                        String normalizedBase = normalizeMediaTypeBase(base);
                        String formatted =
                                nodeTemplate.iriFormat().replace("${value}", normalizedBase);
                        formatted = resolveInlineJsonPlaceholders(formatted, finder);
                        iri = formatted;
                    }

                    // 4) last resort: only use base as IRI if it looks like an absolute IRI
                    if ((iri == null || iri.isBlank()) && looksLikeIri(base)) {
                        iri = base;
                    }
                }
                // guard: invalid or blank → fall back to bnode (prevents <false> crash)
                resource =
                        (iri == null || iri.isBlank() || !looksLikeIri(iri))
                                ? model.createResource()
                                : model.createResource(iri);
            } else {
                resource = model.createResource(); // bnode
            }

            // rdf:type if provided
            if (nodeTemplate.type() != null) {
                resource.addProperty(
                        RDF.type, model.createResource(prefixes.expand(nodeTemplate.type())));
            }

            // attach any nested properties
            nodeTemplate
                    .props()
                    .forEach(
                            (pid, pvs) -> {
                                Property property =
                                        model.createProperty(prefixes.expand(pvs.predicate()));
                                for (RDFNode obj : resolveObjects(model, finder, pvs)) {
                                    resource.addProperty(property, obj);
                                }
                            });

            out.add(resource);
        }

        return out;
    }

    /**
     * Strip parameters from a content-type-like value (e.g., "text/plain; charset=US-ASCII" ->
     * "text/plain").
     */
    private static String stripParameters(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        int i = t.indexOf(';');
        return (i >= 0) ? t.substring(0, i).trim() : t;
    }

    /**
     * Normalize a media-type-like base to a safe "type/subtype" token (lowercase, no
     * params/whitespace). This is especially useful when interpolating ${value} into IRIs such as
     * the IANA media-types registry path.
     */
    private static String normalizeMediaTypeBase(String base) {
        if (base == null) {
            return "";
        }
        String contentType = stripParameters( base); // remove "; charset=..." etc.
        contentType = contentType == null ? "" : contentType.trim().toLowerCase();
        String[] parts = contentType.split("/");
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return parts[0] + "/" + parts[1];
        }
        return contentType; // fallback: return trimmed lowercased token without params
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
            return values.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(values.get(0));
        }
        // If format contains inline JSONPaths or indexed placeholders, ensure we have a single base
        // value
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
            if (!valueSource.map().isEmpty()) {
                return valueSource.map().getOrDefault(s, null);
            }
            return s;
        };
    }

    private Function<String, String> applyFormatIfAny(
            ValueSource valueSource, JaywayJsonFinder finder) {
        return s -> {
            if (valueSource.format() == null || valueSource.format().isBlank()) {
                return s; // no formatting requested
            }
            // Start from format template
            String formatted = valueSource.format();

            // Legacy ${value}: use current s if provided, else resolve vs.json
            if (formatted.contains("${value}")) {
                String base = s;
                if ((base == null || base.isEmpty()) && valueSource.json() != null) {
                    List<String> values = listScopedOrRoot(finder, valueSource.json());
                    base = values.isEmpty() ? "" : values.get(0);
                }
                String normalizedBase = normalizeMediaTypeBase(base);
                formatted = formatted.replace("${value}", normalizedBase);
            }

            // Indexed ${1}, ${2}, ... from vs.jsonPaths
            if (valueSource.jsonPaths() != null && !valueSource.jsonPaths().isEmpty()) {
                for (int i = 0; i < valueSource.jsonPaths().size(); i++) {
                    String path = valueSource.jsonPaths().get(i);
                    List<String> values = listScopedOrRoot(finder, path);
                    String value = values.isEmpty() ? "" : values.get(0);
                    formatted = formatted.replace("${" + (i + 1) + "}", value);
                }
            }

            // Inline JSONPath placeholders: ${$.path} or ${$$.path}
            formatted = resolveInlineJsonPlaceholders(formatted, finder);
            return formatted;
        };
    }

    private String resolveInlineJsonPlaceholders(String format, JaywayJsonFinder finder) {
        StringBuilder out = new StringBuilder();
        int start = 0;
        while (true) {
            int open = format.indexOf("${", start);
            if (open < 0) {
                out.append(format.substring(start));
                break;
            }
            out.append(format, start, open);
            int close = format.indexOf("}", open + 2);
            if (close < 0) { // malformed, append rest
                out.append(format.substring(open));
                break;
            }
            String token = format.substring(open + 2, close);
            String replacement;
            if (token.startsWith("$$")) {
                List<String> vals = listScopedOrRoot(finder, token); // listScopedOrRoot handles $$
                replacement = vals.isEmpty() ? "" : vals.get(0);
            } else if (token.startsWith("$")) {
                List<String> vals = listScopedOrRoot(finder, token);
                replacement = vals.isEmpty() ? "" : vals.get(0);
            } else {
                // leave unknown tokens as-is (e.g., ${1} handled earlier)
                replacement = "${" + token + "}";
            }
            out.append(replacement);
            start = close + 1;
        }
        return out.toString();
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

    private static boolean looksLikeIri(String s) {
        // quick absolute IRI check (scheme ":" ...)
        return s != null && s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }


}
