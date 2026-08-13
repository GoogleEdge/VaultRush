package com.example.vaultrush.arena;

import com.example.vaultrush.model.JobType;
import com.example.vaultrush.model.PlayerSession;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaMatch {
    private final ArenaDefinition arena;
    private final Set<UUID> queue = new LinkedHashSet<>();
    private final Map<UUID, JobType> queuedJobs = new LinkedHashMap<>();
    private final Map<UUID, PlayerSession> sessions = new LinkedHashMap<>();
    private final Map<Team, Set<UUID>> teams = new EnumMap<>(Team.class);
    private final Map<Team, Integer> scores = new EnumMap<>(Team.class);
    private GameState state = GameState.WAITING;
    private String matchId;
    private int countdownSeconds;
    private int remainingSeconds;
    private int vaultGems;
    private BukkitTask countdownTask;
    private BukkitTask timerTask;
    private BukkitTask gemTask;
    private final Set<BukkitTask> respawnTasks = new LinkedHashSet<>();

    public ArenaMatch(ArenaDefinition arena) {
        this.arena = arena;
        for (Team team : Team.values()) {
            teams.put(team, new LinkedHashSet<>());
            scores.put(team, 0);
        }
    }

    public ArenaDefinition arena() { return arena; }
    public Set<UUID> queue() { return queue; }
    public Map<UUID, JobType> queuedJobs() { return queuedJobs; }
    public boolean enqueue(UUID uuid, JobType job) {
        if (uuid == null || job == null || !queue.add(uuid)) return false;
        queuedJobs.put(uuid, job);
        return true;
    }
    public boolean removeQueued(UUID uuid) {
        queuedJobs.remove(uuid);
        return queue.remove(uuid);
    }
    public JobType queuedJob(UUID uuid) { return queuedJobs.get(uuid); }
    public Map<UUID, PlayerSession> sessions() { return sessions; }
    public Map<Team, Set<UUID>> teams() { return teams; }
    public Map<Team, Integer> scores() { return scores; }
    public GameState state() { return state; }
    public void setState(GameState state) { this.state = state; }
    public String matchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public int countdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int value) { countdownSeconds = value; }
    public int remainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(int value) { remainingSeconds = value; }
    public int vaultGems() { return vaultGems; }
    public void addVaultGem() { vaultGems++; }
    public void removeVaultGems(int amount) {
        vaultGems = Math.max(0, vaultGems - Math.max(0, amount));
    }
    public BukkitTask countdownTask() { return countdownTask; }
    public void setCountdownTask(BukkitTask task) { countdownTask = task; }
    public BukkitTask timerTask() { return timerTask; }
    public void setTimerTask(BukkitTask task) { timerTask = task; }
    public BukkitTask gemTask() { return gemTask; }
    public void setGemTask(BukkitTask task) { gemTask = task; }
    public void addRespawnTask(BukkitTask task) {
        if (task != null) respawnTasks.add(task);
    }
    public void removeRespawnTask(BukkitTask task) {
        if (task != null) respawnTasks.remove(task);
    }

    public void cancelTasks() {
        if (countdownTask != null) countdownTask.cancel();
        if (timerTask != null) timerTask.cancel();
        if (gemTask != null) gemTask.cancel();
        for (BukkitTask task : respawnTasks) task.cancel();
        respawnTasks.clear();
        countdownTask = null;
        timerTask = null;
        gemTask = null;
    }

    public void reset() {
        cancelTasks();
        queue.clear();
        queuedJobs.clear();
        sessions.clear();
        for (Team team : Team.values()) {
            teams.get(team).clear();
            scores.put(team, 0);
        }
        matchId = null;
        countdownSeconds = 0;
        remainingSeconds = 0;
        vaultGems = 0;
        state = GameState.WAITING;
    }
}
