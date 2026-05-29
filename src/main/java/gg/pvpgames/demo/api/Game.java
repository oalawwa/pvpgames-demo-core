package gg.pvpgames.demo.api;

import gg.pvpgames.demo.game.state.GameState;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * The contract for ANY game mode the framework can run. A {@code Game} is one live instance of a
 * match (e.g. a single duel between two players). Implementing this interface — plus registering a
 * factory with {@code GameManager} — is the entire surface area required to add CTF, Royale, or a
 * tournament bracket later. The match lifecycle, scoreboard hooks, and spectator handling are all
 * driven against this interface, not against any concrete mode.
 *
 * <p>Lifecycle methods are invoked by {@code GameStateEngine} as the match advances through
 * {@link GameState}. Implementations should be idempotent where noted.
 */
public interface Game {

    /** Unique id for this match instance (used as the key in {@code GameManager}). */
    UUID id();

    /** Which mode this instance is. */
    GameMode mode();

    /** Current lifecycle phase. */
    GameState state();

    /** All competitors (excludes spectators). */
    Collection<UUID> players();

    /** Whether the given player is an active competitor in this match. */
    boolean isPlayer(UUID uuid);

    // ---- lifecycle callbacks, invoked by the engine ----

    /** WAITING: prepare (assign arena, snapshot inventories). Called once. */
    void onWaiting();

    /** COUNTDOWN: teleport to spawns, freeze, give kits, start the countdown. Called once. */
    void onCountdown();

    /** LIVE: unfreeze, enable PvP, begin win detection. Called once. */
    void onLive();

    /** Called every server tick while LIVE, for timers/scoreboard/health updates. */
    void onTick();

    /**
     * Driven once per server tick by {@code GameManager}. Implementations delegate to their
     * {@code GameStateEngine} (which calls {@link #onTick()} only while LIVE). Kept on the
     * interface so the manager never needs to know about the engine type.
     */
    void engineTick();

    /** ENDED: stop combat, compute + persist results, show the results screen. Called once. */
    void onEnded();

    /** CLEANUP: return players to lobby, release the arena, remove the game. Called once. */
    void onCleanup();

    // ---- gameplay event hooks routed from the global listeners ----

    /**
     * A competitor died (or would have). The implementation decides what that means for this mode
     * (in Duels: the round is over and the other player wins).
     */
    void onPlayerDeath(Player victim);

    /**
     * A competitor disconnected. Implementations typically treat this as a forfeit.
     */
    void onPlayerQuit(UUID uuid);

    /** Add a spectator to this match. */
    void addSpectator(Player spectator);

    /** Remove a spectator (e.g. on /leave or quit). */
    void removeSpectator(UUID uuid);

    /** All current spectators. */
    Collection<UUID> spectators();
}
