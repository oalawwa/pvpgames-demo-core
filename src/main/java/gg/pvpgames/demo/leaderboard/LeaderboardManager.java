package gg.pvpgames.demo.leaderboard;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.data.LeaderboardCategory;
import gg.pvpgames.demo.data.LeaderboardEntry;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps an in-memory, periodically refreshed snapshot of the top players for each
 * {@link LeaderboardCategory}. Commands and holograms read these cached lists so they never hit
 * the database on the main thread.
 */
public final class LeaderboardManager {

    private final PvPGamesDemoCore plugin;
    private final Map<LeaderboardCategory, List<LeaderboardEntry>> cache =
            new EnumMap<>(LeaderboardCategory.class);

    private BukkitTask task;

    public LeaderboardManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
        for (LeaderboardCategory c : LeaderboardCategory.values()) {
            cache.put(c, Collections.emptyList());
        }
    }

    public void start() {
        refreshNow();
        long period = 20L * Math.max(10, plugin.getConfig().getInt("holograms.refresh-seconds", 60));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshNow, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Pull fresh top-N lists for every category off-thread and swap them into the cache. */
    public void refreshNow() {
        int limit = Math.max(plugin.getConfig().getInt("holograms.top-size", 10), 10);
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            plugin.stats().topAsync(category, limit, rows -> {
                cache.put(category, rows);
                // Push updated data to holograms when ELO refreshes (covers all three boards).
                if (category == LeaderboardCategory.ELO) {
                    plugin.holograms().refresh();
                }
            });
        }
    }

    public List<LeaderboardEntry> top(LeaderboardCategory category) {
        return cache.getOrDefault(category, Collections.emptyList());
    }
}
