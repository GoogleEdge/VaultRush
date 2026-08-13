package com.example.vaultrush.listener;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.model.PlayerSession;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatRuleListener implements Listener {
    private final VaultRushPlugin plugin;

    public CombatRuleListener(VaultRushPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        ArenaMatch match = plugin.matchController().matchFor(victim.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING) return;
        PlayerSession victimSession = match.sessions().get(victim.getUniqueId());
        if (victimSession == null) return;
        event.setDamage(event.getDamage()
                * plugin.shopService().shieldMultiplier(victimSession));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        ArenaMatch victimMatch = plugin.matchController()
                .matchFor(victim.getUniqueId());
        if (victimMatch == null || victimMatch.state() != GameState.RUNNING) return;
        PlayerSession victimSession = victimMatch.sessions()
                .get(victim.getUniqueId());
        if (victimSession == null) return;

        Player attacker = attacker(event);
        if (attacker != null) {
            ArenaMatch attackerMatch = plugin.matchController()
                    .matchFor(attacker.getUniqueId());
            if (attackerMatch != victimMatch) {
                event.setCancelled(true);
                return;
            }
            PlayerSession attackerSession = victimMatch.sessions()
                    .get(attacker.getUniqueId());
            if (attackerSession != null
                    && attackerSession.team() == victimSession.team()) {
                event.setCancelled(true);
                return;
            }
            if (attackerSession != null) {
                double damage = event.getDamage()
                        * plugin.jobService()
                                .outgoingMultiplier(attackerSession);
                damage += plugin.shopService()
                        .damageBonus(attackerSession);
                damage *= plugin.jobService()
                        .incomingMultiplier(victimSession);
                damage *= plugin.shopService()
                        .shieldMultiplier(victimSession);
                event.setDamage(damage);
                return;
            }
        }
        event.setDamage(event.getDamage()
                * plugin.shopService().shieldMultiplier(victimSession));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        ArenaMatch match = plugin.matchController().matchFor(victim.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING
                || plugin.matchController().matchFor(killer.getUniqueId()) != match) return;
        PlayerSession victimSession = match.sessions().get(victim.getUniqueId());
        PlayerSession killerSession = match.sessions().get(killer.getUniqueId());
        if (victimSession != null && killerSession != null
                && victimSession.team() != killerSession.team()) {
            plugin.shopService().awardKill(killer);
        }
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        Player direct = attacker(event.getDamager());
        if (direct != null) return direct;
        DamageSource source = event.getDamageSource();
        return source == null ? null : attacker(source.getCausingEntity());
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }
}
