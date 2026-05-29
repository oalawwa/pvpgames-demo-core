package gg.pvpgames.demo.profile;

/**
 * High-level state a player can be in. Drives which scoreboard is shown and what actions are
 * allowed (e.g. you can't queue while IN_MATCH). This is deliberately small; per-match phase
 * (countdown vs fighting) lives in the {@code GameState} engine, not here.
 */
public enum PlayerState {
    /** Standing in the hub. */
    LOBBY,
    /** Waiting in a matchmaking queue. */
    QUEUE,
    /** Actively competing in a match. */
    IN_MATCH,
    /** Watching a match (eliminated or chose to spectate). */
    SPECTATING
}
