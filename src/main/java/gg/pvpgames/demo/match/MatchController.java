package gg.pvpgames.demo.match;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.arena.Arena;
import gg.pvpgames.demo.queue.QueueEntry;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Turns an abstract "these two should fight" decision (from {@link gg.pvpgames.demo.queue.QueueManager})
 * into a concrete, running {@link DuelGame}. It owns arena allocation and the failure path when no
 * arena is free. Keeping this separate from the queue means the matchmaking algorithm and the
 * match-construction wiring evolve independently — and a future CTF controller would live beside
 * this one without touching either.
 */
public final class MatchController {

    private final PvPGamesDemoCore plugin;

    public MatchController(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a duel for two matched queue entries. If no arena is available, both players are
     * informed and re-added to the queue so they aren't dropped.
     */
    public void startDuel(QueueEntry a, QueueEntry b) {
        String kit = a.kit(); // both entries share a bracket, so kits match
        boolean ranked = a.ranked();

        Optional<Arena> arenaOpt = plugin.arenas().acquire(kit);
        if (arenaOpt.isEmpty()) {
            handleNoArena(a, b);
            return;
        }

        Arena arena = arenaOpt.get();
        DuelGame game = new DuelGame(plugin, a.player(), b.player(), kit, ranked, arena);
        plugin.gameManager().register(game);
        game.begin();

        if (plugin.configs().debug()) {
            plugin.getLogger().info("Started duel " + game.id() + " on arena " + arena.name()
                    + " (" + kit + ", " + (ranked ? "ranked" : "unranked") + ").");
        }
    }

    /**
     * Admin force-start: build a duel between two online players directly, bypassing the queue.
     * Returns true if it started.
     */
    public boolean forceStart(Player a, Player b, String kit, boolean ranked) {
        if (a.equals(b)) {
            return false;
        }
        if (plugin.gameManager().isInGame(a.getUniqueId()) || plugin.gameManager().isInGame(b.getUniqueId())) {
            return false;
        }
        String kitName = plugin.kits().exists(kit) ? kit : plugin.kits().defaultKitName();
        Optional<Arena> arenaOpt = plugin.arenas().acquire(kitName);
        if (arenaOpt.isEmpty()) {
            return false;
        }
        // Make sure neither is stuck in a queue.
        plugin.queues().leave(a.getUniqueId());
        plugin.queues().leave(b.getUniqueId());

        DuelGame game = new DuelGame(plugin, a.getUniqueId(), b.getUniqueId(), kitName, ranked, arenaOpt.get());
        plugin.gameManager().register(game);
        game.begin();
        return true;
    }

    private void handleNoArena(QueueEntry a, QueueEntry b) {
        notify(a.player());
        notify(b.player());
        // Re-queue both so matchmaking retries once an arena frees up.
        Player pa = plugin.getServer().getPlayer(a.player());
        Player pb = plugin.getServer().getPlayer(b.player());
        if (pa != null) {
            plugin.queues().join(pa, a.mode(), a.kit(), a.ranked());
        }
        if (pb != null) {
            plugin.queues().join(pb, b.mode(), b.kit(), b.ranked());
        }
    }

    private void notify(UUID id) {
        Player p = plugin.getServer().getPlayer(id);
        if (p != null) {
            plugin.messages().send(p, "arena.none-available", Placeholders.create());
        }
    }
}
