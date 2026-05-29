package gg.pvpgames.demo.hologram;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.data.LeaderboardCategory;
import gg.pvpgames.demo.data.LeaderboardEntry;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional hologram leaderboards via DecentHolograms. We talk to DecentHolograms purely through
 * reflection against its public {@code DHAPI} class, so:
 * <ul>
 *   <li>the plugin compiles without DecentHolograms on the classpath, and</li>
 *   <li>if DecentHolograms isn't installed at runtime, every method here quietly no-ops.</li>
 * </ul>
 *
 * <p>Holograms are named {@code pvp_top_elo}, {@code pvp_top_wins}, {@code pvp_top_kills} and are
 * (re)created at the locations configured under {@code holograms} in config.yml... but to keep the
 * demo zero-config we instead let admins place them with DecentHolograms and we just refresh lines
 * by name. Creating holograms in-world is also supported via {@link #createAt}.
 */
public final class HologramManager {

    private static final String DHAPI = "eu.decentsoftware.holograms.api.DHAPI";

    private final PvPGamesDemoCore plugin;
    private boolean available;
    private Class<?> dhapi;

    public HologramManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (!plugin.getConfig().getBoolean("holograms.enabled", true)) {
            return;
        }
        if (plugin.getServer().getPluginManager().getPlugin("DecentHolograms") == null) {
            plugin.getLogger().info("DecentHolograms not found — hologram leaderboards disabled.");
            return;
        }
        try {
            this.dhapi = Class.forName(DHAPI);
            this.available = true;
            plugin.getLogger().info("Hooked into DecentHolograms for hologram leaderboards.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("DecentHolograms present but DHAPI missing; disabling holograms.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Create (or move) a hologram for a category at a location. Admin-driven; the
     * {@code /pvp} command can be extended to call this, or admins use DecentHolograms directly.
     */
    public void createAt(LeaderboardCategory category, Location location) {
        if (!available) {
            return;
        }
        String name = hologramName(category);
        try {
            // DHAPI.createHologram(String name, Location loc, List<String> lines)
            Method create = dhapi.getMethod("createHologram", String.class, Location.class, List.class);
            // Remove an existing one with the same name first.
            removeHologram(name);
            create.invoke(null, name, location, buildLines(category));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create hologram " + name + ": " + e.getMessage());
        }
    }

    /** Refresh the lines of all three leaderboard holograms (called when data updates). */
    public void refresh() {
        if (!available) {
            return;
        }
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            updateLines(category);
        }
    }

    private void updateLines(LeaderboardCategory category) {
        String name = hologramName(category);
        try {
            // Only update if the hologram exists; DHAPI.getHologram returns null otherwise.
            Method getHologram = dhapi.getMethod("getHologram", String.class);
            Object holo = getHologram.invoke(null, name);
            if (holo == null) {
                return;
            }
            // DHAPI.setHologramLines(Hologram holo, List<String> lines). Find by name so we don't
            // have to hard-reference DecentHolograms' Hologram type at compile time.
            Method setLines = findMethod(dhapi, "setHologramLines", 2);
            if (setLines != null) {
                setLines.invoke(null, holo, buildLines(category));
            }
        } catch (Throwable e) {
            if (plugin.configs().debug()) {
                plugin.getLogger().warning("Hologram refresh failed for " + name + ": " + e.getMessage());
            }
        }
    }

    /** Find a public static method by name + parameter count (avoids needing exact param types). */
    private Method findMethod(Class<?> owner, String methodName, int paramCount) {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }

    private void removeHologram(String name) {
        try {
            Method getHologram = dhapi.getMethod("getHologram", String.class);
            Object holo = getHologram.invoke(null, name);
            if (holo != null) {
                Method remove = dhapi.getMethod("removeHologram", String.class);
                remove.invoke(null, name);
            }
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    /**
     * Build the display lines for a category from the cached leaderboard. We pass already-formatted
     * legacy strings since DecentHolograms renders legacy color codes natively.
     */
    private List<String> buildLines(LeaderboardCategory category) {
        List<String> lines = new ArrayList<>();
        // Title line
        String title = plugin.messages().raw("leaderboard.hologram-title")
                .replace("%category%", category.display());
        lines.add(legacy(title));

        List<LeaderboardEntry> top = plugin.leaderboards().top(category);
        int shown = Math.min(top.size(), plugin.getConfig().getInt("holograms.top-size", 10));
        if (shown == 0) {
            lines.add(legacy(plugin.messages().raw("leaderboard.empty")));
        }
        for (int i = 0; i < shown; i++) {
            LeaderboardEntry e = top.get(i);
            Placeholders ph = Placeholders.create()
                    .set("rank", i + 1)
                    .set("player", e.name())
                    .set("value", e.value());
            lines.add(legacy(ph.apply(plugin.messages().raw("leaderboard.hologram-line"))));
        }
        return lines;
    }

    /** Convert MiniMessage/ampersand text into legacy section-sign codes for DecentHolograms. */
    private String legacy(String miniOrAmp) {
        var comp = gg.pvpgames.demo.util.Text.parse(miniOrAmp);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(comp);
    }

    private String hologramName(LeaderboardCategory category) {
        return "pvp_top_" + category.name().toLowerCase();
    }
}
