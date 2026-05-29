package gg.pvpgames.demo.queue;

import gg.pvpgames.demo.api.GameMode;

import java.util.UUID;

/**
 * One player's place in a matchmaking queue. Captures everything needed to match them: who they
 * are, the mode + kit they want, whether it's ranked, their ELO (snapshotted at join), and when
 * they joined (so the ranked ELO band can widen the longer they wait).
 */
public final class QueueEntry {

    private final UUID player;
    private final GameMode mode;
    private final String kit;
    private final boolean ranked;
    private final int elo;
    private final long joinedAtMillis;

    public QueueEntry(UUID player, GameMode mode, String kit, boolean ranked, int elo) {
        this.player = player;
        this.mode = mode;
        this.kit = kit;
        this.ranked = ranked;
        this.elo = elo;
        this.joinedAtMillis = System.currentTimeMillis();
    }

    public UUID player() {
        return player;
    }

    public GameMode mode() {
        return mode;
    }

    public String kit() {
        return kit;
    }

    public boolean ranked() {
        return ranked;
    }

    public int elo() {
        return elo;
    }

    public long joinedAtMillis() {
        return joinedAtMillis;
    }

    public long waitedSeconds() {
        return (System.currentTimeMillis() - joinedAtMillis) / 1000L;
    }

    /** Two entries are compatible only if they want the exact same mode, kit, and ranked flag. */
    public boolean sameBracket(QueueEntry other) {
        return mode == other.mode
                && ranked == other.ranked
                && kit.equalsIgnoreCase(other.kit);
    }
}
