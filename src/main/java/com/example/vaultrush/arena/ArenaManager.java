package com.example.vaultrush.arena;

import com.example.vaultrush.VaultRushPlugin;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ArenaManager {
    private final VaultRushPlugin plugin;
    private final Map<String, ArenaDefinition> arenas = new LinkedHashMap<>();
    private final Map<String, ArenaMatch> matches = new LinkedHashMap<>();
    private final Map<UUID, ArenaMatch> playerMatches = new HashMap<>();

    public ArenaManager(VaultRushPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        playerMatches.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("arenas");
        if (section == null) return;
        for (String rawId : section.getKeys(false)) {
            String id = normalize(rawId);
            ConfigurationSection arenaSection = section.getConfigurationSection(rawId);
            if (arenaSection != null) arenas.put(id, ArenaDefinition.load(id, arenaSection));
        }
        disableEnabledWorldConflicts();
    }

    public void save() {
        plugin.getConfig().set("arenas", null);
        for (ArenaDefinition arena : arenas.values()) {
            ConfigurationSection section = plugin.getConfig().createSection("arenas." + arena.id());
            arena.save(section);
        }
        plugin.saveConfig();
    }

    public ArenaDefinition get(String id) { return id == null ? null : arenas.get(normalize(id)); }
    public Collection<ArenaDefinition> all() { return arenas.values(); }
    public boolean create(String id) {
        String normalized = normalize(id);
        if (!normalized.matches("[a-z0-9_-]+")
                || arenas.containsKey(normalized)) return false;
        arenas.put(normalized, new ArenaDefinition(normalized));
        return true;
    }
    public boolean delete(String id) { return arenas.remove(normalize(id)) != null; }
    public ArenaMatch match(ArenaDefinition arena) {
        return matches.computeIfAbsent(arena.id(), ignored -> new ArenaMatch(arena));
    }
    public ArenaMatch match(String id) {
        ArenaDefinition arena = get(id);
        return arena == null ? null : match(arena);
    }
    public ArenaMatch existingMatch(String id) {
        return id == null ? null : matches.get(normalize(id));
    }
    public Collection<ArenaMatch> matches() { return matches.values(); }

    public ArenaDefinition enabledArenaForWorld(World world) {
        if (world == null) return null;
        for (ArenaDefinition arena : arenas.values()) {
            if (!arena.enabled() || !arena.isValid()) continue;
            World arenaWorld = arena.vault() == null
                    ? null : arena.vault().getWorld();
            if (arenaWorld != null
                    && arenaWorld.getUID().equals(world.getUID())) return arena;
        }
        return null;
    }

    public boolean hasEnabledWorldConflict(ArenaDefinition target) {
        if (target == null || !target.isValid()) return false;
        World targetWorld = target.vault().getWorld();
        if (targetWorld == null) return false;
        for (ArenaDefinition arena : arenas.values()) {
            if (arena == target || !arena.enabled() || !arena.isValid()) continue;
            World world = arena.vault().getWorld();
            if (world != null
                    && world.getUID().equals(targetWorld.getUID())) return true;
        }
        return false;
    }

    public ArenaMatch findByPlayer(java.util.UUID uuid) {
        if (uuid == null) return null;
        ArenaMatch cached = playerMatches.get(uuid);
        if (cached != null) {
            if (cached.queue().contains(uuid) || cached.sessions().containsKey(uuid)) {
                return cached;
            }
            playerMatches.remove(uuid, cached);
        }
        for (ArenaMatch match : matches.values()) {
            if (match.queue().contains(uuid) || match.sessions().containsKey(uuid)) {
                playerMatches.put(uuid, match);
                return match;
            }
        }
        return null;
    }

    public void forgetPlayer(UUID uuid) {
        if (uuid != null) playerMatches.remove(uuid);
    }

    public void removeMatch(ArenaMatch match) {
        if (match == null) return;
        matches.remove(match.arena().id());
        playerMatches.entrySet().removeIf(entry -> entry.getValue() == match);
    }

    private void disableEnabledWorldConflicts() {
        Map<UUID, ArenaDefinition> owners = new HashMap<>();
        boolean changed = false;
        for (ArenaDefinition arena : arenas.values()) {
            if (!arena.enabled() || !arena.isValid()) continue;
            World world = arena.vault().getWorld();
            if (world == null) continue;
            ArenaDefinition owner = owners.putIfAbsent(world.getUID(), arena);
            if (owner != null) {
                owner.setEnabled(false);
                arena.setEnabled(false);
                changed = true;
                plugin.getConfig().set("arenas." + owner.id() + ".enabled", false);
                plugin.getConfig().set("arenas." + arena.id() + ".enabled", false);
                plugin.getLogger().severe("Disabled arenas " + owner.id()
                        + " and " + arena.id() + " because they share world "
                        + world.getName() + ". Enable only one arena after fixing the configuration.");
            }
        }
        if (changed) plugin.saveConfig();
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }
}
