package util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class NameUtility {
    private NameUtility() {}

    public static String convertNameToInitials(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "";
        String[] parts = trimmed.split("\\s+");
        return Arrays.stream(parts)
                .filter(p -> !p.isBlank())
                .map(NameUtility::firstLetterUpper)
                .map(s -> s + ".")
                .collect(Collectors.joining());
    }

    private static String firstLetterUpper(String word) {
        int cp = word.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(cp)));
    }
}
