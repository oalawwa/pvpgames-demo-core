package gg.pvpgames.demo.api;

/**
 * Identifies a type of game the network can run. Only DUELS is implemented in this demo, but the
 * enum is the single place future modes are registered — CTF, ROYALE, and TOURNAMENT are listed
 * here as a deliberate signpost that the framework is built to grow into them.
 *
 * <p>Each constant carries a display name and team size so generic code (queues, scoreboards,
 * results) can treat every mode uniformly.
 */
public enum GameMode {
    DUELS("Duels", 1),
    // ---- Planned modes (not yet implemented) ----
    CTF("Capture The Flag", 4),
    ROYALE("Royale", 1),
    TOURNAMENT("Tournament", 1);

    private final String display;
    private final int teamSize;

    GameMode(String display, int teamSize) {
        this.display = display;
        this.teamSize = teamSize;
    }

    public String display() {
        return display;
    }

    /** Players per team (1 = free-for-all / 1v1). */
    public int teamSize() {
        return teamSize;
    }
}
