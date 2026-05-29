package gg.pvpgames.demo.kit;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.util.Items;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads kit definitions from kits.yml and applies them to players at match start. New kits are
 * added purely in config — no code change required — which is exactly the kind of extensibility a
 * network needs (NoDebuff, Diamond, Axe, Archer, Custom ship by default).
 */
public final class KitManager {

    private final PvPGamesDemoCore plugin;
    // Insertion-ordered so menus list kits in config order.
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /** (Re)load all kits from kits.yml. */
    public void load() {
        kits.clear();
        ConfigurationSection root = plugin.configs().kits().getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().warning("kits.yml has no 'kits' section — no kits loaded.");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
                kits.put(key.toLowerCase(Locale.ROOT), parseKit(key, sec));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load kit '" + key + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + kits.size() + " kits: " + String.join(", ", displayNames()));
    }

    private Kit parseKit(String name, ConfigurationSection sec) {
        Material icon = Material.matchMaterial(
                sec.getString("icon", "IRON_SWORD").toUpperCase(Locale.ROOT));
        if (icon == null) {
            icon = Material.IRON_SWORD;
        }
        String desc = sec.getString("description", "");
        boolean regen = sec.getBoolean("natural-regen", true);
        boolean usePlayerInv = sec.getBoolean("use-player-inventory", false);

        ItemStack helmet = null, chest = null, legs = null, boots = null, offhand = null;
        ConfigurationSection armor = sec.getConfigurationSection("armor");
        if (armor != null) {
            helmet = Items.parse(armor.getString("helmet"));
            chest = Items.parse(armor.getString("chestplate"));
            legs = Items.parse(armor.getString("leggings"));
            boots = Items.parse(armor.getString("boots"));
        }
        if (sec.isString("offhand")) {
            offhand = Items.parse(sec.getString("offhand"));
        }

        List<ItemStack> items = new ArrayList<>();
        for (String raw : sec.getStringList("items")) {
            ItemStack parsed = Items.parse(raw);
            if (parsed != null) {
                items.add(parsed);
            }
        }
        return new Kit(name, icon, desc, regen, usePlayerInv, helmet, chest, legs, boots, offhand, items);
    }

    /**
     * Apply a kit to a player: clears their inventory, equips armor + items, sets health/hunger,
     * and toggles natural regen via the saturation trick. For the Custom kit (usePlayerInventory)
     * we leave whatever they walked in with.
     */
    public void apply(Player player, Kit kit) {
        if (kit.usePlayerInventory()) {
            // Sandbox kit: don't touch their inventory, just make sure they're healthy.
            healAndFeed(player);
            return;
        }

        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setHelmet(kit.helmet());
        inv.setChestplate(kit.chestplate());
        inv.setLeggings(kit.leggings());
        inv.setBoots(kit.boots());
        if (kit.offhand() != null) {
            inv.setItemInOffHand(kit.offhand());
        }

        int slot = 0;
        for (ItemStack item : kit.items()) {
            if (slot > 8) {
                inv.addItem(item); // overflow into main inventory
            } else {
                inv.setItem(slot++, item);
            }
        }

        healAndFeed(player);
        player.updateInventory();
    }

    private void healAndFeed(Player player) {
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        player.setHealth(max);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setExp(0f);
        player.setLevel(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
    }

    @Nullable
    public Kit get(String name) {
        return name == null ? null : kits.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return name != null && kits.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public List<String> displayNames() {
        return kits.values().stream().map(Kit::name).toList();
    }

    /** A safe default kit name (first in config, or "NoDebuff"). */
    public String defaultKitName() {
        return kits.isEmpty() ? "NoDebuff" : kits.values().iterator().next().name();
    }
}
