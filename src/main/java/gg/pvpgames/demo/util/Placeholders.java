package gg.pvpgames.demo.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny ordered key/value replacer for {@code %placeholder%} style tokens.
 *
 * <pre>{@code
 * String out = Placeholders.of("player", "Steve").set("elo", 1200).apply(template);
 * }</pre>
 *
 * This is intentionally dependency-free (does not require PlaceholderAPI). PAPI integration
 * is handled separately in the hook package for cross-plugin placeholders.
 */
public final class Placeholders {

    private final Map<String, String> values = new LinkedHashMap<>();

    private Placeholders() {
    }

    public static Placeholders create() {
        return new Placeholders();
    }

    public static Placeholders of(String key, Object value) {
        return create().set(key, value);
    }

    public Placeholders set(String key, Object value) {
        values.put("%" + key + "%", String.valueOf(value));
        return this;
    }

    /** Replace all known tokens in the input string. */
    public String apply(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (result.indexOf(e.getKey()) >= 0) {
                result = result.replace(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}
