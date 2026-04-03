package io.gdcc.spi.export.dcat3.config.loader;

import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.Subject;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ResourceConfigLoader: parses .properties-based resource mapping configuration.
 * Deterministic ordering: see class comments.
 */
public class ResourceConfigLoader {

    private static final Pattern PROPERTY_PATTERN = Pattern.compile("^props\\.([^.]+)\\.(.+)$");
    private static final Pattern NODE_PATTERN = Pattern.compile("^nodes\\.([^.]+)\\.(.+)$");
    private static final Pattern NODE_PROPERTY_PATTERN = Pattern.compile("^props\\.([^.]+)\\.(.+)$");
    private static final Pattern SUBJECT_JSON_INDEXED = Pattern.compile("^subject\\.iri\\.json\\.(\\d+)$");

    public ResourceConfig load(InputStream in) throws IOException {
        // 3.5: fail cleanly on missing mapping resource
        if (in == null) {
            throw new IllegalArgumentException(
                    "ResourceConfigLoader.load: InputStream is null (mapping resource not found)");
        }

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

        // Finalize props (deterministic alphabetical order by prop id)
        Map<String, ValueSource> props = new LinkedHashMap<>();
        for (String propId : sortedKeys(propAcc)) {
            ValueSourceAccumulator acc = propAcc.get(propId);
            props.put(propId, acc.toValueSource());
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
                case "onUnMappedValue" -> nodeAccumulators.onUnMappedValue = v;
                case "onNoInputValue" -> nodeAccumulators.onNoInputValue = v;
                default -> {
                    // node props: nodes.<nodeId>.props.<propId>.<...>
                    Matcher nodePropertyPatternMatcher = NODE_PROPERTY_PATTERN.matcher(tail);
                    if (nodePropertyPatternMatcher.matches()) {
                        String propId = nodePropertyPatternMatcher.group(1);
                        String propTail = nodePropertyPatternMatcher.group(2);
                        ValueSourceAccumulator vAcc =
                                nodeAccumulators.props.computeIfAbsent(propId, k -> new ValueSourceAccumulator());
                        apply(vAcc, propTail, v);
                    }
                    // node-level iriMap: nodes.<nodeId>.map.<key> = <iri>
                    else if (tail.startsWith("map.")) {
                        String key = tail.substring("map.".length());
                        nodeAccumulators.iriMap.put(key, v);
                    }
                }
            }
        }

        // Finalize nodes (deterministic alphabetical order by node id)
        Map<String, NodeTemplate> nodes = new LinkedHashMap<>();
        for (String nodeId : sortedKeys(nodeAccumulatorMap)) {
            NodeAccumulators na = nodeAccumulatorMap.get(nodeId);

            // Finalize node props deterministically (alphabetical by prop id)
            Map<String, ValueSource> nodeProps = new LinkedHashMap<>();
            for (String propId : sortedKeys(na.props)) {
                nodeProps.put(propId, na.props.get(propId).toValueSource());
            }

            // Sort node iriMap deterministically (alphabetical by map key)
            Map<String, String> iriMapSorted = sortedByKey(na.iriMap);

            NodeTemplate nodeTemplate = new NodeTemplate(
                    na.nodeId,
                    na.kind,
                    na.iriConst,
                    na.iriJson,
                    na.iriFormat,
                    na.type,
                    na.multi,
                    iriMapSorted,
                    nodeProps,
                    na.onUnMappedValue,
                    na.onNoInputValue);

            nodes.put(na.nodeId, nodeTemplate);
        }

        // Build final ResourceConfig
        return new ResourceConfig(subject, props, nodes, scopeJson);
    }

    private static List<String> readSubjectIndexedJsonPaths(Properties property) {
        // Collect into a numeric map to preserve intended order even if Properties enumerates differently
        Map<Integer, String> ordered = new TreeMap<>();
        for (String name : property.stringPropertyNames()) {
            Matcher m = SUBJECT_JSON_INDEXED.matcher(name);
            if (!m.matches()) continue;
            int idx = Integer.parseInt(m.group(1));
            ordered.put(idx, property.getProperty(name));
        }
        return ordered.isEmpty() ? Collections.emptyList() : new ArrayList<>(ordered.values());
    }

    /**
     * Deterministic alphabetical key ordering helper.
     */
    private static <V> List<String> sortedKeys(Map<String, V> map) {
        if (map == null || map.isEmpty()) return Collections.emptyList();
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    /**
     * Return a LinkedHashMap with the same entries as {@code in}, but in alphabetical key order.
     */
    private static <V> Map<String, V> sortedByKey(Map<String, V> in) {
        if (in == null || in.isEmpty()) {
            return in == null ? null : Collections.emptyMap();
        }
        Map<String, V> out = new LinkedHashMap<>();
        for (String k : sortedKeys(in)) {
            out.put(k, in.get(k));
        }
        return out;
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
        String onUnMappedValue, onNoInputValue;
        String mapEmpty, mapNonEmpty;
        boolean multi;

        Map<Integer, String> indexedJsonPaths = new TreeMap<>();
        List<String> jsonPaths = new ArrayList<>();

        Map<String, String> map = new LinkedHashMap<>();

        ValueSource toValueSource() {
            List<String> ordered = new ArrayList<>();
            ordered.addAll(indexedJsonPaths.values());
            ordered.addAll(jsonPaths);

            Map<String, String> sortedMap = sortedByKey(map);

            return new ValueSource(
                    predicate,
                    as,
                    lang,
                    datatype,
                    json,
                    constValue,
                    ordered,
                    nodeRef,
                    multi,
                    when,
                    sortedMap,
                    format,
                    onUnMappedValue,
                    onNoInputValue,
                    mapEmpty,
                    mapNonEmpty);
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
        String onUnMappedValue;
        String onNoInputValue;

        // node-level iriMap and props (sorted at finalisation)
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
            case "json" -> acc.json = value;
            case "const" -> acc.constValue = value;
            case "node" -> acc.nodeRef = value;
            case "multi" -> acc.multi = Boolean.parseBoolean(value);
            case "when" -> acc.when = value;
            case "format" -> acc.format = value;
            case "onUnMappedValue" -> acc.onUnMappedValue = value;
            case "onNoInputValue" -> acc.onNoInputValue = value;
            case "map_empty" -> acc.mapEmpty = value;
            case "map_nonempty" -> acc.mapNonEmpty = value;
            default -> {
                if (keyTail.startsWith("json.")) {
                    String suffix = keyTail.substring("json.".length());
                    try {
                        int idx = Integer.parseInt(suffix);
                        acc.indexedJsonPaths.put(idx, value);
                    } catch (NumberFormatException e) {
                        // Fallback: support non-numeric variants like json.foo if ever introduced
                        acc.jsonPaths.add(value);
                    }
                } else if (keyTail.startsWith("map.")) {
                    String k = keyTail.substring("map.".length());
                    acc.map.put(k, value);
                }
            }
        }
    }
}
