package gg.pvpgames.demo.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Plays sounds defined by Bukkit Sound enum name in config. Resolves names safely so a typo
 * in config never crashes a match — it just silently no-ops with a debug log.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, @Nullable String soundName) {
        play(player, soundName, 1.0f, 1.0f);
    }

    public static void play(Player player, @Nullable String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isBlank()) {
            return;
        }
        Sound sound = resolve(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Sound resolve(String name) {
        // Try the legacy enum-style name first (e.g. UI_BUTTON_CLICK).
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Try as a namespaced key (e.g. "ui.button.click" or "minecraft:ui.button.click").
            try {
                String key = name.toLowerCase(Locale.ROOT).replace('_', '.');
                NamespacedKey nk = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
                if (nk != null) {
                    return org.bukkit.Registry.SOUNDS.get(nk);
                }
            } catch (Throwable ignored2) {
                // give up quietly
            }
        }
        return null;
    }
}
