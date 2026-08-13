package com.example.vaultrush.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LocationCodec {
    private LocationCodec() {
    }

    public static Map<String, Object> write(Location location) {
        return write(location, null);
    }

    public static Map<String, Object> write(Location location,
                                             String fallbackWorldName) {
        if (location == null) return null;
        String worldName = location.getWorld() == null
                ? fallbackWorldName : location.getWorld().getName();
        if (worldName == null || worldName.isBlank()) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("world", worldName);
        values.put("x", location.getX());
        values.put("y", location.getY());
        values.put("z", location.getZ());
        values.put("yaw", location.getYaw());
        values.put("pitch", location.getPitch());
        return values;
    }

    public static Location read(ConfigurationSection section) {
        if (section == null) return null;
        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) return null;
        return new Location(
                Bukkit.getWorld(worldName),
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    public static SavedLocation readSaved(ConfigurationSection section) {
        if (section == null) return null;
        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) return null;
        return new SavedLocation(
                worldName,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    public record SavedLocation(String worldName, double x, double y,
                                double z, float yaw, float pitch) {
        public Location resolve() {
            World world = Bukkit.getWorld(worldName);
            return world == null ? null
                    : new Location(world, x, y, z, yaw, pitch);
        }

        public Map<String, Object> write() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("world", worldName);
            values.put("x", x);
            values.put("y", y);
            values.put("z", z);
            values.put("yaw", yaw);
            values.put("pitch", pitch);
            return values;
        }

        public static SavedLocation from(Location location) {
            if (location == null || location.getWorld() == null) return null;
            return new SavedLocation(location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }
    }
}
