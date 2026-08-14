package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaDefinition;
import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorldProtectionService {
    public enum RecordResult {
        ALLOWED(true),
        UNPROTECTED(true),
        DENIED(false),
        FULL(false);

        private final boolean allowsEvent;

        RecordResult(boolean allowsEvent) {
            this.allowsEvent = allowsEvent;
        }

        public boolean allowsEvent() {
            return allowsEvent;
        }
    }

    private final VaultRushPlugin plugin;
    private final ArenaManager arenaManager;
    private final Map<ArenaMatch, BlockChangeJournal<BlockSnapshot>> journals =
            new HashMap<>();
    private final Map<UUID, ArenaDefinition> protectedArenas = new HashMap<>();
    private final Set<UUID> unprotectedWorlds = new HashSet<>();
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();
    private final Set<UUID> restoringWorlds = new HashSet<>();

    public WorldProtectionService(VaultRushPlugin plugin,
                                  ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("world-protection.enabled", true);
    }

    public ArenaDefinition protectedArena(World world) {
        if (!enabled() || world == null) return null;
        UUID worldId = world.getUID();
        if (protectedArenas.containsKey(worldId)) return protectedArenas.get(worldId);
        if (unprotectedWorlds.contains(worldId)) return null;
        ArenaDefinition arena = arenaManager.enabledArenaForWorld(world);
        if (arena != null) protectedArenas.put(worldId, arena);
        else unprotectedWorlds.add(worldId);
        return arena;
    }

    public void invalidateWorldCache() {
        protectedArenas.clear();
        unprotectedWorlds.clear();
    }

    public ArenaMatch matchForWorld(World world) {
        ArenaDefinition arena = protectedArena(world);
        if (arena == null) return null;
        return arenaManager.existingMatch(arena.id());
    }

    public ProtectionPolicy.Decision decision(Player player, World world) {
        ArenaDefinition arena = protectedArena(world);
        if (arena == null) return ProtectionPolicy.Decision.UNPROTECTED;
        ArenaMatch match = arenaManager.existingMatch(arena.id());
        GameState state = match == null ? GameState.WAITING : match.state();
        boolean participant = player != null && match != null
                && match.sessions().containsKey(player.getUniqueId());
        return ProtectionPolicy.decide(true, true, state, participant);
    }

    public boolean begin(ArenaMatch match) {
        if (!enabled()) return true;
        World world = world(match);
        if (match == null || world == null
                || arenaManager.hasEnabledWorldConflict(match.arena())) {
            return false;
        }
        journals.put(match, new BlockChangeJournal<>(maximumEntries()));
        return true;
    }

    public RecordResult record(Player player, BlockState original) {
        if (original == null) return RecordResult.DENIED;
        ProtectionPolicy.Decision decision = decision(player, original.getWorld());
        if (decision == ProtectionPolicy.Decision.UNPROTECTED) {
            return RecordResult.UNPROTECTED;
        }
        if (decision != ProtectionPolicy.Decision.ALLOWED) {
            sendDenied(player, decision);
            return RecordResult.DENIED;
        }
        ArenaMatch match = matchForWorld(original.getWorld());
        BlockChangeJournal<BlockSnapshot> journal = journals.get(match);
        if (journal == null) return RecordResult.DENIED;
        BlockChangeJournal.RecordResult result = journal.record(
                BlockKey.of(original), BlockSnapshot.capture(original));
        if (result == BlockChangeJournal.RecordResult.FULL) {
            plugin.getLogger().warning("World protection journal is full for arena "
                    + match.arena().id() + " (" + maximumEntries() + " blocks).");
            send(player, "world-protection-limit");
            return RecordResult.FULL;
        }
        return RecordResult.ALLOWED;
    }

    public RecordResult record(Player player, Block block) {
        return block == null ? RecordResult.DENIED
                : record(player, block.getState());
    }

    public RecordResult recordAll(Player player,
                                  Iterable<BlockState> originals) {
        if (originals == null) return RecordResult.DENIED;
        List<BlockState> fresh = new ArrayList<>();
        Set<BlockKey> freshKeys = new HashSet<>();
        ArenaMatch match = null;
        BlockChangeJournal<BlockSnapshot> journal = null;
        boolean sawState = false;
        boolean sawUnprotected = false;
        for (BlockState original : originals) {
            if (original == null) return RecordResult.DENIED;
            sawState = true;
            ProtectionPolicy.Decision decision = decision(player,
                    original.getWorld());
            if (decision == ProtectionPolicy.Decision.UNPROTECTED) {
                if (match != null) return RecordResult.DENIED;
                sawUnprotected = true;
                continue;
            }
            if (decision != ProtectionPolicy.Decision.ALLOWED) {
                sendDenied(player, decision);
                return RecordResult.DENIED;
            }
            if (sawUnprotected) return RecordResult.DENIED;
            ArenaMatch current = matchForWorld(original.getWorld());
            if (match == null) {
                match = current;
                journal = journals.get(match);
            }
            if (current != match || journal == null) return RecordResult.DENIED;
            BlockKey key = BlockKey.of(original);
            if (!journal.contains(key) && freshKeys.add(key)) fresh.add(original);
        }
        if (!sawState) return RecordResult.DENIED;
        if (match == null) return RecordResult.UNPROTECTED;
        if (journal.size() + fresh.size() > maximumEntries()) {
            plugin.getLogger().warning("World protection journal is full for arena "
                    + match.arena().id() + " (" + maximumEntries() + " blocks).");
            send(player, "world-protection-limit");
            return RecordResult.FULL;
        }
        for (BlockState original : fresh) {
            journal.record(BlockKey.of(original), BlockSnapshot.capture(original));
        }
        return RecordResult.ALLOWED;
    }

    public int restore(ArenaMatch match) {
        BlockChangeJournal<BlockSnapshot> journal = journals.get(match);
        if (journal == null || journal.isEmpty()) {
            journals.remove(match);
            return 0;
        }
        if (world(match) == null) {
            plugin.getLogger().severe("Could not restore arena "
                    + match.arena().id() + " because world "
                    + match.arena().worldName() + " is not loaded; keeping the rollback journal.");
            return journal.size();
        }
        int failures = 0;
        UUID worldId = worldId(match);
        if (worldId != null) restoringWorlds.add(worldId);
        try {
            for (BlockSnapshot snapshot : journal.valuesInReverseOrder()) {
                try {
                    if (!snapshot.restore()) failures++;
                } catch (RuntimeException exception) {
                    failures++;
                    plugin.getLogger().warning("Could not restore block "
                            + snapshot.key() + ": " + exception.getMessage());
                }
            }
        } finally {
            if (worldId != null) restoringWorlds.remove(worldId);
        }
        if (failures == 0) {
            journals.remove(match);
            journal.clear();
        }
        if (failures > 0) {
            plugin.getLogger().warning("Arena " + match.arena().id()
                    + " map restore finished with " + failures
                    + " failure(s); keeping the rollback journal for a later cleanup retry.");
        }
        return failures;
    }

    public void discard(ArenaMatch match) {
        BlockChangeJournal<BlockSnapshot> journal = journals.remove(match);
        if (journal != null) journal.clear();
    }

    public boolean isRestoring(World world) {
        return world != null && restoringWorlds.contains(world.getUID());
    }

    public boolean isProtected(World world) {
        return protectedArena(world) != null;
    }

    public void forgetPlayer(UUID uuid) {
        if (uuid != null) messageCooldowns.remove(uuid);
    }

    public void sendDenied(Player player, ProtectionPolicy.Decision decision) {
        if (decision == ProtectionPolicy.Decision.UNPROTECTED
                || decision == ProtectionPolicy.Decision.ALLOWED) return;
        send(player, decision == ProtectionPolicy.Decision.NOT_PARTICIPANT
                ? "world-protection-not-participant"
                : "world-protection-locked");
    }

    private void send(Player player, String key) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        long delay = Math.max(1, plugin.getConfig().getLong(
                "world-protection.message-cooldown-ticks", 20L)) * 50L;
        long until = messageCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < until) return;
        messageCooldowns.put(player.getUniqueId(), now + delay);
        plugin.send(player, key, Map.of());
    }

    private int maximumEntries() {
        return Math.max(1, plugin.getConfig().getInt(
                "world-protection.max-recorded-blocks", 50_000));
    }

    private UUID worldId(ArenaMatch match) {
        World world = world(match);
        return world == null ? null : world.getUID();
    }

    private World world(ArenaMatch match) {
        if (match == null || match.arena().worldName() == null) return null;
        return Bukkit.getWorld(match.arena().worldName());
    }

}
