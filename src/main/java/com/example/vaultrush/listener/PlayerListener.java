package com.example.vaultrush.listener;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaMatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
        Player player = event.getPlayer();
        boolean hadPendingRestore = plugin.cleanupService()
                .hasPendingRestore(player.getUniqueId());
        boolean restored = plugin.cleanupService().restorePending(player);
        if (!hadPendingRestore || restored) plugin.menuItemService().ensure(player);
        plugin.send(player, "join-guidance", java.util.Map.of());
        if (hadPendingRestore || !plugin.autoOpenMenuOnJoin()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.menuService().open(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        plugin.gemService().handlePickup(event);
        if (event.getEntity() instanceof Player player) {
            checkDepositIfCarrying(player, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (from.getWorld() != null && from.getWorld().equals(to.getWorld())
                && from.getX() == to.getX()
                && from.getY() == to.getY()
                && from.getZ() == to.getZ()) return;
        checkDepositIfCarrying(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkDepositIfCarrying(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        if (event.getHand() != null) {
            com.example.vaultrush.menu.PlayerMenuItemService.ActivationResult menu =
                    plugin.menuItemService().activate(event.getPlayer(), event.getHand());
            if (menu != com.example.vaultrush.menu.PlayerMenuItemService.ActivationResult.NONE) {
                event.setCancelled(true);
                return;
            }
            if (plugin.jobService().activate(event.getPlayer(), event.getHand())
                    || plugin.shopService().activate(event.getPlayer(), event.getHand())) {
                event.setCancelled(true);
            }
        }
        checkDepositIfCarrying(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == null) return;
        com.example.vaultrush.menu.PlayerMenuItemService.ActivationResult menu =
                plugin.menuItemService().activate(event.getPlayer(), event.getHand());
        if (menu != com.example.vaultrush.menu.PlayerMenuItemService.ActivationResult.NONE) {
            event.setCancelled(true);
            return;
        }
        if (plugin.jobService().activate(event.getPlayer(), event.getHand())
                || plugin.shopService().activate(event.getPlayer(), event.getHand())) {
            event.setCancelled(true);
        }
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
        plugin.arenaManager().forgetPlayer(event.getPlayer().getUniqueId());
        plugin.menuItemService().forgetPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        plugin.menuService().close(event.getPlayer());
        plugin.shopInventoryService().close(event.getPlayer());
        plugin.jobSelectionService().close(event.getPlayer());
        plugin.matchController().handleQuit(event.getPlayer().getUniqueId());
        plugin.worldProtectionService().forgetPlayer(event.getPlayer().getUniqueId());
        plugin.arenaManager().forgetPlayer(event.getPlayer().getUniqueId());
        plugin.menuItemService().forgetPlayer(event.getPlayer().getUniqueId());
    }

    private void checkDepositIfCarrying(Player player,
                                        org.bukkit.Location candidate) {
        if (player == null || candidate == null) return;
        ArenaMatch match = plugin.matchController().matchFor(player.getUniqueId());
        if (match == null || match.state() != com.example.vaultrush.arena.GameState.RUNNING) return;
        com.example.vaultrush.model.PlayerSession session =
                match.sessions().get(player.getUniqueId());
        if (session == null || session.carriedGems() <= 0) return;
        if (plugin.vaultService().tryDeposit(player, candidate, match, session)) {
            plugin.matchController().checkWin(match);
        }
    }
}
