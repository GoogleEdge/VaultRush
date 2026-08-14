package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.model.PlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

public final class CleanupService {
    private final VaultRushPlugin plugin;
    private final ArenaManager arenaManager;
    private final GemService gemService;
    private final ScoreboardService scoreboardService;
    private final java.util.Map<UUID, com.example.vaultrush.model.PlayerSnapshot>
            pendingRestores = new java.util.HashMap<>();

    public CleanupService(VaultRushPlugin plugin, ArenaManager arenaManager, GemService gemService,
                          ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.gemService = gemService;
        this.scoreboardService = scoreboardService;
    }

    public void cleanup(ArenaMatch match) {
        if (match == null) return;
        if (match.state() == com.example.vaultrush.arena.GameState.RUNNING) {
            match.setState(com.example.vaultrush.arena.GameState.ENDING);
        }
        match.cancelTasks();
        gemService.clear(match);
        int restoreFailures = plugin.worldProtectionService().restore(match);
        if (restoreFailures > 0) {
            throw new IllegalStateException("Map restore failed for arena "
                    + match.arena().id() + " at " + restoreFailures
                    + " block(s); match state was kept for retry.");
        }
        scoreboardService.clear(match);
        for (PlayerSession session : new ArrayList<>(match.sessions().values())) {
            Player player = Bukkit.getPlayer(session.uniqueId());
            if (player != null) {
                plugin.menuService().close(player);
                plugin.shopInventoryService().close(player);
                plugin.jobSelectionService().close(player);
            }
            restore(session);
        }
    }

    public void cleanupSession(ArenaMatch match, UUID uuid) {
        if (match == null || uuid == null) return;
        PlayerSession session = match.sessions().remove(uuid);
        if (session != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.menuService().close(player);
                plugin.shopInventoryService().close(player);
                plugin.jobSelectionService().close(player);
            }
            scoreboardService.clearPlayer(uuid);
            restore(session);
        }
    }

    public void cleanupAll() {
        RuntimeException failure = null;
        for (ArenaMatch match : new ArrayList<>(arenaManager.matches())) {
            try {
                cleanup(match);
                match.reset();
                arenaManager.removeMatch(match);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe(exception.getMessage());
                failure = exception;
            }
        }
        gemService.clearPluginOwnedEntities();
        if (failure != null) throw failure;
    }

    public boolean hasPendingRestore(UUID uuid) {
        return uuid != null && pendingRestores.containsKey(uuid);
    }

    public boolean restorePending(Player player) {
        if (player == null || !player.isOnline()) return false;
        com.example.vaultrush.model.PlayerSnapshot snapshot =
                pendingRestores.get(player.getUniqueId());
        if (snapshot == null) return false;
        return restore(player, snapshot);
    }

    private void restore(PlayerSession session) {
        Player player = Bukkit.getPlayer(session.uniqueId());
        if (player == null || !player.isOnline()) {
            pendingRestores.put(session.uniqueId(), session.snapshot());
            return;
        }
        if (restore(player, session.snapshot())) {
            plugin.menuItemService().ensure(player);
        }
    }

    private boolean restore(Player player,
                            com.example.vaultrush.model.PlayerSnapshot snapshot) {
        try {
            snapshot.restore(player);
            pendingRestores.remove(player.getUniqueId());
            return true;
        } catch (RuntimeException exception) {
            pendingRestores.put(player.getUniqueId(), snapshot);
            plugin.getLogger().warning("Could not restore " + player.getName()
                    + ": " + exception.getMessage());
            return false;
        }
    }
}
