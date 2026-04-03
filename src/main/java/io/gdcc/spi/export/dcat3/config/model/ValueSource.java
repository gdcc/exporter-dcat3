package io.gdcc.spi.export.dcat3.config.model;

import java.util.List;
import java.util.Map;

/**
 * Declarative mapping for a single property value.
 *
 * @param predicate CURIE/IRI of the predicate to emit.
 * @param as How to emit the object: "literal" | "iri" | "node-ref".
 * @param lang Literal metadata optional language tag
 * @param datatype optional datatype IRI (CURIE allowed via Prefixes)
 * @param json Single-source selector JSONPath (scoped or root via $$ convention)
 * @param constValue constant value
 * @param jsonPaths Multi-source selectors: ordered list json.1, json.2, ...
 * @param nodeRef Node reference (for as=node-ref)
 * @param multi Multiplicity: if true and json resolves to multiple values, emit all
 * @param when Conditional emission (future use)
 * @param map Mapping table (optional): map.raw -> mapped
 * @param format formatting template. Supports ${value}, ${1}, ${2}, ... and inline JSONPath
 *     placeholders like ${$.path} or ${$$.path}.
 * @param onUnMappedValue Fallback value when input exists but doesn't match any map key
 * @param onNoInputValue Fallback value when no input is present from JSON path
 * @param mapEmpty Value to use when JSON path returns empty collection/array
 * @param mapNonEmpty Value to use when JSON path returns non-empty collection/array
 */
public record ValueSource(
        String predicate,
        String as,
        String lang,
        String datatype,
        String json,
        String constValue,
        List<String> jsonPaths,
        String nodeRef,
        boolean multi,
        String when,
        Map<String, String> map,
        String format,
        String onUnMappedValue,
        String onNoInputValue,
        String mapEmpty,
        String mapNonEmpty) {}
