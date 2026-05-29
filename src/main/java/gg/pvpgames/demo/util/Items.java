package gg.pvpgames.demo.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Helpers for building {@link ItemStack}s, including a small parser for the kit/menu item
 * syntax used in the YAML configs:
 *
 * <pre>{@code MATERIAL[:amount][ ench:ENCHANT/level,...][ name:"Display"]}</pre>
 */
public final class Items {

    private Items() {
    }

    /** Quick builder: material + display name + lore lines (MiniMessage/legacy supported). */
    public static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(Text.parse(name).decoration(TextDecoration.ITALIC, false));
            }
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>(lore.length);
                for (String l : lore) {
                    lines.add(Text.parse(l).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Parse the kit item syntax into an ItemStack. Unknown materials return {@code null}
     * (the caller logs and skips). Enchantments are looked up by their Bukkit key/legacy name.
     */
    @Nullable
    public static ItemStack parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Split into the leading "MATERIAL[:amount]" token and the rest of the modifiers.
        String working = raw.trim();
        String namePart = null;

        // Extract a quoted name first so spaces inside it don't break tokenization.
        int nameIdx = working.indexOf("name:\"");
        if (nameIdx >= 0) {
            int end = working.indexOf('"', nameIdx + 6);
            if (end > nameIdx) {
                namePart = working.substring(nameIdx + 6, end);
                working = (working.substring(0, nameIdx) + working.substring(end + 1)).trim();
            }
        }

        String[] tokens = working.split("\\s+");
        String matToken = tokens[0];
        int amount = 1;
        if (matToken.contains(":")) {
            String[] ma = matToken.split(":", 2);
            matToken = ma[0];
            try {
                amount = Math.max(1, Integer.parseInt(ma[1]));
            } catch (NumberFormatException ignored) {
                // leave amount = 1
            }
        }

        Material material = Material.matchMaterial(matToken.toUpperCase(Locale.ROOT));
        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.startsWith("ench:")) {
                applyEnchants(item, token.substring(5));
            }
            // (potion:/flags: tokens are intentionally left for KitManager-specific handling)
        }

        if (namePart != null && meta != null) {
            meta.displayName(Text.parse(namePart).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyEnchants(ItemStack item, String spec) {
        for (String pair : spec.split(",")) {
            String[] el = pair.split("/");
            if (el.length == 0) {
                continue;
            }
            Enchantment ench = resolveEnchant(el[0]);
            if (ench == null) {
                continue;
            }
            int level = 1;
            if (el.length > 1) {
                try {
                    level = Integer.parseInt(el[1]);
                } catch (NumberFormatException ignored) {
                    // default 1
                }
            }
            item.addUnsafeEnchantment(ench, level);
        }
    }

    /**
     * Resolve an enchantment by modern key (e.g. {@code sharpness}, {@code protection}) OR by the
     * old Bukkit field name (e.g. {@code DAMAGE_ALL}, {@code PROTECTION_ENVIRONMENTAL}) so existing
     * kit configs from older servers keep working.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    private static Enchantment resolveEnchant(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        // Map a few common legacy names to modern keys.
        switch (name.toUpperCase(Locale.ROOT)) {
            case "DAMAGE_ALL" -> lower = "sharpness";
            case "PROTECTION_ENVIRONMENTAL" -> lower = "protection";
            case "DURABILITY" -> lower = "unbreaking";
            case "ARROW_DAMAGE" -> lower = "power";
            case "ARROW_INFINITE" -> lower = "infinity";
            case "DIG_SPEED" -> lower = "efficiency";
            case "LOOT_BONUS_MOBS" -> lower = "looting";
            default -> { /* assume already a modern key */ }
        }
        try {
            Enchantment byKey = org.bukkit.Registry.ENCHANTMENT
                    .get(NamespacedKey.minecraft(lower));
            if (byKey != null) {
                return byKey;
            }
        } catch (Throwable ignored) {
            // fall through to legacy lookup
        }
        return Enchantment.getByName(name.toUpperCase(Locale.ROOT));
    }
}
