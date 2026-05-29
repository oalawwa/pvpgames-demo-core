package gg.pvpgames.demo.commands;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.profile.PlayerProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /leave} (aliases /hub, /lobby, /spawn) — context-aware exit:
 * <ul>
 *   <li>In a queue → leave the queue.</li>
 *   <li>Spectating → stop spectating and return to lobby.</li>
 *   <li>In a live match → forfeit (the opponent wins).</li>
 *   <li>In the lobby → just (re)send to spawn.</li>
 * </ul>
 */
public final class LeaveCommand implements CommandExecutor {

    private final PvPGamesDemoCore plugin;

    public LeaveCommand(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return true;
        }

        switch (profile.state()) {
            case QUEUE -> {
                plugin.queues().leave(player.getUniqueId());
                plugin.messages().send(player, "queue.left");
                plugin.scoreboards().apply(player);
            }
            case SPECTATING -> plugin.spectators().stopSpectating(player);
            case IN_MATCH -> {
                Game game = plugin.gameManager().byPlayer(player.getUniqueId());
                if (game != null) {
                    // Treat /leave during a match as a forfeit.
                    game.onPlayerQuit(player.getUniqueId());
                } else {
                    plugin.lobby().sendToLobby(player);
                }
            }
            default -> plugin.lobby().sendToLobby(player);
        }
        return true;
    }
}
