package io.gdcc.spi.export.dcat3.config.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @param encodeInvalidIris Whether invalid IRIs should be percent-encoded during export
 * @param baseDir Directory of the root file; used to resolve element files relative to it
 */
public record RootConfig(
        boolean trace,
        boolean encodeInvalidIris,
        Map<String, String> prefixes,
        List<Element> elements,
        List<Relation> relations,
        Map<String, FormatFlags> formats,
        Path baseDir) {}
