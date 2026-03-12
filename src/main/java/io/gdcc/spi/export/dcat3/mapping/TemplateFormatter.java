package io.gdcc.spi.export.dcat3.mapping;

import java.util.List;
import java.util.function.Function;

/**
 * Generic template formatter used consistently for subjects, node IRIs and property values.
 *
 * <p>Supported placeholders:</p>
 * <ul>
 *   <li>${value} - replaced by the base value (optionally normalized)</li>
 *   <li>${1}, ${2}, ... - replaced by values resolved from jsonPaths in order</li>
 *   <li>${$.path} / ${$$.path} - inline JSONPath placeholders resolved via JaywayJsonFinder</li>
 * </ul>
 */
public final class TemplateFormatter {

    private TemplateFormatter() {
        // utility
    }

    /**
     * Formats a template using a base value, optional jsonPaths list, and inline JSON placeholders.
     *
     * @param template       the template string, may contain placeholders
     * @param baseValue      the base value for ${value} (may be null)
     * @param jsonPaths      JSONPaths providing indexed placeholders ${1}, ${2}, ... (may be null/empty)
     * @param finder         JSON finder for resolving jsonPaths and inline placeholders
     * @param baseNormalizer normalizer for baseValue when interpolated into ${value}
     * @return formatted string (never null; may be empty)
     */
    public static String format(
        String template,
        String baseValue,
        List<String> jsonPaths,
        JaywayJsonFinder finder,
        Function<String, String> baseNormalizer) {

        if (template == null || template.isBlank()) {
            return baseValue == null ? "" : baseValue;
        }

        String formatted = template;

        // 1) Legacy ${value}
        if (formatted.contains("${value}")) {
            String base = baseValue == null ? "" : baseValue;
            if (baseNormalizer != null) {
                base = baseNormalizer.apply(base);
                base = base == null ? "" : base;
            }
            formatted = formatted.replace("${value}", base);
        }

        // 2) Indexed ${1}, ${2}, ... from jsonPaths
        if (jsonPaths != null && !jsonPaths.isEmpty()) {
            for (int i = 0; i < jsonPaths.size(); i++) {
                String path = jsonPaths.get(i);
                String value = firstOrEmpty(resolveListScopedOrRoot(finder, path));
                formatted = formatted.replace("${" + (i + 1) + "}", value);
            }
        }

        // 3) Inline JSONPath placeholders: ${$.path} or ${$$.path}
        formatted = resolveInlineJsonPlaceholders(formatted, finder);

        return formatted;
    }

    /**
     * Resolve inline placeholders ${$.path} and ${$$.path} in a template.
     * Unknown tokens are left intact.
     */
    public static String resolveInlineJsonPlaceholders(String template, JaywayJsonFinder finder) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        int start = 0;

        while (true) {
            int open = template.indexOf("${", start);
            if (open < 0) {
                out.append(template.substring(start));
                break;
            }

            out.append(template, start, open);

            int close = template.indexOf("}", open + 2);
            if (close < 0) {
                // malformed, append rest
                out.append(template.substring(open));
                break;
            }

            String token = template.substring(open + 2, close);
            String replacement;

            if (token.startsWith("$$") || token.startsWith("$")) {
                replacement = firstOrEmpty(resolveListScopedOrRoot(finder, token));
            } else {
                // leave unknown tokens as-is (e.g., ${1} handled earlier)
                replacement = "${" + token + "}";
            }

            out.append(replacement);
            start = close + 1;
        }

        return out.toString();
    }

    /** If JSONPath starts with "$$", query original root; else, current scope. */
    static List<String> resolveListScopedOrRoot(JaywayJsonFinder finder, String jsonPath) {
        if (finder == null || jsonPath == null) {
            return List.of();
        }
        if (jsonPath.startsWith("$$")) {
            // strip one '$' so listRoot sees "$.x"
            return finder.listRoot(jsonPath.substring(1));
        }
        return finder.list(jsonPath);
    }

    private static String firstOrEmpty(List<String> values) {
        return (values == null || values.isEmpty()) ? "" : (values.get(0) == null ? "" : values.get(0));
    }
}