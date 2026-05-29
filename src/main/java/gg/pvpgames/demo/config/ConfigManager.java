package gg.pvpgames.demo.config;

import gg.pvpgames.demo.PvPGamesDemoCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Owns every YAML file the plugin uses. Centralizing file IO here means the rest of the code
 * never touches disk directly — it just asks ConfigManager for a {@link FileConfiguration}.
 *
 * <p>Files managed:
 * <ul>
 *   <li>config.yml   — main settings</li>
 *   <li>messages.yml — all player-facing text</li>
 *   <li>kits.yml     — kit definitions</li>
 *   <li>arenas.yml   — arena data (written back at runtime)</li>
 * </ul>
 */
public final class ConfigManager {

    private final PvPGamesDemoCore plugin;

    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration kits;
    private FileConfiguration arenas;
    private File arenasFile;

    public ConfigManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /** Load (or reload) every config file from disk, copying defaults on first run. */
    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        this.messages = loadOrCopy("messages.yml");
        this.kits = loadOrCopy("kits.yml");

        this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!arenasFile.exists()) {
            plugin.saveResource("arenas.yml", false);
        }
        this.arenas = YamlConfiguration.loadConfiguration(arenasFile);
        applyDefaults(this.arenas, "arenas.yml");
    }

    private FileConfiguration loadOrCopy(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        applyDefaults(cfg, name);
        return cfg;
    }

    /** Merge bundled defaults so new keys added in updates are still readable. */
    private void applyDefaults(FileConfiguration cfg, String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                cfg.setDefaults(def);
                cfg.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load defaults for " + resource, e);
        }
    }

    public void saveArenas() {
        try {
            arenas.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml", e);
        }
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public FileConfiguration kits() {
        return kits;
    }

    public FileConfiguration arenas() {
        return arenas;
    }

    public boolean debug() {
        return config.getBoolean("debug", false);
    }

    public void setDebug(boolean value) {
        config.set("debug", value);
        plugin.saveConfig();
    }
}
