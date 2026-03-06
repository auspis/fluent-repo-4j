package io.github.auspis.repo4j.mapping;

/**
 * Utility for converting between Java naming conventions (camelCase) and
 * SQL naming conventions (snake_case).
 */
public final class NamingUtils {

    private NamingUtils() {
    }

    /**
     * Converts a camelCase string to snake_case.
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code "firstName"} → {@code "first_name"}</li>
     *   <li>{@code "UserProfile"} → {@code "user_profile"}</li>
     *   <li>{@code "HTMLParser"} → {@code "html_parser"}</li>
     *   <li>{@code "id"} → {@code "id"}</li>
     * </ul>
     */
    public static String toSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        StringBuilder result = new StringBuilder();
        char[] chars = camelCase.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    // Add underscore before uppercase if previous char is lowercase
                    // or if next char is lowercase (handles acronyms like "HTML")
                    char prev = chars[i - 1];
                    if (Character.isLowerCase(prev)) {
                        result.append('_');
                    } else if (i + 1 < chars.length && Character.isLowerCase(chars[i + 1])) {
                        result.append('_');
                    }
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}
