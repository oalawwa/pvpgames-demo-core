package gg.pvpgames.demo.data;

import gg.pvpgames.demo.stats.PlayerStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Shared JDBC logic for both the MySQL and SQLite backends. Subclasses only provide:
 * <ul>
 *   <li>{@link #connection()} — how to obtain a {@link Connection}</li>
 *   <li>{@link #upsertSql()} — the dialect-specific INSERT ... ON CONFLICT/DUPLICATE statement</li>
 * </ul>
 *
 * <p>The {@code players} table is intentionally simple and flat — one row per player. That maps
 * cleanly to a website API later (a single SELECT renders a profile page).
 */
public abstract class SqlDataStore implements DataStore {

    protected final Logger log;
    protected final String table;

    protected SqlDataStore(Logger log, String tablePrefix) {
        this.log = log;
        this.table = (tablePrefix == null ? "" : tablePrefix) + "players";
    }

    /** Obtain a connection. For pooled backends this borrows from the pool. */
    protected abstract Connection connection() throws SQLException;

    /** Dialect-specific UPSERT. Must match the column order used in {@link #bindUpsert}. */
    protected abstract String upsertSql();

    /** SQL type for the primary key column; MySQL uses VARCHAR(36), SQLite uses TEXT. */
    protected String uuidColumnType() {
        return "VARCHAR(36)";
    }

    @Override
    public boolean init() {
        final String ddl = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "uuid " + uuidColumnType() + " PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "kills INT NOT NULL DEFAULT 0," +
                "deaths INT NOT NULL DEFAULT 0," +
                "wins INT NOT NULL DEFAULT 0," +
                "losses INT NOT NULL DEFAULT 0," +
                "games_played INT NOT NULL DEFAULT 0," +
                "elo INT NOT NULL DEFAULT 1000," +
                "current_streak INT NOT NULL DEFAULT 0," +
                "best_streak INT NOT NULL DEFAULT 0," +
                "damage_dealt DOUBLE NOT NULL DEFAULT 0" +
                ")";
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
            return true;
        } catch (SQLException e) {
            log.severe("[" + name() + "] Failed to create table: " + e.getMessage());
            return false;
        }
    }

    @Override
    public PlayerStats load(UUID uuid) {
        final String sql = "SELECT * FROM " + table + " WHERE uuid = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return read(rs);
                }
            }
        } catch (SQLException e) {
            log.warning("[" + name() + "] load(" + uuid + ") failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void save(PlayerStats s) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(upsertSql())) {
            bindUpsert(ps, s);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[" + name() + "] save(" + s.uuid() + ") failed: " + e.getMessage());
        }
    }

    @Override
    public void saveAll(Iterable<PlayerStats> all) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(upsertSql())) {
                int batch = 0;
                for (PlayerStats s : all) {
                    bindUpsert(ps, s);
                    ps.addBatch();
                    if (++batch % 100 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            log.warning("[" + name() + "] saveAll failed: " + e.getMessage());
        }
    }

    @Override
    public void resetStats(UUID uuid, int startingElo) {
        final String sql = "UPDATE " + table + " SET kills=0,deaths=0,wins=0,losses=0," +
                "games_played=0,elo=?,current_streak=0,best_streak=0,damage_dealt=0 WHERE uuid=?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, startingElo);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[" + name() + "] resetStats failed: " + e.getMessage());
        }
    }

    @Override
    public void resetAll(int startingElo) {
        final String sql = "UPDATE " + table + " SET kills=0,deaths=0,wins=0,losses=0," +
                "games_played=0,elo=?,current_streak=0,best_streak=0,damage_dealt=0";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, startingElo);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("[" + name() + "] resetAll failed: " + e.getMessage());
        }
    }

    @Override
    public List<LeaderboardEntry> topBy(LeaderboardCategory category, int limit) {
        // category.column() is a controlled enum value, never user input — safe to inline.
        final String sql = "SELECT name, " + category.column() + " AS value FROM " + table +
                " ORDER BY " + category.column() + " DESC LIMIT ?";
        List<LeaderboardEntry> out = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LeaderboardEntry(rs.getString("name"), rs.getLong("value")));
                }
            }
        } catch (SQLException e) {
            log.warning("[" + name() + "] topBy(" + category + ") failed: " + e.getMessage());
        }
        return out;
    }

    // ---- helpers shared by both dialects ----

    private PlayerStats read(ResultSet rs) throws SQLException {
        return new PlayerStats(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getInt("kills"),
                rs.getInt("deaths"),
                rs.getInt("wins"),
                rs.getInt("losses"),
                rs.getInt("games_played"),
                rs.getInt("elo"),
                rs.getInt("current_streak"),
                rs.getInt("best_streak"),
                rs.getDouble("damage_dealt"));
    }

    /**
     * Binds parameters 1..11 in this exact order:
     * uuid, name, kills, deaths, wins, losses, games_played, elo, current_streak,
     * best_streak, damage_dealt. Subclass UPSERT SQL must use the same order.
     */
    protected void bindUpsert(PreparedStatement ps, PlayerStats s) throws SQLException {
        ps.setString(1, s.uuid().toString());
        ps.setString(2, s.name());
        ps.setInt(3, s.kills());
        ps.setInt(4, s.deaths());
        ps.setInt(5, s.wins());
        ps.setInt(6, s.losses());
        ps.setInt(7, s.gamesPlayed());
        ps.setInt(8, s.elo());
        ps.setInt(9, s.currentStreak());
        ps.setInt(10, s.bestStreak());
        ps.setDouble(11, s.damageDealt());
    }
}
