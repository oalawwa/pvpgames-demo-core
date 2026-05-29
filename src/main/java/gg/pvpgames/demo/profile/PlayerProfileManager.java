package gg.pvpgames.demo.profile;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.stats.PlayerStats;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the lifecycle of {@link PlayerProfile}s. Loads a profile (and its persistent stats) when a
 * player joins, keeps it cached in memory while they're online, and persists + evicts it on quit.
 *
 * <p>Loading hits the database, so it's done asynchronously by the connection listener and handed
 * here via {@link #cache}. The map is concurrent because async load callbacks and the main thread
 * both touch it.
 */
public final class PlayerProfileManager {

    private final PvPGamesDemoCore plugin;
    private final ConcurrentHashMap<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    public PlayerProfileManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Build a profile from a freshly loaded (or newly created) stats row and cache it. Safe to call
     * from any thread.
     */
    public PlayerProfile cache(UUID uuid, PlayerStats stats) {
        PlayerProfile profile = new PlayerProfile(uuid, stats);
        profiles.put(uuid, profile);
        return profile;
    }

    @Nullable
    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    @Nullable
    public PlayerProfile get(Player player) {
        return profiles.get(player.getUniqueId());
    }

    /**
     * Get a profile, or create a default one on the spot if (rarely) it isn't cached yet. This
     * guards against edge cases like an event firing a tick before the async load finishes.
     */
    public PlayerProfile getOrCreate(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), id -> {
            int startElo = plugin.getConfig().getInt("elo.starting", 1000);
            return new PlayerProfile(id, new PlayerStats(id, player.getName(), startElo));
        });
    }

    public void remove(UUID uuid) {
        profiles.remove(uuid);
    }

    public Collection<PlayerProfile> all() {
        return profiles.values();
    }

    public int onlineCount() {
        return profiles.size();
    }
}
