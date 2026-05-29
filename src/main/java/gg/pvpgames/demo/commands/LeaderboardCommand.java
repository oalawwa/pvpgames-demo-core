package gg.pvpgames.demo.commands;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.data.LeaderboardCategory;
import gg.pvpgames.demo.data.LeaderboardEntry;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /leaderboard [elo|wins|kills]} — prints the cached top players for a category. Reads from
 * {@code LeaderboardManager}'s in-memory snapshot, so it's instant and never touches the DB on the
 * main thread.
 */
public final class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final PvPGamesDemoCore plugin;

    public LeaderboardCommand(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        LeaderboardCategory category = args.length >= 1
                ? LeaderboardCategory.fromString(args[0])
                : LeaderboardCategory.ELO;

        plugin.messages().sendRaw(sender, "leaderboard.header",
                Placeholders.of("category", category.display()));

        List<LeaderboardEntry> top = plugin.leaderboards().top(category);
        if (top.isEmpty()) {
            plugin.messages().sendRaw(sender, "leaderboard.empty", Placeholders.create());
            return true;
        }
        int max = Math.min(top.size(), 10);
        for (int i = 0; i < max; i++) {
            LeaderboardEntry e = top.get(i);
            plugin.messages().sendRaw(sender, "leaderboard.line", Placeholders.create()
                    .set("rank", i + 1)
                    .set("player", e.name())
                    .set("value", e.value()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("elo", "wins", "kills").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
