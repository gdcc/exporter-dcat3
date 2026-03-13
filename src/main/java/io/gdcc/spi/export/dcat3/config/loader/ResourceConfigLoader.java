package io.gdcc.spi.export.dcat3.config.loader;

import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.Subject;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ResourceConfigLoader: parses .properties-based resource mapping configuration.
 *
 * <p>Refactor (Step 6): final-field model classes (Subject, ResourceConfig, NodeTemplate) require
 * constructor-based creation. We accumulate values first, then build immutable instances once per
 * id.
 */
public class ResourceConfigLoader {

    private static final Pattern PROPERTY_PATTERN = Pattern.compile("^props\\.([^.]+)\\.(.+)$");
    private static final Pattern NODE_PATTERN = Pattern.compile("^nodes\\.([^.]+)\\.(.+)$");
    private static final Pattern NODE_PROPERTY_PATTERN = Pattern.compile("^props\\.([^.]+)\\.(.+)$");

    // NEW: subject indexed JSON: subject.iri.json.1, subject.iri.json.2, ...
    private static final Pattern SUBJECT_JSON_INDEXED = Pattern.compile("^subject\\.iri\\.json\\.(\\d+)$");

    public ResourceConfig load(InputStream in) throws IOException {
        Properties property = new Properties();
        property.load(in);

        // -------------------------
        // Subject accumulation
        // -------------------------
        SubjectAccumulator subjectAcc = new SubjectAccumulator();
        subjectAcc.iriConst = property.getProperty("subject.iri.const");
        subjectAcc.iriTemplate = property.getProperty("subject.iri.template");
        subjectAcc.iriJson = property.getProperty("subject.iri.json");
        subjectAcc.iriFormat = property.getProperty("subject.iri.format");

        // NEW: subject.iri.json.N -> iriJsonPaths (numeric order)
        subjectAcc.iriJsonPaths = readSubjectIndexedJsonPaths(property);

        Subject subject = subjectAcc.toSubject();

        // Scope JSON
        String scopeJson = property.getProperty("scope.json");

        // -------------------------
        // Top-level props accumulation
        // -------------------------
        Map<String, ValueSourceAccumulator> propAcc = new LinkedHashMap<>();
        for (String propertyName : property.stringPropertyNames()) {
            Matcher matcher = PROPERTY_PATTERN.matcher(propertyName);
            if (!matcher.matches()) {
                continue;
            }
            String id = matcher.group(1);
            String tail = matcher.group(2);
            String v = property.getProperty(propertyName);
            ValueSourceAccumulator acc = propAcc.computeIfAbsent(id, k -> new ValueSourceAccumulator());
            apply(acc, tail, v);
        }

        // finalize props
        Map<String, ValueSource> props = new LinkedHashMap<>();
        for (Map.Entry<String, ValueSourceAccumulator> e : propAcc.entrySet()) {
            props.put(e.getKey(), e.getValue().toValueSource());
        }

        // -------------------------
        // Nodes accumulation
        // -------------------------
        Map<String, NodeAccumulators> nodeAccumulatorMap = new LinkedHashMap<>();
        for (String propertyName : property.stringPropertyNames()) {
            Matcher propertyMatcher = NODE_PATTERN.matcher(propertyName);
            if (!propertyMatcher.matches()) {
                continue;
            }
            String nodeId = propertyMatcher.group(1);
            String tail = propertyMatcher.group(2);
            String v = property.getProperty(propertyName);

            NodeAccumulators nodeAccumulators =
                    nodeAccumulatorMap.computeIfAbsent(nodeId, k -> new NodeAccumulators(nodeId));

            switch (tail) {
                case "kind" -> nodeAccumulators.kind = v;
                case "iri.const" -> nodeAccumulators.iriConst = v;
                case "iri.json" -> nodeAccumulators.iriJson = v;
                case "iri.format" -> nodeAccumulators.iriFormat = v;
                case "type" -> nodeAccumulators.type = v;
                case "multi" -> nodeAccumulators.multi = Boolean.parseBoolean(v);
                default -> {
                    // existing: node.props.*
                    Matcher nodePropertyPatternMatcher = NODE_PROPERTY_PATTERN.matcher(tail);
                    if (nodePropertyPatternMatcher.matches()) {
                        String propId = nodePropertyPatternMatcher.group(1);
                        String propTail = nodePropertyPatternMatcher.group(2);
                        ValueSourceAccumulator vAcc =
                                nodeAccumulators.props.computeIfAbsent(propId, k -> new ValueSourceAccumulator());
                        apply(vAcc, propTail, v);
                    }
                    // node-level iriMap (nodes.X.map.KEY = IRI)
                    else if (tail.startsWith("map.")) {
                        String key = tail.substring("map.".length());
                        nodeAccumulators.iriMap.put(key, v);
                    }
                }
            }
        }

        // finalize nodes
        Map<String, NodeTemplate> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, NodeAccumulators> e : nodeAccumulatorMap.entrySet()) {
            NodeAccumulators na = e.getValue();
            Map<String, ValueSource> nodeProps = new LinkedHashMap<>();
            for (Map.Entry<String, ValueSourceAccumulator> pe : na.props.entrySet()) {
                nodeProps.put(pe.getKey(), pe.getValue().toValueSource());
            }
            NodeTemplate nodeTemplate = new NodeTemplate(
                    na.nodeId, na.kind, na.iriConst, na.iriJson, na.iriFormat, na.type, na.multi, na.iriMap, nodeProps);
            nodes.put(na.nodeId, nodeTemplate);
        }

