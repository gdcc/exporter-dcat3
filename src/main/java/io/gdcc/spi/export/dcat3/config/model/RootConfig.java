package io.gdcc.spi.export.dcat3.config.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @param baseDir Directory of the root file; used to resolve element files relative to it
 */
public record RootConfig(
        boolean trace,
        Map<String, String> prefixes,
        List<Element> elements,
        List<Relation> relations,
        AvailableToUsers availableToUsers,
        Path baseDir) {}
