package gg.pvpgames.demo.commands.admin;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.match.DuelGame;
import gg.pvpgames.demo.queue.QueueEntry;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /pvp <subcommand>} — the admin root command:
 * <ul>
 *   <li>{@code reload} — reload all configs, kits, and arenas.</li>
 *   <li>{@code forcestart <a> <b> [kit]} — start a duel between two players immediately.</li>
 *   <li>{@code resetstats <player|all>} — wipe stats.</li>
 *   <li>{@code debug} — toggle verbose logging.</li>
 *   <li>{@code setlobby} — set the hub spawn to your current location.</li>
 *   <li>{@code info} — quick runtime summary (games, queue, storage).</li>
 * </ul>
 */
public final class PvPCommand implements CommandExecutor, TabCompleter {

    private final PvPGamesDemoCore plugin;

    public PvPCommand(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("pvpgames.admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "forcestart" -> forceStart(sender, args);
            case "resetstats" -> resetStats(sender, args);
            case "debug" -> toggleDebug(sender);
            case "setlobby" -> setLobby(sender);
            case "info" -> info(sender);
            default -> usage(sender);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.reloadEverything();
        plugin.messages().send(sender, "general.reloaded",
                Placeholders.of("duration", System.currentTimeMillis() - start));
    }

    private void forceStart(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "admin.forcestart-usage");
            return;
        }
        Player a = plugin.getServer().getPlayerExact(args[1]);
        Player b = plugin.getServer().getPlayerExact(args[2]);
        if (a == null) {
            plugin.messages().send(sender, "general.player-not-found", Placeholders.of("player", args[1]));
            return;
        }
        if (b == null) {
            plugin.messages().send(sender, "general.player-not-found", Placeholders.of("player", args[2]));
            return;
        }
        String kit = args.length >= 4 ? args[3] : plugin.kits().defaultKitName();
        boolean ok = plugin.matchController().forceStart(a, b, kit, true);
        if (ok) {
            plugin.messages().send(sender, "admin.forcestart-done",
                    Placeholders.create().set("a", a.getName()).set("b", b.getName()));
        } else {
            plugin.messages().send(sender, "arena.none-available");
        }
    }

    private void resetStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "general.unknown-subcommand",
                    Placeholders.of("usage", "/pvp resetstats <player|all>"));
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            plugin.stats().resetAllAsync(() ->
                    plugin.messages().send(sender, "admin.resetstats-all-done"));
            return;
        }
        var offline = plugin.getServer().getOfflinePlayer(args[1]);
        plugin.stats().resetAsync(offline.getUniqueId(), () ->
                plugin.messages().send(sender, "admin.resetstats-done",
                        Placeholders.of("player", args[1])));
    }

    private void toggleDebug(CommandSender sender) {
        boolean newState = !plugin.configs().debug();
        plugin.configs().setDebug(newState);
        plugin.messages().send(sender, "admin.debug-toggled",
                Placeholders.of("state", newState ? "ON" : "OFF"));
    }

    private void setLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        plugin.lobby().setSpawn(player.getLocation());
        sender.sendMessage(gg.pvpgames.demo.util.Text.parse(
                plugin.messages().prefixRaw() + "<aqua>Lobby spawn set to your location."));
    }

    /** A compact runtime snapshot, also useful when debugging. */
    private void info(CommandSender sender) {
        var t = gg.pvpgames.demo.util.Text.parse(plugin.messages().prefixRaw()
                + "<gray>Storage <white>" + plugin.dataStore().name()
                + "</white> | Games <white>" + plugin.gameManager().activeCount()
                + "</white> | Queue <white>" + plugin.queues().size()
                + "</white> | Arenas <white>" + plugin.arenas().enabledCount()
                + "/" + plugin.arenas().count()
                + "</white> | Kits <white>" + plugin.kits().all().size() + "</white>");
        sender.sendMessage(t);

        if (plugin.configs().debug()) {
            for (Game g : plugin.gameManager().activeGames()) {
                if (g instanceof DuelGame d) {
                    sender.sendMessage(gg.pvpgames.demo.util.Text.parse("<dark_gray> - duel "
                            + d.id().toString().substring(0, 8) + " state=" + d.state()
                            + " kit=" + d.kitName() + " ranked=" + d.ranked()));
                }
            }
            for (QueueEntry e : plugin.queues().snapshot()) {
                sender.sendMessage(gg.pvpgames.demo.util.Text.parse("<dark_gray> - queued "
                        + e.player() + " " + e.kit() + " " + (e.ranked() ? "ranked" : "unranked")
                        + " waited=" + e.waitedSeconds() + "s"));
            }
        }
    }

    private void usage(CommandSender sender) {
        plugin.messages().send(sender, "general.unknown-subcommand", Placeholders.of("usage",
                "/pvp <reload|forcestart|resetstats|debug|setlobby|info>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("pvpgames.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of("reload", "forcestart", "resetstats", "debug", "setlobby", "info"));
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("forcestart")) {
            return null; // suggest player names
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetstats")) {
            List<String> out = new ArrayList<>(List.of("all"));
            return out;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("forcestart")) {
            return filter(args[3], plugin.kits().displayNames());
        }
        return List.of();
    }

    private List<String> filter(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
