package io.gdcc.spi.export.dcat3.config.model;

import java.util.Map;

/**
 * @param scopeJson JSONPath selecting the sub-tree(s) this resource mapping applies to. NEW
 */
public record ResourceConfig(
        Subject subject, Map<String, ValueSource> props, Map<String, NodeTemplate> nodes, String scopeJson) {}
