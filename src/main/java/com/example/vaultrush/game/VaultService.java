package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.arena.Team;
import com.example.vaultrush.model.PlayerSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;

public final class VaultService {
    private final VaultRushPlugin plugin;
    private final ArenaManager arenaManager;

    public VaultService(VaultRushPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    public boolean tryDeposit(Player player) {
        return player != null && tryDeposit(player, player.getLocation());
    }

    public boolean tryDeposit(Player player, Location candidate) {
        if (player == null || candidate == null) return false;
        ArenaMatch match = arenaManager.findByPlayer(player.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING) return false;
        PlayerSession session = match.sessions().get(player.getUniqueId());
        return tryDeposit(player, candidate, match, session);
    }

    public boolean tryDeposit(Player player, Location candidate,
                              ArenaMatch match, PlayerSession session) {
        if (player == null || candidate == null || match == null
                || match.state() != GameState.RUNNING || session == null
                || session.carriedGems() <= 0) return false;
        if (!isInDeposit(candidate, match, session)) return false;

        int amount = session.carriedGems();
        session.setCarriedGems(0);
        int score = match.scores().get(session.team()) + amount;
        match.scores().put(session.team(), score);
        plugin.broadcast(match, "gem-deposited", Map.of(
                "player", player.getName(),
                "amount", String.valueOf(amount),
                "team", session.team().coloredName(plugin),
                "score", String.valueOf(score)
        ));
        plugin.shopService().awardDeposit(player, amount);
        plugin.scoreboardService().update(match);
        return true;
    }

    public boolean isInDeposit(Player player) {
        return player != null && isInDeposit(player, player.getLocation());
    }

    public boolean isInDeposit(Player player, Location candidate) {
        if (player == null || candidate == null) return false;
        ArenaMatch match = arenaManager.findByPlayer(player.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING) return false;
        PlayerSession session = match.sessions().get(player.getUniqueId());
        return session != null && isInDeposit(candidate, match, session);
    }

    private boolean isInDeposit(Location candidate, ArenaMatch match, PlayerSession session) {
        Location deposit = session.team() == Team.RED
                ? match.arena().redDeposit()
                : match.arena().blueDeposit();
        if (deposit == null || !sameWorld(candidate, deposit)) return false;

        double dx = candidate.getX() - deposit.getX();
        double dz = candidate.getZ() - deposit.getZ();
        double horizontalRadius = plugin.vaultRadius();
        double verticalRadius = plugin.depositVerticalRadius();
        return dx * dx + dz * dz <= horizontalRadius * horizontalRadius
                && Math.abs(candidate.getY() - deposit.getY()) <= verticalRadius;
    }

    private boolean sameWorld(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null) return false;
        return first.getWorld().getUID().equals(second.getWorld().getUID());
    }
}
