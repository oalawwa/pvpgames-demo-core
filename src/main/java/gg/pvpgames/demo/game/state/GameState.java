package gg.pvpgames.demo.game.state;

/**
 * The phases every match moves through, in order. This is the formal version of the gameplay
 * flow from the brief:
 *
 * <pre>WAITING → COUNTDOWN → LIVE → ENDED → CLEANUP</pre>
 *
 * <p>{@link gg.pvpgames.demo.game.state.GameStateEngine} guarantees transitions only ever move
 * forward, and notifies the active {@code Game} on each change so it can run phase logic
 * (teleport, give kits, detect a winner, show results, free the arena).
 */
public enum GameState {
    /** Players assigned, not yet teleported. Brief transient phase. */
    WAITING,
    /** Players are frozen at spawns while the countdown ticks down. */
    COUNTDOWN,
    /** The fight is on. Win detection is active. */
    LIVE,
    /** A winner/draw has been decided; results screen is showing. */
    ENDED,
    /** Players returned to lobby, arena released, game removed. Terminal. */
    CLEANUP
}
