package gg.pvpgames.demo.queue;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.GameMode;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matchmaking. Players join via {@link #join}; a repeating task scans the queue and pairs
 * compatible entries, then hands each pair to the {@code MatchController} to start a match.
 *
 * <p>Ranked matchmaking respects an ELO band that starts narrow and widens the longer a player
 * waits, so high-skill players get fair matches quickly but never wait forever. Unranked pairs the
 * two longest-waiting players in a bracket immediately.
 */
public final class QueueManager {

    private final PvPGamesDemoCore plugin;
    // Insertion order == join order, which we rely on for "longest waiting first".
    private final Map<UUID, QueueEntry> queue = new LinkedHashMap<>();

    @Nullable
    private BukkitTask task;

    public QueueManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /** Begin the matchmaking scan (runs every second). */
    public void start() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Attempt to add a player to a queue. Returns false (with no side effects) if they're already
     * queued or otherwise can't queue right now.
     */
    public boolean join(Player player, GameMode mode, String kit, boolean ranked) {
        UUID id = player.getUniqueId();
        if (queue.containsKey(id)) {
            return false;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null || profile.state() != PlayerState.LOBBY) {
            return false;
        }
        QueueEntry entry = new QueueEntry(id, mode, kit, ranked, profile.stats().elo());
        queue.put(id, entry);
        profile.state(PlayerState.QUEUE);
        profile.selectedKit(kit);
        profile.rankedSelected(ranked);
        return true;
    }

    /** Remove a player from the queue (e.g. /leave or disconnect). */
    public boolean leave(UUID player) {
        QueueEntry removed = queue.remove(player);
        if (removed == null) {
            return false;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null && profile.state() == PlayerState.QUEUE) {
            profile.state(PlayerState.LOBBY);
        }
        return true;
    }

    public boolean isQueued(UUID player) {
        return queue.containsKey(player);
    }

    @Nullable
    public QueueEntry entry(UUID player) {
        return queue.get(player);
    }

    public int size() {
        return queue.size();
    }

    /**
     * One matchmaking pass. We greedily walk entries in join order; for each unmatched entry we
     * look for the best compatible partner and, if found, start a match for the pair.
     */
    private void scan() {
        if (queue.size() < 2) {
            return;
        }
        List<QueueEntry> entries = new ArrayList<>(queue.values());
        // Track who we've already paired this pass so we don't double-book.
        List<UUID> consumed = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            QueueEntry a = entries.get(i);
            if (consumed.contains(a.player())) {
                continue;
            }
            QueueEntry partner = findPartner(a, entries, consumed, i);
            if (partner == null) {
                continue;
            }
            consumed.add(a.player());
            consumed.add(partner.player());
            // Remove from queue before starting the match.
            queue.remove(a.player());
            queue.remove(partner.player());
            startMatch(a, partner);
        }
    }

    @Nullable
    private QueueEntry findPartner(QueueEntry a, List<QueueEntry> entries, List<UUID> consumed, int from) {
        for (int j = from + 1; j < entries.size(); j++) {
            QueueEntry b = entries.get(j);
            if (consumed.contains(b.player()) || !a.sameBracket(b)) {
                continue;
            }
            if (!a.ranked()) {
                return b; // unranked: first compatible partner wins
            }
            // Ranked: require both players to be inside each other's (time-widened) ELO band.
            if (withinEloBand(a, b) && withinEloBand(b, a)) {
                return b;
            }
        }
        return null;
    }

    /** Whether {@code b} falls inside {@code a}'s current ELO tolerance, which grows with wait time. */
    private boolean withinEloBand(QueueEntry a, QueueEntry b) {
        var cfg = plugin.getConfig();
        int initial = cfg.getInt("queue.ranked-initial-elo-range", 100);
        int growth = cfg.getInt("queue.ranked-elo-range-growth-per-second", 25);
        int max = cfg.getInt("queue.ranked-max-elo-range", 1000);
        long band = Math.min(max, initial + growth * a.waitedSeconds());
        return Math.abs(a.elo() - b.elo()) <= band;
    }

    private void startMatch(QueueEntry a, QueueEntry b) {
        Player pa = plugin.getServer().getPlayer(a.player());
        Player pb = plugin.getServer().getPlayer(b.player());
        // If either logged off in the same tick, requeue the survivor and bail.
        if (pa == null || pb == null) {
            if (pa != null) {
                queue.put(a.player(), a);
            }
            if (pb != null) {
                queue.put(b.player(), b);
            }
            return;
        }
        // Tell each player an opponent was found, then hand off to the controller.
        plugin.messages().send(pa, "queue.found-opponent", Placeholders.of("opponent", pb.getName()));
        plugin.messages().send(pb, "queue.found-opponent", Placeholders.of("opponent", pa.getName()));
        plugin.matchController().startDuel(a, b);
    }

    /** Snapshot of all queued entries (used by /pvp debug). */
    public List<QueueEntry> snapshot() {
        return new ArrayList<>(queue.values());
    }
}
