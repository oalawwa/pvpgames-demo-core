package gg.pvpgames.demo.data;

/**
 * One row of a leaderboard query: a player's name and the value for the ranked category.
 * Immutable by design — leaderboards are read-only snapshots.
 */
public record LeaderboardEntry(String name, long value) {
}
