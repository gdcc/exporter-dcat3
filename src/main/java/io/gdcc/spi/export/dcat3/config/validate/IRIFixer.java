package io.gdcc.spi.export.dcat3.config.validate;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class IRIFixer {
    // RFC 3986 Appendix B — splits ANY string into components, never throws
    private static final Pattern SPLIT = Pattern.compile(
            "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    public static String buildValidUri(String raw) {
        Matcher m = SPLIT.matcher(raw);
        m.matches();

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

    // --- IRI validation ---

    /**
     * Checks if the given string is a valid IRI according to RFC 3986.
     * A valid IRI must have a scheme and be well-formed.
     *
     * @param iri the IRI string to validate
     * @return true if the IRI is valid, false otherwise
     */
    public static boolean isValidIri(String iri) {
        if (iri == null || iri.trim().isEmpty()) {
            return false;
        }

        Matcher m = SPLIT.matcher(iri);
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
        if ((path == null || path.isEmpty()) && (authority == null || authority.isEmpty())) {
            return false;
        }

        // Check for illegal characters in each component
        if (path != null && hasIllegalCharactersInComponent(path, "-._~!$&'()*+,;=:@/")) {
            return false;
        }
        if (query != null && hasIllegalCharactersInComponent(query, "-._~!$&'()*+,;=:@/?")) {
            return false;
        }
        if (fragment != null && hasIllegalCharactersInComponent(fragment, "-._~!$&'()*+,;=:@/?")) {
            return false;
        }
        if (authority != null && hasIllegalCharactersInComponent(authority, "-._~!$&'()*+,;=:@[]")) {
            return false;
        }
        if (scheme != null && !scheme.matches("^[a-zA-Z0-9+.-]*$")) {
            return false;
        }
        
        return true;
    }

    /**
     * Checks if a component contains illegal characters according to its allowed character set.
     *
     * @param component the component string to check
     * @param extraSafeChars the extra allowed characters for this component (beyond alphanumeric)
     * @return true if illegal characters are found, false otherwise
     */
    private static boolean hasIllegalCharactersInComponent(String component, String extraSafeChars) {
        if (component == null || component.isEmpty()) {
            return false;
        }

        for (int i = 0; i < component.length(); i++) {
            char c = component.charAt(i);

            // Check for percent-encoding: % must be followed by exactly two hex digits
            if (c == '%') {
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
            } else if (c > 127) {
                // Non-ASCII character found without percent-encoding
                return true;
            } else if (c < 32 || c == 127) {
                // Control characters are illegal
                return true;
            } else if (!Character.isLetterOrDigit(c) && extraSafeChars.indexOf(c) < 0) {
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
}

