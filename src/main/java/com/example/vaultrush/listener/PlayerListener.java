package com.example.vaultrush.listener;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaMatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerKickEvent;

public final class PlayerListener implements Listener {
    private final VaultRushPlugin plugin;

    public PlayerListener(VaultRushPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        boolean restored = plugin.cleanupService()
                .restorePending(event.getPlayer());
        plugin.send(event.getPlayer(), "join-guidance", java.util.Map.of());
        if (restored || !plugin.autoOpenMenuOnJoin()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) plugin.menuService().open(event.getPlayer());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        plugin.gemService().handlePickup(event);
        if (event.getEntity() instanceof Player player) {
            checkDeposit(player, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        checkDeposit(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkDeposit(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (event.getHand() != null
                && (plugin.jobService().activate(event.getPlayer(), event.getHand())
                || plugin.shopService().activate(event.getPlayer(), event.getHand()))) {
            event.setCancelled(true);
        }
        checkDeposit(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ArenaMatch match = plugin.matchController().matchFor(player.getUniqueId());
        if (match != null) {
            com.example.vaultrush.model.PlayerSession session = match.sessions().get(player.getUniqueId());
            if (session != null) session.clearTacticalEffects();
            plugin.gemService().dropCarried(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.matchController().handleRespawn(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        plugin.menuService().close(event.getPlayer());
        plugin.shopInventoryService().close(event.getPlayer());
        plugin.jobSelectionService().close(event.getPlayer());
        plugin.matchController().handleQuit(event.getPlayer().getUniqueId());
        plugin.worldProtectionService().forgetPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        plugin.menuService().close(event.getPlayer());
        plugin.shopInventoryService().close(event.getPlayer());
        plugin.jobSelectionService().close(event.getPlayer());
        plugin.matchController().handleQuit(event.getPlayer().getUniqueId());
        plugin.worldProtectionService().forgetPlayer(event.getPlayer().getUniqueId());
    }

    private void checkDeposit(Player player, org.bukkit.Location candidate) {
        if (plugin.vaultService().tryDeposit(player, candidate)) {
            ArenaMatch match = plugin.matchController().matchFor(player.getUniqueId());
            plugin.matchController().checkWin(match);
        }
    }
}
