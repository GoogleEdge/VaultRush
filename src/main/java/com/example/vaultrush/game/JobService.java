package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.model.JobType;
import com.example.vaultrush.model.PlayerSession;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JobService {
    private final VaultRushPlugin plugin;
    private final NamespacedKey jobKey;
    private final NamespacedKey matchKey;
    private final NamespacedKey ownerKey;

    public JobService(VaultRushPlugin plugin) {
        this.plugin = plugin;
        this.jobKey = new NamespacedKey(plugin, "job_ability");
        this.matchKey = new NamespacedKey(plugin, "job_match");
        this.ownerKey = new NamespacedKey(plugin, "job_owner");
    }

    public void prepare(Player player, ArenaMatch match, PlayerSession session) {
        if (player == null || match == null || session == null) return;
        if (session.job() == JobType.ENGINEER) {
            int blocks = integer(session.job(), "extra-blocks", 16, 0);
            if (blocks > 0 && plugin.kitBlockMaterial() != null) {
                player.getInventory().addItem(new ItemStack(plugin.kitBlockMaterial(), blocks));
            }
        }
        giveAbilityItem(player, match, session);
    }

    public boolean activate(Player player, EquipmentSlot hand) {
        ArenaMatch match = plugin.matchController().matchFor(player.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING) return false;
        PlayerSession session = match.sessions().get(player.getUniqueId());
        if (session == null) return false;
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!isAbilityItem(stack, player.getUniqueId(), match.matchId(), session.job())) return false;

        long now = System.currentTimeMillis();
        long remaining = session.jobCooldownRemaining(now);
        if (remaining > 0) {
            plugin.send(player, "job-cooldown", Map.of(
                    "job", session.job().displayName(),
                    "seconds", String.valueOf((remaining + 999L) / 1000L)));
            return true;
        }

        if (!applyActive(player, match, session)) return true;
        int cooldown = integer(session.job(), "active.cooldown-seconds",
                defaultCooldown(session.job()), 0);
        session.startJobCooldown(now + cooldown * 1000L);
        plugin.send(player, "job-activated", Map.of(
                "job", session.job().displayName(),
                "ability", session.job().abilityName()));
        return true;
    }

    public void onGemPickup(Player player, ArenaMatch match, PlayerSession session) {
        if (player == null || match == null || session == null
                || match.state() != GameState.RUNNING) return;
        long now = System.currentTimeMillis();
        if (!session.jobPassiveReady(now)) return;
        if (session.job() == JobType.SCOUT) {
            int duration = integer(JobType.SCOUT,
                    "passive.duration-seconds", 3, 1);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, duration * 20, 0));
            session.startJobPassiveCooldown(now + integer(JobType.SCOUT,
                    "passive.cooldown-seconds", 10, 0) * 1000L);
        } else if (session.job() == JobType.ILLUSIONIST) {
            int duration = integer(JobType.ILLUSIONIST,
                    "passive.duration-seconds", 2, 1);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, duration * 20, 0));
            session.startJobPassiveCooldown(now + integer(JobType.ILLUSIONIST,
                    "passive.cooldown-seconds", 15, 0) * 1000L);
        }
    }

    public double outgoingMultiplier(PlayerSession session) {
        return session != null && session.job() == JobType.ASSAULT
                ? decimal(JobType.ASSAULT, "passive.damage-multiplier", 1.10, 0.0)
                : 1.0;
    }

    public double incomingMultiplier(PlayerSession session) {
        return session != null && session.job() == JobType.GUARDIAN
                ? decimal(JobType.GUARDIAN, "passive.damage-multiplier", 0.85, 0.0)
                : 1.0;
    }

    public String description(JobType job) {
        return switch (job) {
            case ASSAULT -> "对敌伤害提高 10%；主动冲锋";
            case SCOUT -> "拾取宝石获得短暂加速；主动标记附近敌人";
            case GUARDIAN -> "受到敌人伤害降低 15%；主动获得抗性提升";
            case ENGINEER -> "额外获得建筑方块；主动获得急迫 II";
            case ILLUSIONIST -> "拾取宝石短暂隐身；主动进入幻影状态";
        };
    }

    public List<String> lore(JobType job) {
        return List.of(
                ChatColor.GRAY + description(job),
                ChatColor.YELLOW + "主动技能：" + job.abilityName(),
                ChatColor.DARK_GRAY + "选择后加入比赛队列");
    }

    private boolean applyActive(Player player, ArenaMatch match, PlayerSession session) {
        JobType job = session.job();
        switch (job) {
            case ASSAULT -> dash(player);
            case SCOUT -> {
                if (!revealEnemies(player, match, session)) {
                    plugin.send(player, "job-no-target", Map.of());
                    return false;
                }
            }
            case GUARDIAN -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE,
                    integer(job, "active.duration-seconds", 4, 1) * 20, 0));
            case ENGINEER -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.HASTE,
                    integer(job, "active.duration-seconds", 8, 1) * 20, 1));
            case ILLUSIONIST -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    integer(job, "active.duration-seconds", 5, 1) * 20, 0));
        }
        return true;
    }

    private void dash(Player player) {
        double horizontalPower = decimal(JobType.ASSAULT,
                "active.horizontal-power", 1.25, 0.0);
        double verticalPower = decimal(JobType.ASSAULT,
                "active.vertical-power", 0.45, 0.0);
        Vector look = player.getLocation().getDirection();
        Vector horizontal = new Vector(look.getX(), 0.0, look.getZ());
        if (horizontal.lengthSquared() < 1.0e-6) {
            double radians = Math.toRadians(player.getLocation().getYaw());
            horizontal = new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
        } else {
            horizontal.normalize();
        }
        player.setVelocity(new Vector(horizontal.getX() * horizontalPower,
                verticalPower, horizontal.getZ() * horizontalPower));
    }

    private boolean revealEnemies(Player source, ArenaMatch match, PlayerSession session) {
        double radius = decimal(JobType.SCOUT, "active.radius", 24.0, 0.0);
        int duration = integer(JobType.SCOUT, "active.duration-seconds", 5, 1);
        boolean found = false;
        for (Map.Entry<UUID, PlayerSession> entry : match.sessions().entrySet()) {
            if (entry.getValue().team() == session.team()) continue;
            Player target = plugin.getServer().getPlayer(entry.getKey());
            if (target == null || !target.isOnline()
                    || !target.getWorld().getUID().equals(source.getWorld().getUID())) continue;
            if (target.getLocation().distanceSquared(source.getLocation()) > radius * radius) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    duration * 20, 0));
            found = true;
        }
        return found;
    }

    private void giveAbilityItem(Player player, ArenaMatch match, PlayerSession session) {
        ItemStack stack = new ItemStack(session.job().icon());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + session.job().abilityName()
                + ChatColor.GRAY + "（职业技能）");
        meta.setLore(List.of(
                ChatColor.GRAY + description(session.job()),
                ChatColor.YELLOW + "手持右键使用；不会被消耗"));
        meta.getPersistentDataContainer().set(jobKey,
                PersistentDataType.STRING, session.job().id());
        meta.getPersistentDataContainer().set(matchKey,
                PersistentDataType.STRING, match.matchId());
        meta.getPersistentDataContainer().set(ownerKey,
                PersistentDataType.STRING, player.getUniqueId().toString());
        stack.setItemMeta(meta);
        player.getInventory().addItem(stack);
    }

    private boolean isAbilityItem(ItemStack stack, UUID owner,
                                  String matchId, JobType expected) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        String job = meta.getPersistentDataContainer().get(jobKey,
                PersistentDataType.STRING);
        String markedMatch = meta.getPersistentDataContainer().get(matchKey,
                PersistentDataType.STRING);
        String markedOwner = meta.getPersistentDataContainer().get(ownerKey,
                PersistentDataType.STRING);
        return expected != null && expected == JobType.fromId(job)
                && matchId != null && matchId.equals(markedMatch)
                && owner.toString().equals(markedOwner);
    }

    private int defaultCooldown(JobType job) {
        return switch (job) {
            case ASSAULT -> 24;
            case SCOUT, GUARDIAN -> 30;
            case ENGINEER, ILLUSIONIST -> 25;
        };
    }

    private int integer(JobType job, String key, int fallback, int minimum) {
        return Math.max(minimum, plugin.getConfig().getInt(
                job.configPath() + "." + key, fallback));
    }

    private double decimal(JobType job, String key, double fallback,
                           double minimum) {
        return Math.max(minimum, plugin.getConfig().getDouble(
                job.configPath() + "." + key, fallback));
    }
}
