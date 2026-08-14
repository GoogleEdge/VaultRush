package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.arena.Team;
import com.example.vaultrush.model.JobType;
import com.example.vaultrush.model.PlayerSession;
import com.example.vaultrush.model.PlayerSnapshot;
import com.example.vaultrush.util.TeamAllocator;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MatchController {
    private final VaultRushPlugin plugin;
    private final ArenaManager arenaManager;
    private final QueueService queueService;
    private final GemService gemService;
    private final VaultService vaultService;
    private final ScoreboardService scoreboardService;
    private final CleanupService cleanupService;

    public MatchController(VaultRushPlugin plugin, ArenaManager arenaManager, QueueService queueService,
                           GemService gemService, VaultService vaultService, ScoreboardService scoreboardService,
                           CleanupService cleanupService) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.queueService = queueService;
        this.gemService = gemService;
        this.vaultService = vaultService;
        this.scoreboardService = scoreboardService;
        this.cleanupService = cleanupService;
    }

    public void maybeStart(ArenaMatch match) {
        if (match == null) return;
        if (!match.arena().enabled() || !match.arena().isValid()) {
            abortCountdown(match);
            return;
        }
        if (match.state() == GameState.WAITING && match.queue().size() >= plugin.minPlayers()) {
            startCountdown(match);
        } else if (match.state() == GameState.COUNTDOWN && match.queue().size() < plugin.minPlayers()) {
            cancelCountdown(match);
        }
    }

    public void start(ArenaMatch match) {
        if (match == null || match.state() == GameState.RUNNING) return;
        if (!match.arena().enabled() || !match.arena().isValid()) {
            abortCountdown(match);
            return;
        }
        if (match.state() == GameState.COUNTDOWN) cancelCountdownTaskOnly(match);
        if (match.queue().size() < plugin.minPlayers()) {
            cancelCountdown(match);
            return;
        }

        List<UUID> queued = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(match.queue())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) queued.add(uuid);
            else match.removeQueued(uuid);
        }
        if (queued.size() < plugin.minPlayers()) {
            cancelCountdown(match);
            return;
        }
        Map<UUID, JobType> queuedJobs = new java.util.HashMap<>(match.queuedJobs());
        match.queue().clear();
        match.queuedJobs().clear();
        Map<Team, List<UUID>> assignment = TeamAllocator.alternate(queued);
        match.sessions().clear();
        for (Team team : Team.values()) match.teams().get(team).clear();
        match.scores().put(Team.RED, 0);
        match.scores().put(Team.BLUE, 0);
        if (!plugin.worldProtectionService().begin(match)) {
            match.reset();
            for (UUID uuid : queued) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) plugin.send(player,
                        "world-protection-start-failed", Map.of());
                JobType job = queuedJobs.get(uuid);
                if (job != null) match.enqueue(uuid, job);
            }
            return;
        }
        match.setMatchId(UUID.randomUUID().toString());
        match.setRemainingSeconds(plugin.matchDurationSeconds());
        match.setState(GameState.RUNNING);

        for (Team team : Team.values()) {
            for (UUID uuid : assignment.get(team)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                plugin.menuService().close(player);
                plugin.jobSelectionService().close(player);
                PlayerSession session = new PlayerSession(uuid, team,
                        PlayerSnapshot.capture(player),
                        queuedJobs.getOrDefault(uuid, JobType.ASSAULT));
                match.sessions().put(uuid, session);
                match.teams().get(team).add(uuid);
                preparePlayer(player, team, match);
            }
        }
        if (match.sessions().size() < plugin.minPlayers()
                || teamHasNoPlayers(match)) {
            cleanupService.cleanup(match);
            match.reset();
            return;
        }

        plugin.broadcast(match, "game-start", Map.of());
        scoreboardService.start(match);
        gemService.spawn(match);
        scheduleTasks(match);
    }

    public void forceStop(ArenaMatch match) {
        if (match == null) return;
        if (match.state() == GameState.WAITING
                || match.state() == GameState.COUNTDOWN) {
            match.cancelTasks();
            plugin.broadcastQueued(match, "forced-stop", Map.of());
            for (UUID uuid : new ArrayList<>(match.queue())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) closePlayerInterfaces(player);
            }
            plugin.worldProtectionService().discard(match);
            match.reset();
            return;
        }
        end(match, null, true);
    }

    public ArenaMatch matchFor(UUID uuid) {
        return arenaManager.findByPlayer(uuid);
    }

    public boolean leave(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        ArenaMatch match = matchFor(uuid);
        if (match == null) return false;
        plugin.menuService().close(player);
        if (match.removeQueued(uuid)) {
            closePlayerInterfaces(player);
            maybeStart(match);
            return true;
        }
        PlayerSession session = match.sessions().get(uuid);
        if (session == null) return false;
        plugin.shopInventoryService().close(player);
        cleanupService.cleanupSession(match, uuid);
        match.sessions().remove(uuid);
        match.teams().get(session.team()).remove(uuid);
        if (match.state() == GameState.COUNTDOWN) {
            maybeStart(match);
        } else if (match.state() == GameState.RUNNING && (match.sessions().isEmpty() || teamHasNoPlayers(match))) {
            end(match, otherTeamWithPlayers(match), false);
        }
        return true;
    }

    public void handleQuit(UUID uuid) {
        if (uuid == null) return;
        ArenaMatch match = matchFor(uuid);
        if (match == null) return;
        if (match.removeQueued(uuid)) {
            maybeStart(match);
            return;
        }
        PlayerSession session = match.sessions().get(uuid);
        if (session == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) plugin.shopInventoryService().close(player);
        cleanupService.cleanupSession(match, uuid);
        match.sessions().remove(uuid);
        match.teams().get(session.team()).remove(uuid);
        if (match.state() == GameState.COUNTDOWN) {
            maybeStart(match);
        } else if (match.state() == GameState.RUNNING && (match.sessions().isEmpty() || teamHasNoPlayers(match))) {
            end(match, otherTeamWithPlayers(match), false);
        }
    }

    public void handleRespawn(Player player, org.bukkit.event.player.PlayerRespawnEvent event) {
        if (player == null) return;
        ArenaMatch match = matchFor(player.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING) return;
        PlayerSession session = match.sessions().get(player.getUniqueId());
        if (session == null || !session.respawnPending()) return;
        Location spawn = session.team() == Team.RED ? match.arena().redSpawn() : match.arena().blueSpawn();
        if (spawn != null) event.setRespawnLocation(spawn);
        session.setRespawnPending(false);
        long delay = Math.max(0, plugin.respawnDelayTicks());
        String scheduledMatchId = match.matchId();
        final BukkitTask[] scheduled = new BukkitTask[1];
        scheduled[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            match.removeRespawnTask(scheduled[0]);
            if (match.state() != GameState.RUNNING || !java.util.Objects.equals(scheduledMatchId, match.matchId())) return;
            Player online = Bukkit.getPlayer(player.getUniqueId());
            PlayerSession current = match.sessions().get(player.getUniqueId());
            if (online != null && current != null) {
                preparePlayer(online, current.team(), match);
                if (spawn != null) online.teleport(spawn);
                scoreboardService.update(match);
            }
        }, delay);
        match.addRespawnTask(scheduled[0]);
    }

    public void checkWin(ArenaMatch match) {
        if (match == null || match.state() != GameState.RUNNING) return;
        int target = plugin.scoreToWin();
        if (target <= 0) return;
        for (Team team : Team.values()) {
            if (match.scores().get(team) >= target) {
                end(match, team, false);
                return;
            }
        }
    }

    public void stopAll() {
        cleanupService.cleanupAll();
    }

    private void startCountdown(ArenaMatch match) {
        if (match.state() == GameState.COUNTDOWN) return;
        int seconds = plugin.countdownSeconds();
        if (seconds <= 0) {
            start(match);
            return;
        }
        match.setState(GameState.COUNTDOWN);
        match.setCountdownSeconds(seconds);
        broadcastCountdown(match);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (match.state() != GameState.COUNTDOWN) return;
            if (!match.arena().enabled() || !match.arena().isValid()) {
                abortCountdown(match);
                return;
            }
            if (match.queue().size() < plugin.minPlayers()) {
                cancelCountdown(match);
                return;
            }
            int remaining = match.countdownSeconds() - 1;
            match.setCountdownSeconds(remaining);
            if (remaining <= 0) {
                cancelCountdownTaskOnly(match);
                start(match);
            } else {
                broadcastCountdown(match);
            }
        }, 20L, 20L);
        match.setCountdownTask(task);
    }

    private void broadcastCountdown(ArenaMatch match) {
        plugin.broadcastQueued(match, "countdown", Map.of(
                "arena", match.arena().id(),
                "seconds", String.valueOf(match.countdownSeconds())
        ));
    }

    private void cancelCountdown(ArenaMatch match) {
        if (match.state() != GameState.COUNTDOWN) return;
        cancelCountdownTaskOnly(match);
        match.setState(GameState.WAITING);
        match.setCountdownSeconds(0);
        plugin.broadcastQueued(match, "countdown-cancelled", Map.of());
    }

    private void abortCountdown(ArenaMatch match) {
        if (match == null || match.state() != GameState.COUNTDOWN) return;
        cancelCountdownTaskOnly(match);
        match.setState(GameState.WAITING);
        match.setCountdownSeconds(0);
        plugin.broadcastQueued(match, "arena-unavailable", Map.of(
                "arena", match.arena().id()));
    }

    private void cancelCountdownTaskOnly(ArenaMatch match) {
        BukkitTask task = match.countdownTask();
        if (task != null) task.cancel();
        match.setCountdownTask(null);
    }

    private void scheduleTasks(ArenaMatch match) {
        long gemInterval = Math.max(1, plugin.gemSpawnIntervalSeconds()) * 20L;
        BukkitTask gemTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> gemService.spawn(match), gemInterval, gemInterval);
        match.setGemTask(gemTask);
        BukkitTask timerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (match.state() != GameState.RUNNING) return;
            int remaining = match.remainingSeconds() - 1;
            match.setRemainingSeconds(remaining);
            scoreboardService.update(match);
            if (remaining <= 0) endByTimeout(match);
        }, 20L, 20L);
        match.setTimerTask(timerTask);
    }

    private void endByTimeout(ArenaMatch match) {
        int red = match.scores().get(Team.RED);
        int blue = match.scores().get(Team.BLUE);
        Team winner = red == blue ? null : (red > blue ? Team.RED : Team.BLUE);
        end(match, winner, false);
    }

    private void end(ArenaMatch match, Team winner, boolean forced) {
        if (match.state() == GameState.ENDING) return;
        match.setState(GameState.ENDING);
        match.cancelTasks();
        if (forced) {
            plugin.broadcast(match, "forced-stop", Map.of());
        } else if (winner == null) {
            if (match.remainingSeconds() <= 0) plugin.broadcast(match, "timeout", Map.of());
            plugin.broadcast(match, "draw", Map.of(
                    "red", String.valueOf(match.scores().get(Team.RED)),
                    "blue", String.valueOf(match.scores().get(Team.BLUE))
            ));
        } else {
            plugin.broadcast(match, "winner", Map.of(
                    "team", winner.coloredName(plugin),
                    "score", String.valueOf(match.scores().get(winner))
            ));
        }
        java.util.Set<UUID> affected = new HashSet<>(match.queue());
        affected.addAll(match.sessions().keySet());
        for (UUID uuid : affected) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) closePlayerInterfaces(player);
        }
        cleanupService.cleanup(match);
        match.reset();
    }

    private void preparePlayer(Player player, Team team, ArenaMatch match) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        if (player.getMaxHealth() > 0) player.setHealth(player.getMaxHealth());
        plugin.menuItemService().ensure(player);
        giveKit(player, team);
        PlayerSession session = match.sessions().get(player.getUniqueId());
        if (session != null) plugin.jobService().prepare(player, match, session);
        Location spawn = team == Team.RED ? match.arena().redSpawn() : match.arena().blueSpawn();
        if (spawn != null) player.teleport(spawn);
    }

    private void giveKit(Player player, Team team) {
        Material sword = plugin.kitSword();
        Material blocks = plugin.kitBlockMaterial();
        if (sword != null && sword != Material.AIR) player.getInventory().addItem(new ItemStack(sword, 1));
        if (blocks != null && blocks != Material.AIR && plugin.kitBlocks() > 0) {
            player.getInventory().addItem(new ItemStack(blocks, plugin.kitBlocks()));
        }
        if (plugin.kitFood() > 0) player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, plugin.kitFood()));
    }

    private void closePlayerInterfaces(Player player) {
        plugin.menuService().close(player);
        plugin.shopInventoryService().close(player);
        plugin.jobSelectionService().close(player);
    }

    private boolean teamHasNoPlayers(ArenaMatch match) {
        return match.teams().get(Team.RED).isEmpty() || match.teams().get(Team.BLUE).isEmpty();
    }

    private Team otherTeamWithPlayers(ArenaMatch match) {
        boolean red = !match.teams().get(Team.RED).isEmpty();
        boolean blue = !match.teams().get(Team.BLUE).isEmpty();
        if (red == blue) return null;
        return red ? Team.RED : Team.BLUE;
    }
}
