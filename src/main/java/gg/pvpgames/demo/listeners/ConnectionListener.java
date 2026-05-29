package gg.pvpgames.demo.listeners;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.profile.PlayerProfile;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player lifecycle: load stats on join (async), drop them into the lobby, and on quit
 * persist their stats, pull them from any queue/match, and evict their cached profile.
 */
public final class ConnectionListener implements Listener {

    private final PvPGamesDemoCore plugin;

    public ConnectionListener(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Load (or create) the player's persistent stats off-thread, then cache + lobby them.
        plugin.stats().loadAsync(player.getUniqueId(), player.getName(), stats -> {
            plugin.profiles().cache(player.getUniqueId(), stats);
            // Only move them to the lobby if they're still online when the load completes.
            if (player.isOnline()) {
                plugin.lobby().sendToLobby(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // If they were in a match, let the game handle the forfeit logic.
        Game game = plugin.gameManager().byPlayer(player.getUniqueId());
        if (game != null) {
            game.onPlayerQuit(player.getUniqueId());
        }
        // Pull them from any queue.
        plugin.queues().leave(player.getUniqueId());

        // Persist their final stats and evict the profile.
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        if (profile != null) {
            plugin.stats().saveAsync(profile.stats());
        }
        plugin.profiles().remove(player.getUniqueId());
        plugin.gameManager().unindexPlayer(player.getUniqueId());
    }

    /**
     * Lightweight chat tag: prefix messages with the player's ELO so the hub feels competitive.
     * This is intentionally minimal — a full chat-format plugin would own this in production.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        PlayerProfile profile = plugin.profiles().get(event.getPlayer().getUniqueId());
        if (profile == null) {
            return;
        }
        int elo = profile.stats().elo();
        event.renderer((source, sourceDisplayName, message, viewer) ->
                gg.pvpgames.demo.util.Text.parse("<dark_gray>[<aqua>" + elo + "<dark_gray>] <gray>")
                        .append(sourceDisplayName)
                        .append(gg.pvpgames.demo.util.Text.parse("<dark_gray>: <white>"))
                        .append(message));
    }
}
