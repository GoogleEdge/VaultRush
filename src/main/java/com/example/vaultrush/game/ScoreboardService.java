package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.Team;
import com.example.vaultrush.model.PlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScoreboardService {
    private final VaultRushPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Map<Integer, String>> lines = new HashMap<>();

    public ScoreboardService(VaultRushPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(ArenaMatch match) {
        update(match);
    }

    public void update(ArenaMatch match) {
        if (match == null) return;
        for (Map.Entry<UUID, PlayerSession> entry : match.sessions().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            UUID uuid = entry.getKey();
            Scoreboard board = boards.computeIfAbsent(uuid,
                    ignored -> Bukkit.getScoreboardManager().getNewScoreboard());
            Objective objective = board.getObjective("vaultrush");
            if (objective == null) {
                objective = board.registerNewObjective("vaultrush", "dummy",
                        plugin.menuText("scoreboard-title", "&6宝库争夺", Map.of()));
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            PlayerSession session = entry.getValue();
            Map<Integer, String> next = new HashMap<>();
            next.put(9, plugin.menuText("scoreboard-divider", "&7────────────", Map.of()));
            next.put(8, plugin.menuText("scoreboard-red", "&c红队：&f%score%",
                    Map.of("score", String.valueOf(match.scores().get(Team.RED)))));
            next.put(7, plugin.menuText("scoreboard-blue", "&9蓝队：&f%score%",
                    Map.of("score", String.valueOf(match.scores().get(Team.BLUE)))));
            next.put(6, plugin.menuText("scoreboard-job", "&d职业：&f%job%",
                    Map.of("job", session.job().displayName(plugin))));
            next.put(5, plugin.menuText("scoreboard-carried", "&e携带：&f%amount%",
                    Map.of("amount", String.valueOf(session.carriedGems()))));
            next.put(4, plugin.menuText("scoreboard-currency", "&6战术币：&f%amount%",
                    Map.of("amount", String.valueOf(session.tacticalCurrency()))));
            next.put(3, plugin.menuText("scoreboard-target", "&a目标：&f%score%",
                    Map.of("score", String.valueOf(plugin.scoreToWin()))));
            next.put(2, plugin.menuText("scoreboard-time", "&b时间：&f%time%",
                    Map.of("time", formatTime(match.remainingSeconds()))));
            next.put(1, ChatColor.DARK_GRAY + plugin.menuText(
                    "scoreboard-divider", "&7────────────", Map.of()));

            Map<Integer, String> previous = lines.computeIfAbsent(uuid,
                    ignored -> new HashMap<>());
            for (Map.Entry<Integer, String> line : next.entrySet()) {
                String old = previous.get(line.getKey());
                if (line.getValue().equals(old)) continue;
                if (old != null) board.resetScores(old);
                objective.getScore(line.getValue()).setScore(line.getKey());
            }
            previous.clear();
            previous.putAll(next);
            if (player.getScoreboard() != board) player.setScoreboard(board);
        }
    }

    public void clear(ArenaMatch match) {
        if (match == null) return;
        for (UUID uuid : new HashSet<>(match.sessions().keySet())) {
            Scoreboard board = boards.remove(uuid);
            lines.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && board != null && player.getScoreboard() == board) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            if (board != null) {
                for (Objective objective : new HashSet<>(board.getObjectives())) objective.unregister();
            }
        }
    }

    public void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        Scoreboard board = boards.remove(uuid);
        lines.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline() && board != null && player.getScoreboard() == board) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        if (board != null) {
            for (Objective objective : new HashSet<>(board.getObjectives())) objective.unregister();
        }
    }

    private static void addLine(Objective objective, String text, int score) {
        objective.getScore(text).setScore(score);
    }

    private static String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("%02d:%02d", safe / 60, safe % 60);
    }
}
