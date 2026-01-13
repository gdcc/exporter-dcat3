package io.gdcc.spi.export.dcat3.config.loader;

import static io.gdcc.spi.export.dcat3.config.loader.FileResolver.resolveFile;

import io.gdcc.spi.export.dcat3.config.model.AvailableToUsers;
import io.gdcc.spi.export.dcat3.config.model.Element;
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

    private static final Pattern AVAILABLE_FLAG_PATTERN =
            Pattern.compile("^dcat\\.format\\.([^.]+)\\.availableToUsers$");

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
        AvailableToUsers formats = parseAvailableToUsers(properties);

        return new RootConfig(trace, prefixes, elements, relations, formats, baseDir);
    }

    /** Parse dcat.format.* flags, defaulting to TRUE when a flag is absent. */
    static AvailableToUsers parseAvailableToUsers(Properties properties) {

        boolean rdfXmlAvailableToUsers = true;
        boolean jsonLdAvailableToUsers = true;
        boolean turtleAvailableToUsers = true;
        for (String key : properties.stringPropertyNames()) {
            Matcher matcher = AVAILABLE_FLAG_PATTERN.matcher(key);
            if (!matcher.matches()) {
                continue;
            }

            String format = matcher.group(1);
            String raw = properties.getProperty(key);

            boolean value = safeBoolean(raw, true); // parse robustly; default TRUE if missing
            format = format.toLowerCase();
            switch (format) {
                case "rdfxml":
                    rdfXmlAvailableToUsers = value;
                    break;
                case "jsonld":
                    jsonLdAvailableToUsers = value;
                    break;
                case "turtle":
                    turtleAvailableToUsers = value;
                    break;
            }
        }
        return new AvailableToUsers(
                rdfXmlAvailableToUsers, jsonLdAvailableToUsers, turtleAvailableToUsers);
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
