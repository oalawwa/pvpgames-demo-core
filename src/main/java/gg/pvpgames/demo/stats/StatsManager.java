package gg.pvpgames.demo.stats;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.data.DataStore;
import gg.pvpgames.demo.data.LeaderboardCategory;
import gg.pvpgames.demo.data.LeaderboardEntry;
import gg.pvpgames.demo.profile.PlayerProfile;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The bridge between in-memory {@link PlayerStats} and the {@link DataStore}. Every read/write to
 * the database goes through here so threading is handled in one place:
 * <ul>
 *   <li>{@link #loadAsync} — off-thread DB read, callback on main thread.</li>
 *   <li>{@link #saveAsync} — off-thread DB write.</li>
 *   <li>{@link #applyMatchResult} — pure stat + ELO mutation (call on main thread), then persist.</li>
 * </ul>
 */
public final class StatsManager {

    private final PvPGamesDemoCore plugin;
    private final DataStore store;

    public StatsManager(PvPGamesDemoCore plugin, DataStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public DataStore store() {
        return store;
    }

    /**
     * Load a player's stats off-thread. If no row exists, create a default. The {@code callback}
     * runs back on the main server thread with the resulting stats.
     */
    public void loadAsync(UUID uuid, String name, Consumer<PlayerStats> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerStats loaded = store.load(uuid);
            if (loaded == null) {
                int startElo = plugin.getConfig().getInt("elo.starting", 1000);
                loaded = new PlayerStats(uuid, name, startElo);
                store.save(loaded); // create the row up-front so leaderboards include new players
            } else {
                loaded.name(name); // keep the stored name fresh (handles name changes)
            }
            final PlayerStats result = loaded;
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    /** Persist one player's stats off-thread. */
    public void saveAsync(PlayerStats stats) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> store.save(stats));
    }

    /** Persist every cached profile (used on shutdown / periodic flush). Runs synchronously —
     * call it from an async context or only at disable. */
    public void saveAllProfiles(Iterable<PlayerProfile> profiles) {
        java.util.List<PlayerStats> list = new java.util.ArrayList<>();
        for (PlayerProfile p : profiles) {
            list.add(p.stats());
        }
        store.saveAll(list);
    }

    /**
     * Apply the outcome of a finished 1v1 match to both players' stats and (if ranked) ELO.
     * Mutates the in-memory {@link PlayerStats}, then schedules an async save for each.
     *
     * @param ranked whether ELO should change
     * @return the signed ELO change applied to the winner (loser gets the inverse-ish value)
     */
    public EloResult applyMatchResult(PlayerStats winner, PlayerStats loser, boolean ranked) {
        winner.recordWin();
        loser.recordLoss();

        int winnerDelta = 0;
        int loserDelta = 0;
        if (ranked) {
            int k = plugin.getConfig().getInt("elo.k-factor", 32);
            int min = plugin.getConfig().getInt("elo.minimum", 0);
            int wElo = winner.elo();
            int lElo = loser.elo();
            winnerDelta = Elo.delta(wElo, lElo, 1.0, k);
            loserDelta = Elo.delta(lElo, wElo, 0.0, k);
            winner.elo(Elo.apply(wElo, winnerDelta, min));
            loser.elo(Elo.apply(lElo, loserDelta, min));
        }

        saveAsync(winner);
        saveAsync(loser);
        return new EloResult(winnerDelta, loserDelta);
    }

    /** Apply a draw to both players (records a game played, no win/loss, no ELO by default). */
    public void applyDraw(PlayerStats a, PlayerStats b) {
        a.recordDraw();
        b.recordDraw();
        saveAsync(a);
        saveAsync(b);
    }

    /** Fetch a leaderboard off-thread; callback on the main thread. */
    public void topAsync(LeaderboardCategory category, int limit, Consumer<List<LeaderboardEntry>> cb) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<LeaderboardEntry> rows = store.topBy(category, limit);
            plugin.getServer().getScheduler().runTask(plugin, () -> cb.accept(rows));
        });
    }

    /** Reset one player's stats (DB + in-memory if online). */
    public void resetAsync(UUID uuid, Runnable done) {
        int startElo = plugin.getConfig().getInt("elo.starting", 1000);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            store.resetStats(uuid, startElo);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                PlayerProfile p = plugin.profiles().get(uuid);
                if (p != null) {
                    Player online = p.player();
                    String name = online != null ? online.getName() : p.stats().name();
                    p.stats(new PlayerStats(uuid, name, startElo));
                }
                if (done != null) {
                    done.run();
                }
            });
        });
    }

    /** Reset ALL stats (DB + every online profile). */
    public void resetAllAsync(Runnable done) {
        int startElo = plugin.getConfig().getInt("elo.starting", 1000);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            store.resetAll(startElo);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (PlayerProfile p : plugin.profiles().all()) {
                    Player online = p.player();
                    String name = online != null ? online.getName() : p.stats().name();
                    p.stats(new PlayerStats(p.uuid(), name, startElo));
                }
                if (done != null) {
                    done.run();
                }
            });
        });
    }

    /** Small holder for the ELO deltas produced by a ranked match. */
    public record EloResult(int winnerDelta, int loserDelta) {
    }
}
