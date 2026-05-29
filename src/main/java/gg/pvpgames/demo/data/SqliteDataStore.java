package gg.pvpgames.demo.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Local SQLite backend. Used for quick local testing and as the automatic fallback when MySQL
 * is unreachable. Zero external setup — it just writes a {@code data.db} file in the plugin
 * folder, so a developer can clone the repo and run a match immediately.
 *
 * <p>SQLite is single-file; we open a fresh connection per operation. With the plugin's low write
 * volume (a handful of rows per match) this is perfectly adequate and avoids locking headaches.
 */
public final class SqliteDataStore extends SqlDataStore {

    private final File dbFile;
    private final String jdbcUrl;

    public SqliteDataStore(Logger log, File dbFile, String tablePrefix) {
        super(log, tablePrefix);
        this.dbFile = dbFile;
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    @Override
    public String name() {
        return "SQLite";
    }

    @Override
    public boolean init() {
        try {
            // Ensure the parent folder exists.
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warning("[SQLite] Could not create data folder " + parent);
            }
            // Force-load the shaded driver (helps on some classloader setups).
            Class.forName("org.sqlite.JDBC");
            return super.init();
        } catch (ClassNotFoundException e) {
            log.severe("[SQLite] Driver not found on classpath: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    @Override
    protected String uuidColumnType() {
        return "TEXT";
    }

    @Override
    protected String upsertSql() {
        // SQLite UPSERT (3.24+, bundled driver is well past that).
        return "INSERT INTO " + table + " (uuid,name,kills,deaths,wins,losses,games_played," +
                "elo,current_streak,best_streak,damage_dealt) VALUES (?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,kills=excluded.kills," +
                "deaths=excluded.deaths,wins=excluded.wins,losses=excluded.losses," +
                "games_played=excluded.games_played,elo=excluded.elo," +
                "current_streak=excluded.current_streak,best_streak=excluded.best_streak," +
                "damage_dealt=excluded.damage_dealt";
    }

    @Override
    public void close() {
        // Nothing to pool/close; connections are per-op and auto-closed via try-with-resources.
    }
}
