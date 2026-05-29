package gg.pvpgames.demo.arena;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.util.Locations;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads, saves, and allocates {@link Arena}s. Backed by arenas.yml, fully manageable in-game via
 * the {@code /arena} commands. The allocation methods ({@link #acquire}, {@link #release}) ensure a
 * single arena is never handed to two matches simultaneously.
 */
public final class ArenaManager {

    private final PvPGamesDemoCore plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /** (Re)load arenas from arenas.yml. Clears any runtime in-use flags. */
    public void load() {
        arenas.clear();
        ConfigurationSection root = plugin.configs().arenas().getConfigurationSection("arenas");
        if (root == null) {
            plugin.getLogger().info("No arenas defined yet. Use /arena create <name> to add one.");
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) {
                continue;
            }
            Arena arena = new Arena(
                    name,
                    sec.getBoolean("enabled", false),
                    Locations.deserialize(sec.getString("spawn1")),
                    Locations.deserialize(sec.getString("spawn2")),
                    sec.getStringList("kits"));
            arenas.put(name.toLowerCase(Locale.ROOT), arena);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
    }

    /** Persist every arena back to arenas.yml. */
    public void save() {
        ConfigurationSection root = plugin.configs().arenas().createSection("arenas");
        for (Arena arena : arenas.values()) {
            ConfigurationSection sec = root.createSection(arena.name());
            sec.set("enabled", arena.enabled());
            sec.set("spawn1", arena.spawn1() == null ? null : Locations.serialize(arena.spawn1()));
            sec.set("spawn2", arena.spawn2() == null ? null : Locations.serialize(arena.spawn2()));
            sec.set("kits", arena.allowedKits());
        }
        plugin.configs().saveArenas();
    }

    // ---- admin operations (called by /arena commands) ----

    public boolean create(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (arenas.containsKey(key)) {
            return false;
        }
        arenas.put(key, new Arena(name));
        save();
        return true;
    }

    public boolean delete(String name) {
        if (arenas.remove(name.toLowerCase(Locale.ROOT)) != null) {
            save();
            return true;
        }
        return false;
    }

    public boolean setSpawn(String name, int which, Location loc) {
        Arena arena = get(name);
        if (arena == null) {
            return false;
        }
        if (which == 1) {
            arena.spawn1(loc);
        } else {
            arena.spawn2(loc);
        }
        save();
        return true;
    }

    public boolean setEnabled(String name, boolean enabled) {
        Arena arena = get(name);
        if (arena == null) {
            return false;
        }
        if (enabled && !arena.hasBothSpawns()) {
            return false; // caller shows the "set spawns first" message
        }
        arena.enabled(enabled);
        save();
        return true;
    }

    // ---- allocation (called by MatchController) ----

    /**
     * Reserve a free, enabled arena that supports the given kit. Returns empty if none are free.
     * The returned arena is marked in-use until {@link #release} is called.
     */
    public synchronized Optional<Arena> acquire(String kitName) {
        for (Arena arena : arenas.values()) {
            if (arena.enabled() && !arena.inUse() && arena.hasBothSpawns() && arena.supports(kitName)) {
                arena.inUse(true);
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }

    public synchronized void release(Arena arena) {
        if (arena != null) {
            arena.inUse(false);
        }
    }

    // ---- queries ----

    @Nullable
    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return name != null && arenas.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public List<Arena> all() {
        return new ArrayList<>(arenas.values());
    }

    public int count() {
        return arenas.size();
    }

    public long enabledCount() {
        return arenas.values().stream().filter(Arena::enabled).count();
    }

    public long availableCount(String kitName) {
        return arenas.values().stream()
                .filter(a -> a.enabled() && !a.inUse() && a.hasBothSpawns() && a.supports(kitName))
                .count();
    }
}
