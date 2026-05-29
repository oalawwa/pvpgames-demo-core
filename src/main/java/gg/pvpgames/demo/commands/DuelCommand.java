package gg.pvpgames.demo.commands;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.GameMode;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import gg.pvpgames.demo.util.Placeholders;
import gg.pvpgames.demo.util.Sounds;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /duel} — with no args, opens the queue menu. With args it queues directly:
 * {@code /duel <kit> [ranked|unranked]}. This dual behaviour means the menu, the lobby compass,
 * and the clickable requeue prompt can all funnel through one well-tested code path.
 */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private final PvPGamesDemoCore plugin;

    public DuelCommand(PvPGamesDemoCore plugin) {
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
            return true; // still loading; ignore quietly
        }

        // Guard rails.
        if (profile.state() == PlayerState.IN_MATCH || profile.state() == PlayerState.SPECTATING) {
            plugin.messages().send(player, "queue.cannot-queue-in-match");
            return true;
        }
        if (profile.state() == PlayerState.QUEUE) {
            plugin.messages().send(player, "queue.already-queued");
            return true;
        }

        // No args → open the GUI.
        if (args.length == 0) {
            plugin.queueMenu().open(player);
            return true;
        }

        // Parse kit + ranked flag.
        String kit = args[0];
        if (!plugin.kits().exists(kit)) {
            plugin.messages().send(player, "kit.not-found", Placeholders.of("kit", kit));
            return true;
        }
        boolean ranked = true;
        if (args.length >= 2) {
            ranked = !args[1].equalsIgnoreCase("unranked");
        }

        boolean joined = plugin.queues().join(player, GameMode.DUELS, kit, ranked);
        if (joined) {
            Sounds.play(player, plugin.getConfig().getString("sounds.queue-join"));
            plugin.messages().send(player, "queue.joined", Placeholders.create()
                    .set("mode", GameMode.DUELS.display())
                    .set("kit", kit)
                    .set("ranked", ranked ? "Ranked" : "Unranked"));
            plugin.scoreboards().apply(player);
        } else {
            plugin.messages().send(player, "queue.already-queued");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String kit : plugin.kits().displayNames()) {
                if (kit.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(kit);
                }
            }
            return out;
        }
        if (args.length == 2) {
            return List.of("ranked", "unranked");
        }
        return List.of();
    }
}
