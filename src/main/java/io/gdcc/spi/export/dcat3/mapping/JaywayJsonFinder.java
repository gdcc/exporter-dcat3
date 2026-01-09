package io.gdcc.spi.export.dcat3.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * JaywayJsonFinder: supports - list(): values from current scope - nodes(): subtree matches from
 * current scope - at(): create a finder scoped to a subtree while preserving original root -
 * listRoot(): values from original document root
 */
public class JaywayJsonFinder {
    private static final Logger logger =
            Logger.getLogger(JaywayJsonFinder.class.getCanonicalName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReadContext ctx; // current scope
    private final ReadContext originalRootCtx; // original document root

    private static ReadContext createCtx(JsonNode root) {
        Configuration config =
                Configuration.builder()
                        .jsonProvider(new JacksonJsonProvider())
                        .mappingProvider(new JacksonMappingProvider())
                        .options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS)
                        .build();
        return JsonPath.using(config).parse(root.toString());
    }

    public JaywayJsonFinder(JsonNode root) {
        this.ctx = createCtx(root);
        this.originalRootCtx = this.ctx; // initial root
    }

    private JaywayJsonFinder(ReadContext ctx, ReadContext originalRootCtx) {
        this.ctx = ctx;
        this.originalRootCtx = originalRootCtx;
    }

    /** Execute a JSONPath against the current scope and return stringified values. */
    public List<String> list(String jsonPath) {
        return listInternal(ctx, jsonPath);
    }

    /** Execute a JSONPath against the original document root and return stringified values. */
    public List<String> listRoot(String jsonPath) {
        return listInternal(originalRootCtx, jsonPath);
    }

    private List<String> listInternal(ReadContext context, String jsonPath) {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            logger.warning("jsonPath is null or empty");
            return Collections.emptyList();
        }
        List<Object> raw;
        try {
            raw = context.read(jsonPath, new TypeRef<List<Object>>() {});
        } catch (JsonPathException ex) {
            // Invalid or unparsable JsonPath -> behave as "no matches"
            return Collections.emptyList();
        }
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(raw.size());
        for (Object object : raw) {
            if (object == null) {
                continue;
            }
            if (object instanceof CharSequence) {
                out.add(object.toString());
            } else if (object instanceof Number || object instanceof Boolean) {
                out.add(String.valueOf(object));
            } else {
                // Complex nodes -> toString()
                out.add(String.valueOf(object));
            }
        }
        return out;
    }

    /** Return matching subtrees as JsonNode list from the current scope. */
    public List<JsonNode> nodes(String jsonPath) {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> raw;
        try {
            raw = ctx.read(jsonPath, new TypeRef<List<Object>>() {});
        } catch (JsonPathException ex) {
            return Collections.emptyList();
        }
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<JsonNode> result = new ArrayList<>(raw.size());
        for (Object o : raw) {
            JsonNode node = MAPPER.valueToTree(o);
            result.add(node);
        }
        return result;
    }

    /** Create a finder scoped to the given subtree, preserving the original root for listRoot(). */
    public JaywayJsonFinder at(JsonNode node) {
        ReadContext subCtx = createCtx(node);
        return new JaywayJsonFinder(subCtx, originalRootCtx);
    }
}
