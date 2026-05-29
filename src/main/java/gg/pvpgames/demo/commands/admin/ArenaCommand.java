package gg.pvpgames.demo.commands.admin;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.arena.Arena;
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
 * {@code /arena <subcommand>} — full in-game arena management so no YAML editing is required:
 * <pre>
 *   /arena create &lt;name&gt;
 *   /arena setspawn1 &lt;name&gt;   (uses your current location)
 *   /arena setspawn2 &lt;name&gt;
 *   /arena enable &lt;name&gt;
 *   /arena disable &lt;name&gt;
 *   /arena list
 *   /arena delete &lt;name&gt;
 * </pre>
 */
public final class ArenaCommand implements CommandExecutor, TabCompleter {

    private final PvPGamesDemoCore plugin;

    public ArenaCommand(PvPGamesDemoCore plugin) {
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
        String sub = args[0].toLowerCase(Locale.ROOT);

        // 'list' needs no arena name.
        if (sub.equals("list")) {
            list(sender);
            return true;
        }
        if (args.length < 2) {
            usage(sender);
            return true;
        }
        String name = args[1];

        switch (sub) {
            case "create" -> create(sender, name);
            case "setspawn1" -> setSpawn(sender, name, 1);
            case "setspawn2" -> setSpawn(sender, name, 2);
            case "enable" -> setEnabled(sender, name, true);
            case "disable" -> setEnabled(sender, name, false);
            case "delete" -> delete(sender, name);
            default -> usage(sender);
        }
        return true;
    }

    private void create(CommandSender sender, String name) {
        if (plugin.arenas().create(name)) {
            plugin.messages().send(sender, "arena.created", Placeholders.of("arena", name));
        } else {
            plugin.messages().send(sender, "arena.already-exists", Placeholders.of("arena", name));
        }
    }

    private void setSpawn(CommandSender sender, String name, int which) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.player-only");
            return;
        }
        if (!plugin.arenas().exists(name)) {
            plugin.messages().send(sender, "arena.not-found", Placeholders.of("arena", name));
            return;
        }
        plugin.arenas().setSpawn(name, which, player.getLocation());
        plugin.messages().send(sender, "arena.spawn-set",
                Placeholders.create().set("arena", name).set("n", which));
    }

    private void setEnabled(CommandSender sender, String name, boolean enabled) {
        if (!plugin.arenas().exists(name)) {
            plugin.messages().send(sender, "arena.not-found", Placeholders.of("arena", name));
            return;
        }
        boolean ok = plugin.arenas().setEnabled(name, enabled);
        if (!ok && enabled) {
            plugin.messages().send(sender, "arena.enable-needs-spawns", Placeholders.of("arena", name));
            return;
        }
        plugin.messages().send(sender, enabled ? "arena.enabled" : "arena.disabled",
                Placeholders.of("arena", name));
    }

    private void delete(CommandSender sender, String name) {
        if (plugin.arenas().delete(name)) {
            plugin.messages().send(sender, "arena.deleted", Placeholders.of("arena", name));
        } else {
            plugin.messages().send(sender, "arena.not-found", Placeholders.of("arena", name));
        }
    }

    private void list(CommandSender sender) {
        List<Arena> all = plugin.arenas().all();
        plugin.messages().sendRaw(sender, "arena.list-header", Placeholders.of("count", all.size()));
        for (Arena arena : all) {
            String status = plugin.messages().raw(arena.enabled()
                    ? "arena.status-enabled" : "arena.status-disabled");
            plugin.messages().sendRaw(sender, "arena.list-line", Placeholders.create()
                    .set("arena", arena.name())
                    .set("status", status)
                    .set("spawns", arena.spawnCount()));
        }
    }

    private void usage(CommandSender sender) {
        plugin.messages().send(sender, "general.unknown-subcommand", Placeholders.of("usage",
                "/arena <create|setspawn1|setspawn2|enable|disable|list|delete> [name]"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("pvpgames.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of("create", "setspawn1", "setspawn2",
                    "enable", "disable", "list", "delete"));
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("list")) {
            return filter(args[1], plugin.arenas().all().stream().map(Arena::name).toList());
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
