package io.gdcc.spi.export.dcat3.config.model;

import java.util.List;

/**
 * Subject configuration for minting the RDF subject IRI.
 *
 * <p>Supported sources:
 *
 * <ul>
 *   <li>iriConst: fixed IRI
 *   <li>iriTemplate: template IRI (currently used as-is)
 *   <li>iriJson: single JSONPath providing base value
 *   <li>iriJsonPaths: multiple JSONPaths providing indexed values for ${1}, ${2}, ...
 * </ul>
 *
 * <p>iriFormat may use placeholders:
 *
 * <ul>
 *   <li>${value} from iriJson (or empty if not provided)
 *   <li>${1}, ${2}, ... from iriJsonPaths
 *   <li>inline JSONPath placeholders: ${$.path} / ${$$.path}
 * </ul>
 *
 * @param iriConst Constant IRI, if provided.
 * @param iriTemplate A template IRI, currently used as-is (optional).
 * @param iriJson JSONPath to read the subject IRI value (or id) from input.
 * @param iriJsonPaths Multi-source selectors: ordered list json.1, json.2, ...
 * @param iriFormat Optional format string to mint an absolute IRI from a JSONPath value.
 */
public record Subject(
        String iriConst, String iriTemplate, String iriJson, List<String> iriJsonPaths, String iriFormat) {}
