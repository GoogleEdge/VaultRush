package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.model.PlayerSession;
import com.example.vaultrush.model.ShopItem;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopService {
    public enum PurchaseResult { SUCCESS, DISABLED, NOT_RUNNING, NOT_AT_DEPOSIT, INSUFFICIENT_FUNDS, LIMIT_REACHED, COOLDOWN, INVENTORY_FULL }

    private final VaultRushPlugin plugin;
    private final ArenaManager arenaManager;
    private final VaultService vaultService;
    private final NamespacedKey itemKey;
    private final NamespacedKey matchKey;

    public ShopService(VaultRushPlugin plugin, ArenaManager arenaManager, VaultService vaultService) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.vaultService = vaultService;
        this.itemKey = new NamespacedKey(plugin, "shop_item");
        this.matchKey = new NamespacedKey(plugin, "shop_match");
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("shop.enabled", true); }
    public int cost(ShopItem item) { return Math.max(0, plugin.getConfig().getInt(item.configPath() + ".cost", 0)); }
    public int limit(ShopItem item) { return Math.max(0, plugin.getConfig().getInt(item.configPath() + ".purchase-limit", 0)); }
    public int cooldownSeconds(ShopItem item) { return Math.max(0, plugin.getConfig().getInt(item.configPath() + ".cooldown-seconds", 0)); }
    public int durationSeconds(ShopItem item) { return Math.max(1, plugin.getConfig().getInt(item.configPath() + ".duration-seconds", 1)); }

    public ArenaMatch runningMatch(Player player) {
        if (player == null) return null;
        ArenaMatch match = arenaManager.findByPlayer(player.getUniqueId());
        return match != null && match.state() == GameState.RUNNING ? match : null;
    }

    public PurchaseResult accessResult(Player player) {
        if (!enabled()) return PurchaseResult.DISABLED;
        if (runningMatch(player) == null) return PurchaseResult.NOT_RUNNING;
        return vaultService.isInDeposit(player) ? PurchaseResult.SUCCESS : PurchaseResult.NOT_AT_DEPOSIT;
    }

    public boolean canOpen(Player player) {
        return accessResult(player) == PurchaseResult.SUCCESS;
    }

    public PurchaseResult purchase(Player player, ShopItem item, String expectedMatchId) {
        if (!enabled()) return PurchaseResult.DISABLED;
        ArenaMatch match = runningMatch(player);
        if (match == null || item == null || expectedMatchId != null && !expectedMatchId.equals(match.matchId())) {
            return PurchaseResult.NOT_RUNNING;
        }
        if (!vaultService.isInDeposit(player)) return PurchaseResult.NOT_AT_DEPOSIT;
        PlayerSession session = match.sessions().get(player.getUniqueId());
        if (session == null) return PurchaseResult.NOT_RUNNING;
        int price = cost(item);
        if (session.tacticalCurrency() < price) return PurchaseResult.INSUFFICIENT_FUNDS;
        int maximum = limit(item);
        if (maximum > 0 && session.purchases(item) >= maximum) return PurchaseResult.LIMIT_REACHED;
        long now = System.currentTimeMillis();
        if (session.cooldownRemaining(item, now) > 0) return PurchaseResult.COOLDOWN;
        if (!session.spendTacticalCurrency(price)) return PurchaseResult.INSUFFICIENT_FUNDS;
        if (!giveItem(player, item, match)) {
            session.addTacticalCurrency(price);
            return PurchaseResult.INVENTORY_FULL;
        }
        session.recordPurchase(item, now + cooldownSeconds(item) * 1000L);
        plugin.send(player, "shop-purchased", Map.of("item", item.displayName(), "balance", String.valueOf(session.tacticalCurrency())));
        plugin.scoreboardService().update(match);
        return PurchaseResult.SUCCESS;
    }

    public void sendFailure(Player player, ShopItem item, PurchaseResult result) {
        if (player == null || result == PurchaseResult.SUCCESS) return;
        String key = switch (result) {
            case DISABLED -> "shop-disabled";
            case NOT_RUNNING -> "shop-not-running";
            case NOT_AT_DEPOSIT -> "shop-not-at-deposit";
            case INSUFFICIENT_FUNDS -> "shop-insufficient";
            case LIMIT_REACHED -> "shop-limit";
            case COOLDOWN -> "shop-cooldown";
            case INVENTORY_FULL -> "shop-inventory-full";
            default -> "shop-not-running";
        };
        PlayerSession session = session(player);
        long remaining = session == null || item == null ? 0L : session.cooldownRemaining(item, System.currentTimeMillis());
        plugin.send(player, key, Map.of(
                "item", item == null ? "" : item.displayName(),
                "balance", String.valueOf(session == null ? 0 : session.tacticalCurrency()),
                "seconds", String.valueOf((remaining + 999L) / 1000L)
        ));
    }

    public void awardDeposit(Player player, int gems) {
        award(player, Math.max(0, gems) * Math.max(0, plugin.getConfig().getInt("shop.currency.deposit-reward-per-gem", 1)), "currency-deposit");
    }

    public void awardKill(Player killer) {
        award(killer, Math.max(0, plugin.getConfig().getInt("shop.currency.kill-reward", 3)), "currency-kill");
    }

    private void award(Player player, int amount, String message) {
        PlayerSession session = session(player);
        ArenaMatch match = runningMatch(player);
        if (session == null || match == null || amount <= 0) return;
        session.addTacticalCurrency(amount);
        plugin.send(player, message, Map.of("amount", String.valueOf(amount), "balance", String.valueOf(session.tacticalCurrency())));
        plugin.scoreboardService().update(match);
    }

    public boolean activate(Player player, EquipmentSlot hand) {
        ArenaMatch match = runningMatch(player);
        if (match == null) return false;
        ItemStack stack = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        ShopItem item = markedItem(stack, match.matchId());
        PlayerSession session = match.sessions().get(player.getUniqueId());
        if (item == null || session == null) return false;
        consume(stack);
        long now = System.currentTimeMillis();
        switch (item) {
            case SPEED -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationSeconds(item) * 20, amplifier(item)));
            case JUMP -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationSeconds(item) * 20, amplifier(item)));
            case FIREBALL -> propel(player);
            case SHIELD -> session.activateShield(now + durationSeconds(item) * 1000L);
            case DAMAGE_BOOST -> session.activateDamageBoost(now + durationSeconds(item) * 1000L);
            case SMOKE -> smoke(player, match);
        }
        plugin.send(player, "shop-activated", Map.of("item", item.displayName()));
        return true;
    }

    public PlayerSession session(Player player) {
        ArenaMatch match = runningMatch(player);
        return match == null ? null : match.sessions().get(player.getUniqueId());
    }

    public double shieldMultiplier(PlayerSession session) {
        if (session == null || !session.shieldActive(System.currentTimeMillis())) return 1.0;
        double percent = Math.max(0.0, Math.min(100.0, plugin.getConfig().getDouble("shop.items.shield.damage-reduction-percent", 30.0)));
        return 1.0 - percent / 100.0;
    }

    public double damageBonus(PlayerSession session) {
        if (session == null || !session.damageBoostActive(System.currentTimeMillis())) return 0.0;
        return Math.max(0.0, plugin.getConfig().getDouble("shop.items.damage-boost.bonus-damage", 3.0));
    }

    public String status(PlayerSession session, ShopItem item) {
        if (session == null) return "不可用";
        long cooldown = session.cooldownRemaining(item, System.currentTimeMillis());
        int maximum = limit(item);
        String count = maximum <= 0 ? "不限" : session.purchases(item) + "/" + maximum;
        return cooldown > 0 ? "冷却 " + ((cooldown + 999L) / 1000L) + "秒；次数 " + count : "可购买；次数 " + count;
    }

    public List<String> lore(PlayerSession session, ShopItem item) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + description(item));
        lore.add(ChatColor.GOLD + "价格：" + cost(item) + " 战术币");
        lore.add(ChatColor.YELLOW + "余额：" + (session == null ? 0 : session.tacticalCurrency()));
        lore.add(ChatColor.AQUA + status(session, item));
        lore.add(ChatColor.DARK_GRAY + "购买后右键使用");
        return lore;
    }

    private boolean giveItem(Player player, ShopItem item, ArenaMatch match) {
        ItemStack stack = new ItemStack(item.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + item.displayName());
        meta.setLore(List.of(ChatColor.GRAY + description(item), ChatColor.YELLOW + "右键使用"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, item.id());
        meta.getPersistentDataContainer().set(matchKey, PersistentDataType.STRING, match.matchId());
        stack.setItemMeta(meta);
        return player.getInventory().addItem(stack).isEmpty();
    }

    private ShopItem markedItem(ItemStack stack, String matchId) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        String markedMatch = stack.getItemMeta().getPersistentDataContainer().get(matchKey, PersistentDataType.STRING);
        return matchId != null && matchId.equals(markedMatch) ? ShopItem.fromId(id) : null;
    }

    private void consume(ItemStack stack) {
        if (stack.getAmount() <= 1) stack.setAmount(0);
        else stack.setAmount(stack.getAmount() - 1);
    }

    private int amplifier(ShopItem item) {
        return Math.max(0, plugin.getConfig().getInt(item.configPath() + ".amplifier", 1));
    }

    private void propel(Player player) {
        double horizontalPower = Math.max(0.0,
                plugin.getConfig().getDouble(
                        "shop.items.fireball.horizontal-power", 1.8));
        double verticalPower = Math.max(0.0,
                plugin.getConfig().getDouble(
                        "shop.items.fireball.vertical-power", 1.6));

        Vector look = player.getLocation().getDirection();
        Vector horizontal = new Vector(look.getX(), 0.0, look.getZ());
        if (horizontal.lengthSquared() < 1.0e-6) {
            float yaw = player.getLocation().getYaw();
            double radians = Math.toRadians(yaw);
            horizontal = new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
        } else {
            horizontal.normalize();
        }

        player.setVelocity(new Vector(
                horizontal.getX() * horizontalPower,
                verticalPower,
                horizontal.getZ() * horizontalPower));
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 24,
                0.35, 0.35, 0.35, 0.02);
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
    }

    private void smoke(Player source, ArenaMatch match) {
        double radius = Math.max(0.0,
                plugin.getConfig().getDouble("shop.items.smoke.radius", 8.0));
        int duration = durationSeconds(ShopItem.SMOKE) * 20;
        int amplifier = amplifier(ShopItem.SMOKE);
        source.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                source.getLocation().add(0, 1, 0), 100,
                radius / 3, 1.5, radius / 3, 0.02);
        PlayerSession sourceSession = match.sessions().get(source.getUniqueId());
        if (sourceSession == null) return;
        for (Map.Entry<java.util.UUID, PlayerSession> entry : match.sessions().entrySet()) {
            if (entry.getValue().team() == sourceSession.team()) continue;
            Player target = plugin.getServer().getPlayer(entry.getKey());
            if (target != null && target.getWorld() != null
                    && source.getWorld() != null
                    && target.getWorld().getUID().equals(source.getWorld().getUID())
                    && target.getLocation().distanceSquared(source.getLocation()) <= radius * radius) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, duration, amplifier));
            }
        }
    }

    public String description(ShopItem item) {
        return switch (item) {
            case SPEED -> "短时间获得速度 II";
            case JUMP -> "短时间获得跳跃提升 II";
            case FIREBALL -> "水平前冲并向上约 8 格，不爆炸、不点火、不伤人";
            case SHIELD -> "短时间降低受到的伤害";
            case DAMAGE_BOOST -> "短时间增加对敌伤害";
            case SMOKE -> "使 8 格内敌人获得 Blindness II，默认约 8 秒";
        };
    }
}
