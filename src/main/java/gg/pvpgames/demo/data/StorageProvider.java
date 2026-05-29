package gg.pvpgames.demo.data;

import gg.pvpgames.demo.PvPGamesDemoCore;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Decides which {@link DataStore} the plugin uses, honoring config and implementing the
 * "MySQL with SQLite fallback" behaviour from the spec.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>If {@code storage.type = SQLITE} → use SQLite.</li>
 *   <li>If {@code storage.type = MYSQL} → try MySQL. If it fails AND
 *       {@code storage.fallback-to-sqlite = true} → use SQLite instead.</li>
 * </ol>
 */
public final class StorageProvider {

    private StorageProvider() {
    }

    public static DataStore create(PvPGamesDemoCore plugin) {
        Logger log = plugin.getLogger();
        ConfigurationSection storage = plugin.getConfig().getConfigurationSection("storage");
        String type = storage == null ? "SQLITE"
                : storage.getString("type", "MYSQL").toUpperCase(Locale.ROOT);

        String prefix = storage != null && storage.getConfigurationSection("mysql") != null
                ? storage.getConfigurationSection("mysql").getString("table-prefix", "pvp_")
                : "pvp_";
        File sqliteFile = new File(plugin.getDataFolder(),
                storage == null ? "data.db" : storage.getString("sqlite-file", "data.db"));

        if ("SQLITE".equals(type)) {
            return initOrThrow(new SqliteDataStore(log, sqliteFile, prefix), log);
        }

        // type == MYSQL (or anything unrecognized → treat as MySQL)
        ConfigurationSection mysql = storage.getConfigurationSection("mysql");
        MySqlDataStore mysqlStore = new MySqlDataStore(log, mysql);
        if (mysqlStore.init()) {
            log.info("Storage backend: MySQL (connected).");
            return mysqlStore;
        }

        boolean fallback = storage.getBoolean("fallback-to-sqlite", true);
        if (fallback) {
            log.warning("MySQL unavailable — falling back to local SQLite (" + sqliteFile.getName() + ").");
            return initOrThrow(new SqliteDataStore(log, sqliteFile, prefix), log);
        }

        throw new IllegalStateException(
                "MySQL connection failed and fallback-to-sqlite is disabled. Check config.yml.");
    }

    private static DataStore initOrThrow(DataStore store, Logger log) {
        if (!store.init()) {
            throw new IllegalStateException("Failed to initialize storage backend: " + store.name());
        }
        log.info("Storage backend: " + store.name() + ".");
        return store;
    }
}
