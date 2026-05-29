package gg.pvpgames.demo.commands;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /spectate <player>} — watch the match a given player is competing in. The caller must be
 * idle in the lobby (not queued or already in a match).
 */
public final class SpectateCommand implements CommandExecutor, TabCompleter {

    private final PvPGamesDemoCore plugin;

    public SpectateCommand(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return true;
        }
        if (args.length < 1) {
            plugin.messages().send(player, "general.unknown-subcommand",
                    Placeholders.of("usage", "/spectate <player>"));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(player, "general.player-not-found",
                    Placeholders.of("player", args[0]));
            return true;
        }
        if (target.equals(player)) {
            plugin.messages().send(player, "spectator.cannot-spectate-self");
            return true;
        }

        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null && (profile.state() == PlayerState.IN_MATCH)) {
            plugin.messages().send(player, "queue.cannot-queue-in-match");
            return true;
        }

        Game game = plugin.gameManager().byPlayer(target.getUniqueId());
        if (game == null || !game.isPlayer(target.getUniqueId())) {
            plugin.messages().send(player, "spectator.target-not-in-match");
            return true;
        }
        plugin.spectators().spectate(player, game);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? null : List.of();
    }
}
