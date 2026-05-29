package gg.pvpgames.demo.data;

import java.util.Locale;

/**
 * The metrics a leaderboard can rank by. The {@code column} is the SQL column name shared by
 * both the MySQL and SQLite schemas, so a single query template works for every backend.
 */
public enum LeaderboardCategory {
    ELO("elo", "ELO"),
    WINS("wins", "WINS"),
    KILLS("kills", "KILLS");

    private final String column;
    private final String display;

    LeaderboardCategory(String column, String display) {
        this.column = column;
        this.display = display;
    }

    public String column() {
        return column;
    }

    public String display() {
        return display;
    }

    /** Parse a user/config string ("elo", "wins", "kills"); defaults to ELO. */
    public static LeaderboardCategory fromString(String s) {
        if (s == null) {
            return ELO;
        }
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ELO;
        }
    }
}
