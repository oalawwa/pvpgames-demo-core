package gg.pvpgames.demo.commands;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.kit.Kit;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.util.Items;
import gg.pvpgames.demo.util.Sounds;
import gg.pvpgames.demo.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel queue GUI. It renders one clickable icon per kit; left-click = ranked queue, right-click
 * = unranked. The menu is a self-contained {@link Listener} + {@link InventoryHolder}, so click
 * handling is unambiguous (we only react to inventories whose holder is this class).
 *
 * <p>This is a clean, dependency-free menu. If you prefer DeluxeMenus, the same actions are exposed
 * as commands ({@code /duel <kit> <ranked|unranked>}), so a DeluxeMenus config can drive them too —
 * see menus/duel_menu.yml.
 */
public final class QueueMenu implements Listener {

    private final PvPGamesDemoCore plugin;

    public QueueMenu(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /** Open the queue menu for a player. */
    public void open(Player player) {
        List<Kit> kits = new ArrayList<>(plugin.kits().all());
        int size = Math.max(9, ((kits.size() + 8) / 9) * 9);
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size,
                plugin.messages().component("kit.menu-title"));
        holder.inventory = inv;

        for (int i = 0; i < kits.size(); i++) {
            inv.setItem(i, icon(kits.get(i)));
        }
        player.openInventory(inv);
        Sounds.play(player, plugin.getConfig().getString("sounds.menu-click"));
    }

    /** Build the icon for a kit, including click hints in the lore. */
    private ItemStack icon(Kit kit) {
        ItemStack item = new ItemStack(kit.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse("<aqua><bold>" + kit.name())
                    .decoration(TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (!kit.description().isBlank()) {
                lore.add(Text.parse("<gray>" + kit.description()).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Text.parse("").decoration(TextDecoration.ITALIC, false));
            lore.add(Text.parse("<green>Left-Click <dark_gray>» <gray>Ranked queue")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Text.parse("<yellow>Right-Click <dark_gray>» <gray>Unranked queue")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true); // it's a menu — never let players take items
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        Kit kit = matchKit(clicked);
        if (kit == null) {
            return;
        }
        boolean ranked = !event.isRightClick(); // left = ranked, right = unranked
        player.closeInventory();

        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null) {
            profile.selectedKit(kit.name());
            profile.rankedSelected(ranked);
        }
        // Reuse the command path so behaviour is identical to typing /duel.
        player.performCommand("duel " + kit.name() + " " + (ranked ? "ranked" : "unranked"));
    }

    @Nullable
    private Kit matchKit(ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(meta.displayName());
        for (Kit kit : plugin.kits().all()) {
            if (name.equalsIgnoreCase(kit.name())) {
                return kit;
            }
        }
        return null;
    }

    /** Marker holder so we only handle clicks in OUR inventory. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
