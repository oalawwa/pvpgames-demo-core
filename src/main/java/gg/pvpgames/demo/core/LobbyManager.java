package gg.pvpgames.demo.core;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import gg.pvpgames.demo.util.Items;
import gg.pvpgames.demo.util.Locations;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything about the hub: where it is, and how a player is reset when they arrive (gamemode,
 * inventory, the lobby hotbar with a queue compass + kit selector). Centralizing this means the
 * match code just calls {@link #sendToLobby(Player)} and trusts the player ends up clean.
 */
public final class LobbyManager {

    public static final int SLOT_QUEUE_COMPASS = 0;
    public static final int SLOT_KIT_SELECTOR = 1;
    public static final int SLOT_LEADERBOARD = 7;
    public static final int SLOT_PROFILE = 8;

    private final PvPGamesDemoCore plugin;

    public LobbyManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @Nullable
    public Location spawn() {
        return Locations.deserialize(plugin.getConfig().getString("lobby.spawn", ""));
    }

    public void setSpawn(Location loc) {
        plugin.getConfig().set("lobby.spawn", Locations.serialize(loc));
        plugin.saveConfig();
    }

    /**
     * Send a player to the lobby and fully reset their state. Falls back to the world spawn if no
     * lobby spawn is configured, so the plugin still behaves on a fresh install.
     */
    public void sendToLobby(Player player) {
        Location target = spawn();
        if (target == null) {
            target = player.getWorld().getSpawnLocation();
        }
        player.teleport(target);
        resetToLobbyState(player);

        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null) {
            profile.state(PlayerState.LOBBY);
            profile.currentMatchId(null);
            profile.spectating(false);
        }
        plugin.scoreboards().apply(player);
    }

    /** Reset a player's gamemode/inventory/effects to the standard lobby loadout. */
    public void resetToLobbyState(Player player) {
        boolean enforce = plugin.getConfig().getBoolean("lobby.enforce-lobby-state", true);
        if (!enforce) {
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setExp(0f);
        player.setLevel(0);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

        if (plugin.getConfig().getBoolean("lobby.give-lobby-items", true)) {
            giveLobbyItems(player);
        }
        player.updateInventory();
    }

    private void giveLobbyItems(Player player) {
        var inv = player.getInventory();
        inv.setItem(SLOT_QUEUE_COMPASS, Items.named(Material.COMPASS,
                "<aqua><bold>Play</bold> <dark_gray>(Right-Click)",
                "<gray>Open the duel queue menu."));
        inv.setItem(SLOT_KIT_SELECTOR, Items.named(Material.DIAMOND_SWORD,
                "<aqua><bold>Kit Selector</bold> <dark_gray>(Right-Click)",
                "<gray>Choose your default kit."));
        inv.setItem(SLOT_LEADERBOARD, Items.named(Material.PAPER,
                "<aqua><bold>Leaderboards</bold>",
                "<gray>View the top players."));
        inv.setItem(SLOT_PROFILE, Items.named(Material.PLAYER_HEAD,
                "<aqua><bold>Your Stats</bold>",
                "<gray>View your competitive record."));
    }

    private double maxHealth(Player player) {
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    public boolean isLobbyItem(@Nullable ItemStack item) {
        if (item == null) {
            return false;
        }
        Material t = item.getType();
        return t == Material.COMPASS || t == Material.DIAMOND_SWORD
                || t == Material.PAPER || t == Material.PLAYER_HEAD;
    }
}
