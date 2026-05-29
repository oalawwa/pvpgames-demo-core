package gg.pvpgames.demo.game.state;

import gg.pvpgames.demo.api.Game;

/**
 * A tiny, reusable state machine attached to one {@link Game}. It enforces that a match only ever
 * moves forward through {@link GameState} and fires the matching lifecycle callback exactly once
 * per phase. Concrete games never call their own {@code onXxx()} methods directly — they ask the
 * engine to {@link #transition(GameState)} and the engine invokes the callback. That keeps phase
 * logic in one predictable place and makes illegal transitions impossible.
 */
public final class GameStateEngine {

    private final Game game;
    private GameState state = GameState.WAITING;
    private boolean entered; // whether we've fired the callback for the current state

    public GameStateEngine(Game game) {
        this.game = game;
    }

    public GameState state() {
        return state;
    }

    /** Fire the callback for the very first state (WAITING). Call once after construction. */
    public void begin() {
        entered = false;
        enter();
    }

    /**
     * Move to a new state if it's strictly later than the current one. Backwards or same-state
     * transitions are ignored, so callers can be naive without corrupting the machine.
     */
    public void transition(GameState next) {
        if (next.ordinal() <= state.ordinal()) {
            return; // never move backwards or re-enter
        }
        this.state = next;
        this.entered = false;
        enter();
    }

    /** Convenience: advance to the immediately following state. */
    public void advance() {
        GameState[] all = GameState.values();
        int idx = state.ordinal();
        if (idx + 1 < all.length) {
            transition(all[idx + 1]);
        }
    }

    /** Called every server tick by {@code GameManager}; only does work while LIVE. */
    public void tick() {
        if (state == GameState.LIVE) {
            game.onTick();
        }
    }

    private void enter() {
        if (entered) {
            return;
        }
        entered = true;
        switch (state) {
            case WAITING -> game.onWaiting();
            case COUNTDOWN -> game.onCountdown();
            case LIVE -> game.onLive();
            case ENDED -> game.onEnded();
            case CLEANUP -> game.onCleanup();
        }
    }

    public boolean isTerminal() {
        return state == GameState.CLEANUP;
    }
}
