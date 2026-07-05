package io.gdcc.spi.export.dcat3.config.model;

/**
 * @param displayName optional label shown in the UI dropdown; null means "use the exporter default"
 */
public record FormatFlags(boolean availableToUsers, boolean harvestable, String displayName) {}
