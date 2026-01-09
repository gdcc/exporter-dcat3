package io.gdcc.spi.export.dcat3.config.model;

import java.util.Map;

/**
 * @param kind bnode | iri
 * @param type CURIE or IRI
 */
public record NodeTemplate(
        String id,
        String kind,
        String iriConst,
        String iriJson,
        String iriFormat,
        String type,
        boolean multi,
        Map<String, String> iriMap,
        Map<String, ValueSource> props) {}
