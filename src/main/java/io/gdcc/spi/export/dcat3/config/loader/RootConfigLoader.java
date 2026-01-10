package io.gdcc.spi.export.dcat3.config.loader;

import static io.gdcc.spi.export.dcat3.config.loader.FileResolver.resolveFile;

import io.gdcc.spi.export.dcat3.config.model.Element;
import io.gdcc.spi.export.dcat3.config.model.FormatFlags;
import io.gdcc.spi.export.dcat3.config.model.Relation;
import io.gdcc.spi.export.dcat3.config.model.RootConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RootConfigLoader {
    public static final String SYS_PROP = "dataverse.dcat3.config";
    private static final Pattern ELEMENT_ID_PATTERN = Pattern.compile("^element\\.([^.]+)\\.id$");
    private static final Pattern RELATION_PREDICATE_PATTERN =
            Pattern.compile("^relation\\.([^.]+)\\.predicate$");

    // NEW: dcat.format.<format>.<flag>, where flag ∈ {availableToUsers, harvestable}
    private static final Pattern FORMAT_FLAG_PATTERN =
            Pattern.compile("^dcat\\.format\\.([^.]+)\\.(availableToUsers|harvestable)$");

    private RootConfigLoader() {}

    /**
     * Load the root config from the location specified in the system property or fallbacks:
     * relative, relative to user home or resource directory.
     *
     * @return RootConfig
     * @throws IOException when loading fails
     */
    public static RootConfig load() throws IOException {
        String rootProperty = System.getProperty(SYS_PROP);
        if (rootProperty == null || rootProperty.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "System property '"
                            + SYS_PROP
                            + "' not set; please provide a path to dcat-root.properties");
        }

        FileResolver.ResolvedFile resolved = resolveFile(null, rootProperty);
        Properties properties = new Properties();
        try (InputStream closeMe = resolved.in()) {
            properties.load(closeMe);
        }

        Path baseDir = resolved.baseDir(); // may be null when loaded from classpath
        return parse(properties, baseDir);
    }

    private static RootConfig parse(Properties properties, Path baseDir) {
        boolean trace = Boolean.parseBoolean(properties.getProperty("dcat.trace.enabled", "false"));

        // prefixes.*
        Map<String, String> prefixes = new LinkedHashMap<>();
        for (String k : properties.stringPropertyNames()) {
            if (k.startsWith("prefix.")) {
                prefixes.put(k.substring("prefix.".length()), properties.getProperty(k));
            }
        }

        // elements: element.<name>.{id,type,file}
        List<Element> elements = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            Matcher matcher = ELEMENT_ID_PATTERN.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            String base = "element." + matcher.group(1);
            String id = properties.getProperty(base + ".id");
            String type = properties.getProperty(base + ".type");
            String file = properties.getProperty(base + ".file");
            elements.add(new Element(id, type, file));
        }

        // relations: relation.<name>.{subject,predicate,object}
        List<Relation> relations = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            Matcher matcher = RELATION_PREDICATE_PATTERN.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            String base = "relation." + matcher.group(1);
            String subject = properties.getProperty(base + ".subject");
            String predicate = properties.getProperty(base + ".predicate");
            String object = properties.getProperty(base + ".object");
            Relation relation = new Relation(subject, predicate, object);
            relations.add(relation);
        }

        // NEW: dcat.format.<format>.<flag> -> defaults TRUE on absence
        Map<String, FormatFlags> formats = parseFormats(properties);

        return new RootConfig(trace, prefixes, elements, relations, formats, baseDir);
    }

    /** Parse dcat.format.* flags, defaulting to TRUE when a flag is absent. */
    private static Map<String, FormatFlags> parseFormats(Properties properties) {
        Map<String, FormatFlags> result = new LinkedHashMap<>();

        // First pass: discover all <format> names present in any dcat.format.* key
        // and collect explicit values when present.
        Map<String, Boolean> availableToUsers = new LinkedHashMap<>();
        Map<String, Boolean> harvestable = new LinkedHashMap<>();

        for (String key : properties.stringPropertyNames()) {
            Matcher matcher = FORMAT_FLAG_PATTERN.matcher(key);
            if (!matcher.matches()) continue;

            String format = matcher.group(1);
            String flag = matcher.group(2);
            String raw = properties.getProperty(key);

            boolean value = safeBoolean(raw, true); // parse robustly; default TRUE if missing
            if ("availableToUsers".equals(flag)) {
                availableToUsers.put(format, value);
            } else {
                harvestable.put(format, value);
            }
        }

        // Second pass: assemble FormatFlags for each discovered format.
        // Even if a format appears only in one flag, the other flag defaults to TRUE.
        // Also: if NO keys exist for a format at all, we won’t invent formats.
        // Formats are defined implicitly by the presence of any dcat.format.<format>.* key in the
        // file.
        for (String format : unionKeys(availableToUsers, harvestable)) {
            boolean a = availableToUsers.getOrDefault(format, true);
            boolean h = harvestable.getOrDefault(format, true);
            result.put(format, new FormatFlags(a, h));
        }

        return result;
    }

    /**
     * Robust boolean parsing: - null -> defaultValue - trims whitespace - ignores a trailing
     * semicolon (e.g., "true;") - uses Boolean.parseBoolean on the cleaned token
     */
    private static boolean safeBoolean(String raw, boolean defaultValue) {
        if (raw == null) return defaultValue;
        String cleaned = raw.trim();
        if (cleaned.endsWith(";")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        return Boolean.parseBoolean(cleaned);
    }

    private static <T> java.util.Set<T> unionKeys(Map<T, ?> a, Map<T, ?> b) {
        Set<T> s = new LinkedHashSet<>(a.keySet());
        s.addAll(b.keySet());
        return s;
    }
}
