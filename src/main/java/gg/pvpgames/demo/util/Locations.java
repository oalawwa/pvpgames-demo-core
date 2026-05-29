package gg.pvpgames.demo.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Serialize/deserialize {@link Location} to the compact string form used across all configs:
 * {@code world,x,y,z,yaw,pitch}. Keeps config files readable and diff-friendly.
 */
public final class Locations {

    private Locations() {
    }

    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "";
        }
        return String.format("%s,%.2f,%.2f,%.2f,%.1f,%.1f",
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    /**
     * Parse a serialized location. Returns {@code null} if the string is blank or the world
     * isn't loaded (callers should handle that gracefully).
     */
    @Nullable
    public static Location deserialize(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] parts = s.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
