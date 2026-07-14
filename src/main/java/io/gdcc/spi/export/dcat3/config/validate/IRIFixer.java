package io.gdcc.spi.export.dcat3.config.validate;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class IRIFixer {
    /**
     * RFC 3986 Appendix B splitter.
     *
     * <p>Example for {@code https://user:pass@example.org:8443/catalog/dataset?id=42#section-2}:
     *
     * <ul>
     *   <li>scheme = {@code https}</li>
     *   <li>authority = {@code user:pass@example.org:8443}</li>
     *   <li>path = {@code /catalog/dataset}</li>
     *   <li>query = {@code id=42}</li>
     *   <li>fragment = {@code section-2}</li>
     * </ul>
     *
     * <p>The capturing groups used in this class are:
     *
     * <ul>
     *   <li>group 2 = scheme</li>
     *   <li>group 4 = authority</li>
     *   <li>group 5 = path</li>
     *   <li>group 7 = query</li>
     *   <li>group 9 = fragment</li>
     * </ul>
     */
    private static final Pattern SPLIT = Pattern.compile(
            "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    public static String buildValidUri(String raw) {
        Matcher m = SPLIT.matcher(raw);
        if (!m.matches()) {
            return raw;
        }

        String scheme    = m.group(2);
        String authority = m.group(4);
        String path      = m.group(5);
        String query     = m.group(7);
        String fragment  = m.group(9);

        StringBuilder sb = new StringBuilder();
        if (scheme != null)    sb.append(scheme).append(":");
        if (authority != null) sb.append("//").append(encodeAuthority(authority));
        if (path != null)      sb.append(encodePath(path));
        if (query != null)     sb.append("?").append(encodeQueryOrFragment(query));
        if (fragment != null)  sb.append("#").append(encodeQueryOrFragment(fragment));

        return sb.toString();
    }

    public static String buildValidIri(String raw) {
        Matcher m = SPLIT.matcher(raw);
        if (!m.matches()) {
            return raw;
        }

        String scheme    = m.group(2);
        String authority = m.group(4);
        String path      = m.group(5);
        String query     = m.group(7);
        String fragment  = m.group(9);

        StringBuilder sb = new StringBuilder();
        if (scheme != null)    sb.append(scheme).append(":");
        if (authority != null) sb.append("//").append(encodeIriAuthority(authority));
        if (path != null)      sb.append(encodeIriPath(path));
        if (query != null)     sb.append("?").append(encodeIriQueryOrFragment(query));
        if (fragment != null)  sb.append("#").append(encodeIriQueryOrFragment(fragment));

        return sb.toString();
    }

    // --- IRI validation ---

    /**
     * Checks if the given string is a valid URI according to RFC 3986.
     * A valid URI must have a scheme and be well-formed.
     *
     * @param uri the URI string to validate
     * @return true if the URI is valid, false otherwise
     */
    public static boolean isValidUri(String uri) {
        return isValidIdentifier(uri, false);
    }

    /**
     * Checks if the given string is a valid IRI according to RFC 3987.
     * A valid IRI must have a scheme and may contain legal Unicode code points
     * in its authority, path, query, and fragment components.
     *
     * @param iri the IRI string to validate
     * @return true if the IRI is valid, false otherwise
     */
    public static boolean isValidIri(String iri) {
        return isValidIdentifier(iri, true);
    }

    private static boolean isValidIdentifier(String value, boolean allowIriChars) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        Matcher m = SPLIT.matcher(value);
        if (!m.matches()) {
            return false;
        }

        String scheme = m.group(2);
        String authority = m.group(4);
        String path = m.group(5);
        String query = m.group(7);
        String fragment = m.group(9);

        // RFC 3986: A URI must have a scheme
        if (scheme == null || scheme.isEmpty()) {
            return false;
        }

        // Scheme must start with letter and contain only alphanumeric, +, -, .
        if (!scheme.matches("^[a-zA-Z][a-zA-Z0-9+.-]*$")) {
            return false;
        }

        // If authority is present, it must be non-empty
        // If authority is absent, path must be non-empty or absolute
        if (authority != null && authority.isEmpty()) {
            return false;
        }

        // Path should be non-empty (for most URIs) or authority should be present
        if ((path == null || path.isEmpty()) && authority == null) {
            return false;
        }

        // Check for illegal characters in each component
        if (hasIllegalCharactersInComponent(path, "-._~!$&'()*+,;=:@/", allowIriChars)) {
            return false;
        }
        if (hasIllegalCharactersInComponent(query, "-._~!$&'()*+,;=:@/?", allowIriChars)) {
            return false;
        }
        if (hasIllegalCharactersInComponent(fragment, "-._~!$&'()*+,;=:@/?", allowIriChars)) {
            return false;
        }
        return !hasIllegalCharactersInComponent(authority, "-._~!$&'()*+,;=:@[]", allowIriChars);
    }

    /**
     * Checks if a component contains illegal characters according to its allowed character set.
     *
     * @param component the component string to check
     * @param extraSafeChars the extra allowed characters for this component (beyond alphanumeric)
     * @param allowIriChars whether RFC 3987 Unicode characters are allowed in this component
     * @return true if illegal characters are found, false otherwise
     */
    private static boolean hasIllegalCharactersInComponent(
            String component, String extraSafeChars, boolean allowIriChars) {
        if (component == null || component.isEmpty()) {
            return false;
        }

        for (int i = 0; i < component.length(); i++) {
            int cp = component.codePointAt(i);
            int charCount = Character.charCount(cp);

            // Check for percent-encoding: % must be followed by exactly two hex digits
            if (cp == '%') {
                // Need exactly 2 characters after %
                if (i + 3 > component.length()) {
                    return true; // Invalid percent-encoding (incomplete)
                }
                try {
                    String hex = component.substring(i + 1, i + 3);
                    // Verify both characters are valid hex digits
                    Integer.parseInt(hex, 16);
                    i += 2; // Skip the two hex digits
                } catch (NumberFormatException e) {
                    return true; // Invalid percent-encoding (not hex digits)
                }
            } else if (cp > 127) {
                if (!allowIriChars || !isUcschar(cp)) {
                    return true;
                }
                i += charCount - 1;
            } else if (cp < 32 || cp == 127) {
                // Control characters are illegal
                return true;
            } else if (!Character.isLetterOrDigit(cp) && extraSafeChars.indexOf(cp) < 0) {
                // Character is not alphanumeric and not in the extra safe set
                return true;
            }
        }
        return false;
    }

    // --- per-component encoders: allow that component's legal chars, encode the rest ---

    private static String encodePath(String s)      { return pctEncode(s, "-._~!$&'()*+,;=:@/"); }
    private static String encodeAuthority(String s)  { return pctEncode(s, "-._~!$&'()*+,;=:@[]"); }
    private static String encodeQueryOrFragment(String s) { return pctEncode(s, "-._~!$&'()*+,;=:@/?"); }
    private static String encodeIriPath(String s) { return pctEncodeIriAware(s, "-._~!$&'()*+,;=:@/"); }
    private static String encodeIriAuthority(String s) { return pctEncodeIriAware(s, "-._~!$&'()*+,;=:@[]"); }
    private static String encodeIriQueryOrFragment(String s) { return pctEncodeIriAware(s, "-._~!$&'()*+,;=:@/?"); }

    private static String pctEncode(String s, String extraSafe) {
        StringBuilder out = new StringBuilder();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            boolean isAscii = b >= 0; // single-byte ASCII range
            boolean safe = isAscii && (Character.isLetterOrDigit(c) || extraSafe.indexOf(c) >= 0);
            if (safe) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    private static boolean isUcschar(int codePoint) {
        return (codePoint >= 0xA0 && codePoint <= 0xD7FF)
                || (codePoint >= 0xF900 && codePoint <= 0xFDCF)
                || (codePoint >= 0xFDF0 && codePoint <= 0xFFEF)
                || (codePoint >= 0x10000 && codePoint <= 0xEFFFD); // simplified supplementary range
    }

    private static String pctEncodeIriAware(String s, String asciiSafe) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int charCount = Character.charCount(cp);

            boolean safe = (cp < 128 && (Character.isLetterOrDigit(cp) || asciiSafe.indexOf(cp) >= 0))
                    || isUcschar(cp);

            if (safe) {
                out.appendCodePoint(cp);
            } else {
                byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) out.append('%').append(String.format("%02X", b & 0xFF));
            }
            i += charCount;
        }
        return out.toString();
    }
}

