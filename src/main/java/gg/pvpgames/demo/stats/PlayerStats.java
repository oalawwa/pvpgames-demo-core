package gg.pvpgames.demo.stats;

import java.util.UUID;

/**
 * Mutable, in-memory representation of one player's competitive record. This is the single
 * object the rest of the plugin reads and writes; {@code StatsManager} flushes it to the
 * {@code DataStore} asynchronously.
 *
 * <p>Kept as a plain mutable class (not a record) on purpose: stats are updated field-by-field
 * during a match (e.g. {@code addKill()}), and a mutable object avoids churning allocations.
 */
public final class PlayerStats {

    private final UUID uuid;
    private String name;

    private int kills;
    private int deaths;
    private int wins;
    private int losses;
    private int gamesPlayed;
    private int elo;
    private int currentStreak;
    private int bestStreak;
    private double damageDealt;

    public PlayerStats(UUID uuid, String name, int startingElo) {
        this.uuid = uuid;
        this.name = name;
        this.elo = startingElo;
    }

    /** Full constructor used when loading a row from storage. */
    public PlayerStats(UUID uuid, String name, int kills, int deaths, int wins, int losses,
                       int gamesPlayed, int elo, int currentStreak, int bestStreak,
                       double damageDealt) {
        this.uuid = uuid;
        this.name = name;
        this.kills = kills;
        this.deaths = deaths;
        this.wins = wins;
        this.losses = losses;
        this.gamesPlayed = gamesPlayed;
        this.elo = elo;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
        this.damageDealt = damageDealt;
    }

    // ---- derived values ----

    /** Kill/Death ratio. Deaths of 0 are treated as 1 so a fresh player isn't "infinite". */
    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    /** Win rate as a 0-100 percentage. */
    public double winRate() {
        int total = wins + losses;
        return total == 0 ? 0.0 : (wins * 100.0) / total;
    }

    // ---- mutations used during a match ----

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    public void addDamageDealt(double amount) {
        if (amount > 0) {
            damageDealt += amount;
        }
    }

    public void recordWin() {
        wins++;
        gamesPlayed++;
        currentStreak++;
        if (currentStreak > bestStreak) {
            bestStreak = currentStreak;
        }
    }

    public void recordLoss() {
        losses++;
        gamesPlayed++;
        currentStreak = 0;
    }

    public void recordDraw() {
        gamesPlayed++;
        // A draw doesn't break a win streak by convention here; tweak if you prefer otherwise.
    }

    // ---- getters / setters ----

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int gamesPlayed() {
        return gamesPlayed;
    }

    public int elo() {
        return elo;
    }

    public void elo(int elo) {
        this.elo = elo;
    }

    public int currentStreak() {
        return currentStreak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public double damageDealt() {
        return damageDealt;
    }
}
