package gg.pvpgames.demo.listeners;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

/**
 * Keeps the hub clean and wires the lobby hotbar items to actions:
 * <ul>
 *   <li>No block break/place or hunger loss unless the player is in a live match.</li>
 *   <li>Right-clicking the queue compass opens the duel menu (or runs /duel).</li>
 *   <li>Right-clicking the kit selector / leaderboard / profile items runs the matching command.</li>
 * </ul>
 */
public final class LobbyProtectionListener implements Listener {

    private final PvPGamesDemoCore plugin;

    public LobbyProtectionListener(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (notInMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (notInMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** No hunger drain outside a live match (so the hub doesn't starve idle players). */
    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (notInMatch(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    /** Lobby hotbar interactions. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        if (profile == null || profile.state() != PlayerState.LOBBY) {
            return; // only react to clicks while in the lobby
        }
        Material type = event.getMaterial();
        if (!plugin.lobby().isLobbyItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        switch (type) {
            case COMPASS -> player.performCommand("duel");
            case DIAMOND_SWORD -> player.performCommand("duel"); // menu also handles kit choice
            case PAPER -> player.performCommand("leaderboard");
            case PLAYER_HEAD -> player.performCommand("stats");
            default -> { /* ignore */ }
        }
    }

    /** True if the player is NOT currently in a live-or-running match. */
    private boolean notInMatch(Player player) {
        // Creative-mode admins bypass protection entirely.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        return profile == null || profile.state() != PlayerState.IN_MATCH;
    }
}