        // Build final ResourceConfig via constructor
        return new ResourceConfig(subject, props, nodes, scopeJson);
    }

    private static List<String> readSubjectIndexedJsonPaths(Properties property) {
        // collect into a numeric map to preserve intended order even if Properties enumerates
        // differently
        Map<Integer, String> ordered = new TreeMap<>();
        for (String name : property.stringPropertyNames()) {
            Matcher m = SUBJECT_JSON_INDEXED.matcher(name);
            if (!m.matches()) continue;
            int idx = Integer.parseInt(m.group(1));
            ordered.put(idx, property.getProperty(name));
        }
        return ordered.isEmpty() ? Collections.emptyList() : new ArrayList<>(ordered.values());
    }

    /** Accumulator for Subject fields prior to construction. */
    static final class SubjectAccumulator {
        String iriConst, iriTemplate, iriJson, iriFormat;
        List<String> iriJsonPaths = Collections.emptyList();

        Subject toSubject() {
            return new Subject(iriConst, iriTemplate, iriJson, iriJsonPaths, iriFormat);
        }
    }

    /** Accumulator for ValueSource fields prior to construction. */
    static final class ValueSourceAccumulator {
        String predicate, as, lang, datatype, json, constValue, nodeRef, when, format;
        boolean multi;
        List<String> jsonPaths = new ArrayList<>();
        Map<String, String> map = new LinkedHashMap<>();

        ValueSource toValueSource() {
            return new ValueSource(
                    predicate, as, lang, datatype, json, constValue, jsonPaths, nodeRef, multi, when, map, format);
        }
    }

    /** Holds per-node accumulators: node-level fields + property accumulators. */
    static final class NodeAccumulators {
        final String nodeId;
        String kind;
        String iriConst;
        String iriJson;
        String iriFormat;
        String type;
        boolean multi;
        Map<String, String> iriMap = new LinkedHashMap<>();
        Map<String, ValueSourceAccumulator> props = new LinkedHashMap<>();

        NodeAccumulators(String nodeId) {
            this.nodeId = nodeId;
        }
    }

    /** Apply a single key/value to the accumulator. */
    private static void apply(ValueSourceAccumulator acc, String keyTail, String value) {
        switch (keyTail) {
            case "predicate" -> acc.predicate = value;
            case "as" -> acc.as = value;
            case "lang" -> acc.lang = value;
            case "datatype" -> acc.datatype = value;
            case "json" -> acc.json = value; // legacy single source
            case "const" -> acc.constValue = value;
            case "node" -> acc.nodeRef = value;
            case "multi" -> acc.multi = Boolean.parseBoolean(value);
            case "when" -> acc.when = value;
            case "format" -> acc.format = value; // formatter with placeholders
            default -> {
                if (keyTail.startsWith("json.")) {
                    // Supports json.1, json.2, ...
                    acc.jsonPaths.add(value);
                } else if (keyTail.startsWith("map.")) {
                    String k = keyTail.substring("map.".length());
                    acc.map.put(k, value);
                }
            }
        }
    }
}
