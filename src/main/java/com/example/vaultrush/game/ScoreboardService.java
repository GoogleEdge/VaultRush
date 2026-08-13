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
            Scoreboard board = boards.computeIfAbsent(entry.getKey(), ignored -> Bukkit.getScoreboardManager().getNewScoreboard());
            Objective objective = board.getObjective("vaultrush");
            if (objective == null) {
                objective = board.registerNewObjective("vaultrush", "dummy", ChatColor.GOLD + "宝库争夺");
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
            Set<String> oldEntries = new HashSet<>(board.getEntries());
            for (String oldEntry : oldEntries) board.resetScores(oldEntry);

            PlayerSession session = entry.getValue();
            addLine(objective, ChatColor.GRAY + "────────────", 9);
            addLine(objective, ChatColor.RED + "红队：" + ChatColor.WHITE + match.scores().get(Team.RED), 8);
            addLine(objective, ChatColor.BLUE + "蓝队：" + ChatColor.WHITE + match.scores().get(Team.BLUE), 7);
            addLine(objective, ChatColor.LIGHT_PURPLE + "职业：" + ChatColor.WHITE + session.job().displayName(), 6);
            addLine(objective, ChatColor.YELLOW + "携带：" + ChatColor.WHITE + session.carriedGems(), 5);
            addLine(objective, ChatColor.GOLD + "战术币：" + ChatColor.WHITE + session.tacticalCurrency(), 4);
            addLine(objective, ChatColor.GREEN + "目标：" + ChatColor.WHITE + plugin.scoreToWin(), 3);
            addLine(objective, ChatColor.AQUA + "时间：" + ChatColor.WHITE + formatTime(match.remainingSeconds()), 2);
            addLine(objective, ChatColor.DARK_GRAY + "────────────", 1);
            player.setScoreboard(board);
        }
    }

    public void clear(ArenaMatch match) {
        if (match == null) return;
        for (UUID uuid : new HashSet<>(match.sessions().keySet())) {
            Scoreboard board = boards.remove(uuid);
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
