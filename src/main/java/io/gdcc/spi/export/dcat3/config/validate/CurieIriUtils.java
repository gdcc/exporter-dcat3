package io.gdcc.spi.export.dcat3.config.validate;

import java.util.Map;

public final class CurieIriUtils {
    private CurieIriUtils() {}

    public static boolean isIri(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("mailto:")) {
            // Minimal check: require a non-empty address after "mailto:"
            return trimmed.length() > "mailto:".length();
        }
        return trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("urn:");
    }

    public static boolean isCurie(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String prefix = trimmed.substring(0, colon);
        String suffix = trimmed.substring(colon + 1);
        return !prefix.isEmpty() && !suffix.isEmpty() && !isIri(value);
    }

    public static boolean curieHasKnownPrefix(String curie, Map<String, String> prefixes) {
        if (!isCurie(curie)) {
            return false;
        }
        int colon = curie.indexOf(':');
        String prefix = curie.substring(0, colon);
        return prefixes != null && prefixes.containsKey(prefix);
    }
}
