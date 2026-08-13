package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaDefinition;
import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.model.JobType;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class QueueService {
    public enum JoinResult {
        JOINED,
        ALREADY_QUEUED_OR_PLAYING,
        QUEUE_FULL,
        INVALID_ARENA,
        ARENA_DISABLED,
        MATCH_RUNNING
    }

    private final VaultRushPlugin plugin;
    private final ArenaManager arenaManager;

    public QueueService(VaultRushPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    public JoinResult join(Player player, ArenaMatch match) {
        return join(player, match, JobType.ASSAULT);
    }

    public JoinResult join(Player player, ArenaMatch match, JobType job) {
        if (player == null || match == null || job == null) return JoinResult.INVALID_ARENA;
        ArenaDefinition arena = match.arena();
        if (!arena.isValid()) return JoinResult.INVALID_ARENA;
        if (!arena.enabled()) return JoinResult.ARENA_DISABLED;
        if (match.state() != GameState.WAITING && match.state() != GameState.COUNTDOWN) {
            return JoinResult.MATCH_RUNNING;
        }
        if (arenaManager.findByPlayer(player.getUniqueId()) != null) {
            return JoinResult.ALREADY_QUEUED_OR_PLAYING;
        }
        int maxPlayers = plugin.maxPlayers();
        if (match.queue().size() >= maxPlayers) return JoinResult.QUEUE_FULL;
        return match.enqueue(player.getUniqueId(), job)
                ? JoinResult.JOINED
                : JoinResult.ALREADY_QUEUED_OR_PLAYING;
    }

    public boolean leaveQueue(UUID uniqueId) {
        ArenaMatch match = queuedArena(uniqueId);
        return match != null && match.removeQueued(uniqueId);
    }

    public ArenaMatch queuedArena(UUID uniqueId) {
        if (uniqueId == null) return null;
        for (ArenaMatch match : arenaManager.matches()) {
            if (match.queue().contains(uniqueId)) return match;
        }
        return null;
    }

    public ArenaDefinition defaultArena() {
        for (ArenaDefinition arena : arenaManager.all()) {
            if (arena.enabled() && arena.isValid()) return arena;
        }
        return null;
    }

    public int queueSize(ArenaMatch match) {
        return match == null ? 0 : match.queue().size();
    }
}
