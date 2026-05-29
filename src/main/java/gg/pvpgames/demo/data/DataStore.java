package gg.pvpgames.demo.data;

import gg.pvpgames.demo.stats.PlayerStats;

import java.util.List;
import java.util.UUID;

/**
 * Storage abstraction for all persistent player data.
 *
 * <p>The plugin codes against this interface only; whether the bytes land in MySQL or a local
 * SQLite file is an implementation detail decided at startup. This is the seam that makes the
 * project expandable — a future Redis cache, a web-API-backed store, or a Mongo implementation
 * just implements {@code DataStore} and nothing else changes.
 *
 * <p>All methods are expected to be called OFF the main server thread (see {@code StatsManager},
 * which schedules async tasks). Implementations may block on IO.
 */
public interface DataStore {

    /** Human-readable backend name for logs, e.g. "MySQL" or "SQLite". */
    String name();

    /**
     * Open connections and create tables if needed.
     *
     * @return true if the store initialized successfully and is ready for use.
     */
    boolean init();

    /** Close pools/connections cleanly on plugin disable. */
    void close();

    /**
     * Load a player's stats, or {@code null} if they have no row yet.
     */
    PlayerStats load(UUID uuid);

    /**
     * Insert or update a player's full stat row (UPSERT).
     */
    void save(PlayerStats stats);

    /**
     * Persist many rows at once (used for periodic batch flushes / shutdown).
     */
    void saveAll(Iterable<PlayerStats> all);

    /**
     * Reset one player's stats to defaults (keeps the row, zeroes the values).
     */
    void resetStats(UUID uuid, int startingElo);

    /**
     * Wipe stats for every player. Used by {@code /pvp resetstats all}.
     */
    void resetAll(int startingElo);

    /**
     * Top-N players for a category, highest value first.
     *
     * @param category which metric to rank by
     * @param limit    maximum number of rows
     */
    List<LeaderboardEntry> topBy(LeaderboardCategory category, int limit);
}
