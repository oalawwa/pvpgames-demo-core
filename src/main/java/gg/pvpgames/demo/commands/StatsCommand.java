package gg.pvpgames.demo.commands;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.stats.PlayerStats;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /stats [player]} — shows the caller's record, or another ONLINE player's. For offline
 * lookups we fetch from the data store asynchronously so we never block the main thread.
 */
public final class StatsCommand implements CommandExecutor, TabCompleter {

    private final PvPGamesDemoCore plugin;

    public StatsCommand(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "general.player-only");
                return true;
            }
            PlayerProfile profile = plugin.profiles().get(player);
            if (profile != null) {
                render(sender, profile.stats());
            }
            return true;
        }

        // Look up by name. Prefer an online/cached profile; otherwise hit the DB async.
        String targetName = args[0];
        Player online = plugin.getServer().getPlayerExact(targetName);
        if (online != null) {
            PlayerProfile profile = plugin.profiles().get(online);
            if (profile != null) {
                render(sender, profile.stats());
                return true;
            }
        }

        // Offline: resolve UUID then async-load.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var offline = plugin.getServer().getOfflinePlayer(targetName);
            PlayerStats stats = plugin.dataStore().load(offline.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (stats == null) {
                    plugin.messages().send(sender, "general.player-not-found",
                            Placeholders.of("player", targetName));
                } else {
                    render(sender, stats);
                }
            });
        });
        return true;
    }

    private void render(CommandSender to, PlayerStats s) {
        Placeholders ph = Placeholders.create()
                .set("player", s.name())
                .set("elo", s.elo())
                .set("wins", s.wins())
                .set("losses", s.losses())
                .set("winrate", String.format("%.1f", s.winRate()))
                .set("kills", s.kills())
                .set("deaths", s.deaths())
                .set("kdr", String.format("%.2f", s.kdr()))
                .set("streak", s.currentStreak())
                .set("best_streak", s.bestStreak())
                .set("games", s.gamesPlayed())
                .set("damage", String.format("%.0f", s.damageDealt()));

        plugin.messages().sendRaw(to, "stats.header", ph);
        plugin.messages().sendRaw(to, "stats.line-elo", ph);
        plugin.messages().sendRaw(to, "stats.line-wl", ph);
        plugin.messages().sendRaw(to, "stats.line-kd", ph);
        plugin.messages().sendRaw(to, "stats.line-streak", ph);
        plugin.messages().sendRaw(to, "stats.line-games", ph);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return null; // let Bukkit suggest online player names
        }
        return List.of();
    }
}
