package gg.pvpgames.demo.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * MySQL backend backed by a HikariCP connection pool. This is the primary production store.
 *
 * <p>{@link #init()} verifies connectivity by creating tables; if the server is unreachable it
 * returns {@code false}, which lets {@code StorageProvider} transparently fall back to SQLite.
 */
public final class MySqlDataStore extends SqlDataStore {

    private final ConfigurationSection cfg;
    private HikariDataSource pool;

    public MySqlDataStore(Logger log, ConfigurationSection mysqlSection) {
        super(log, mysqlSection.getString("table-prefix", "pvp_"));
        this.cfg = mysqlSection;
    }

    @Override
    public String name() {
        return "MySQL";
    }

    @Override
    public boolean init() {
        try {
            HikariConfig hc = new HikariConfig();
            String host = cfg.getString("host", "127.0.0.1");
            int port = cfg.getInt("port", 3306);
            String db = cfg.getString("database", "pvpgames_demo");
            boolean ssl = cfg.getBoolean("use-ssl", false);

            hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=" + ssl
                    + "&allowPublicKeyRetrieval=true"
                    + "&useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=UTC");
            hc.setUsername(cfg.getString("username", "root"));
            hc.setPassword(cfg.getString("password", ""));
            hc.setMaximumPoolSize(cfg.getInt("pool-size", 10));
            hc.setConnectionTimeout(cfg.getLong("connection-timeout-ms", 5000));
            hc.setPoolName("PvPGamesDemo-MySQL");
            // Driver class is shaded; HikariCP autodetects it from the URL, but set it to be safe.
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            // Sensible MySQL perf flags.
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            this.pool = new HikariDataSource(hc);

            // Probe the pool, then build the schema via the shared base logic.
            try (Connection ignored = pool.getConnection()) {
                // connection obtained successfully
            }
            return super.init();
        } catch (Exception e) {
            log.warning("[MySQL] Could not initialize: " + e.getMessage());
            if (pool != null) {
                pool.close();
                pool = null;
            }
            return false;
        }
    }

    @Override
    protected Connection connection() throws SQLException {
        return pool.getConnection();
    }

    @Override
    protected String upsertSql() {
        // MySQL UPSERT.
        return "INSERT INTO " + table + " (uuid,name,kills,deaths,wins,losses,games_played," +
                "elo,current_streak,best_streak,damage_dealt) VALUES (?,?,?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE name=VALUES(name),kills=VALUES(kills)," +
                "deaths=VALUES(deaths),wins=VALUES(wins),losses=VALUES(losses)," +
                "games_played=VALUES(games_played),elo=VALUES(elo)," +
                "current_streak=VALUES(current_streak),best_streak=VALUES(best_streak)," +
                "damage_dealt=VALUES(damage_dealt)";
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
