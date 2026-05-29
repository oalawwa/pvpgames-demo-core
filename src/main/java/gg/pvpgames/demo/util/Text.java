package gg.pvpgames.demo.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Central text utility.
 *
 * <p>Paper ships Adventure, so we render everything to {@link Component}. We accept BOTH
 * the modern MiniMessage syntax ({@code <aqua>}, {@code <gradient:...>}) and old-school
 * ampersand codes ({@code &b}) so config authors can use whichever they're comfortable with.
 *
 * <p>Strategy: if a string contains a legacy {@code &} code, convert it to MiniMessage tags
 * first, then parse once with MiniMessage. That lets a single line mix both styles.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /**
     * Parse a string into a Component, understanding legacy {@code &} codes and MiniMessage.
     */
    public static Component parse(String input) {
        if (input == null) {
            return Component.empty();
        }
        String normalized = input;
        if (normalized.indexOf('&') >= 0) {
            // Convert legacy ampersand codes into MiniMessage so a line can mix both.
            Component legacy = LEGACY_AMP.deserialize(normalized);
            normalized = MINI.serialize(legacy);
        }
        return MINI.deserialize(normalized).compact();
    }

    /**
     * Convenience overload that applies simple {@code %key%} -> value replacements before parsing.
     */
    public static Component parse(String input, Placeholders placeholders) {
        return parse(placeholders == null ? input : placeholders.apply(input));
    }

    /** Strip all formatting and return plain text (used for logs and scoreboard width math). */
    public static String plain(String input) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText()
                .serialize(parse(input));
    }
}
