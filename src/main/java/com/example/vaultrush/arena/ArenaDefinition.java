package com.example.vaultrush.arena;

import com.example.vaultrush.util.LocationCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public final class ArenaDefinition {
    private final String id;
    private boolean enabled;
    private String worldName;
    private LocationCodec.SavedLocation lobby;
    private LocationCodec.SavedLocation redSpawn;
    private LocationCodec.SavedLocation blueSpawn;
    private LocationCodec.SavedLocation redDeposit;
    private LocationCodec.SavedLocation blueDeposit;
    private LocationCodec.SavedLocation vault;

    public ArenaDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public static ArenaDefinition load(String id, ConfigurationSection section) {
        ArenaDefinition arena = new ArenaDefinition(id);
        arena.enabled = section.getBoolean("enabled", false);
        arena.worldName = section.getString("world");
        arena.lobby = LocationCodec.readSaved(
                section.getConfigurationSection("lobby"));
        arena.redSpawn = LocationCodec.readSaved(
                section.getConfigurationSection("red-spawn"));
        arena.blueSpawn = LocationCodec.readSaved(
                section.getConfigurationSection("blue-spawn"));
        arena.redDeposit = LocationCodec.readSaved(
                section.getConfigurationSection("red-deposit"));
        arena.blueDeposit = LocationCodec.readSaved(
                section.getConfigurationSection("blue-deposit"));
        arena.vault = LocationCodec.readSaved(
                section.getConfigurationSection("vault"));
        return arena;
    }

    public void save(ConfigurationSection section) {
        section.set("enabled", enabled);
        section.set("world", worldName);
        section.set("lobby", write(lobby));
        section.set("red-spawn", write(redSpawn));
        section.set("blue-spawn", write(blueSpawn));
        section.set("red-deposit", write(redDeposit));
        section.set("blue-deposit", write(blueDeposit));
        section.set("vault", write(vault));
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String worldName() { return worldName; }
    public Location lobby() { return resolve(lobby); }
    public Location redSpawn() { return resolve(redSpawn); }
    public Location blueSpawn() { return resolve(blueSpawn); }
    public Location redDeposit() { return resolve(redDeposit); }
    public Location blueDeposit() { return resolve(blueDeposit); }
    public Location vault() { return resolve(vault); }

    public void setLobby(Location location) {
        lobby = saved(location);
        worldName = location.getWorld().getName();
    }

    public void setRedSpawn(Location location) {
        redSpawn = saved(location);
        worldName = location.getWorld().getName();
    }

    public void setBlueSpawn(Location location) {
        blueSpawn = saved(location);
        worldName = location.getWorld().getName();
    }

    public void setRedDeposit(Location location) {
        redDeposit = saved(location);
        worldName = location.getWorld().getName();
    }

    public void setBlueDeposit(Location location) {
        blueDeposit = saved(location);
        worldName = location.getWorld().getName();
    }

    public void setVault(Location location) {
        vault = saved(location);
        worldName = location.getWorld().getName();
    }

    public boolean isConfigured() {
        return worldName != null && !worldName.isBlank()
                && lobby != null && redSpawn != null && blueSpawn != null
                && redDeposit != null && blueDeposit != null && vault != null;
    }

    public boolean isValid() {
        return isConfigured() && Bukkit.getWorld(worldName) != null
                && sameWorld(lobby) && sameWorld(redSpawn)
                && sameWorld(blueSpawn) && sameWorld(redDeposit)
                && sameWorld(blueDeposit) && sameWorld(vault);
    }

    private boolean sameWorld(LocationCodec.SavedLocation location) {
        return location != null && worldName.equals(location.worldName());
    }

    private static Location resolve(LocationCodec.SavedLocation location) {
        return location == null ? null : location.resolve();
    }

    private static LocationCodec.SavedLocation saved(Location location) {
        return LocationCodec.SavedLocation.from(location);
    }

    private static Object write(LocationCodec.SavedLocation location) {
        return location == null ? null : location.write();
    }
}
