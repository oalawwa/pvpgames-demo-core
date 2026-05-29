package gg.pvpgames.demo.arena;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A PvP arena: two spawn points and an enabled flag, optionally restricted to certain kits.
 *
 * <p>Arenas are allocated to one match at a time. The {@link #inUse} flag is runtime-only (never
 * saved) so a server restart always frees every arena.
 */
public final class Arena {

    private final String name;
    private boolean enabled;
    @Nullable
    private Location spawn1;
    @Nullable
    private Location spawn2;
    private final List<String> allowedKits;

    /** Runtime-only: true while a live match occupies this arena. */
    private transient boolean inUse;

    public Arena(String name) {
        this.name = name;
        this.allowedKits = new ArrayList<>();
    }

    public Arena(String name, boolean enabled, @Nullable Location spawn1,
                 @Nullable Location spawn2, List<String> allowedKits) {
        this.name = name;
        this.enabled = enabled;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
        this.allowedKits = new ArrayList<>(allowedKits);
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public Location spawn1() {
        return spawn1;
    }

    public void spawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    @Nullable
    public Location spawn2() {
        return spawn2;
    }

    public void spawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public List<String> allowedKits() {
        return allowedKits;
    }

    public boolean hasBothSpawns() {
        return spawn1 != null && spawn2 != null;
    }

    public boolean inUse() {
        return inUse;
    }

    public void inUse(boolean inUse) {
        this.inUse = inUse;
    }

    public int spawnCount() {
        return (spawn1 != null ? 1 : 0) + (spawn2 != null ? 1 : 0);
    }

    /** True if this arena can host a match with the given kit right now. */
    public boolean supports(String kitName) {
        if (allowedKits.isEmpty()) {
            return true; // empty whitelist = all kits allowed
        }
        return allowedKits.stream().anyMatch(k -> k.equalsIgnoreCase(kitName));
    }
}
