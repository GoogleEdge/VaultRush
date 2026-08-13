package com.example.vaultrush.game;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.model.PlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GemService {
    private final VaultRushPlugin plugin;
    private final ArenaManager arenaManager;
    private final NamespacedKey gemKey;
    private final NamespacedKey arenaKey;
    private final NamespacedKey matchKey;
    private final NamespacedKey centralKey;
    private final Map<String, Set<UUID>> trackedEntities = new HashMap<>();

    public GemService(VaultRushPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.gemKey = new NamespacedKey(plugin, "vault_gem");
        this.arenaKey = new NamespacedKey(plugin, "arena");
        this.matchKey = new NamespacedKey(plugin, "match");
        this.centralKey = new NamespacedKey(plugin, "central");
    }

    public void spawn(ArenaMatch match) {
        if (match == null || match.state() != GameState.RUNNING) return;
        int maximum = plugin.maxVaultGems();
        if (match.vaultGems() >= maximum) return;
        Location vault = match.arena().vault();
        if (vault == null || vault.getWorld() == null) return;
        dropMarked(vault.clone().add(0.0, 0.5, 0.0), 1, match, true);
        match.addVaultGem();
    }

    public void handlePickup(EntityPickupItemEvent event) {
        if (event == null || event.isCancelled()
                || !(event.getEntity() instanceof Player player)) return;
        Item item = event.getItem();
        if (!isMarkedGem(item.getItemStack())) return;
        event.setCancelled(true);
        ArenaMatch match = arenaManager.findByPlayer(player.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING
                || !belongsToMatch(item.getItemStack(), match)) return;
        PlayerSession session = match.sessions().get(player.getUniqueId());
        if (session == null) return;
        int amount = Math.max(1, item.getItemStack().getAmount());
        if (isCentral(item.getItemStack())) match.removeVaultGems(amount);
        session.addCarriedGems(amount);
        plugin.jobService().onGemPickup(player, match, session);
        untrack(item, match);
        item.remove();
        player.sendMessage(plugin.message("gem-picked", Map.of("carried", String.valueOf(session.carriedGems()))));
        plugin.scoreboardService().update(match);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (match.state() != GameState.RUNNING || match.sessions().get(player.getUniqueId()) != session) return;
            if (plugin.vaultService().tryDeposit(player, player.getLocation())) {
                plugin.matchController().checkWin(match);
            }
        });
    }

    public void dropCarried(Player player, PlayerDeathEvent event) {
        ArenaMatch match = arenaManager.findByPlayer(player.getUniqueId());
        if (match == null || match.state() != GameState.RUNNING) return;
        PlayerSession session = match.sessions().get(player.getUniqueId());
        if (session == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        int carried = session.carriedGems();
        session.setCarriedGems(0);
        int configured = plugin.deathGems();
        int amount = configured <= 0 ? carried : Math.min(carried, configured);
        if (plugin.dropCarriedGems() && amount > 0) {
            dropMarked(player.getLocation(), amount, match, false);
            plugin.broadcast(match, "death-drop", Map.of(
                    "player", player.getName(),
                    "amount", String.valueOf(amount)
            ));
        }
        session.setRespawnPending(true);
        plugin.scoreboardService().update(match);
    }

    public void clear(ArenaMatch match) {
        if (match == null) return;
        Set<UUID> tracked = trackedEntities.remove(match.arena().id());
        if (tracked != null) {
            for (UUID uuid : new HashSet<>(tracked)) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity instanceof Item) entity.remove();
            }
        }
        if (match.arena().vault() == null || match.arena().vault().getWorld() == null) return;
        for (Item item : match.arena().vault().getWorld().getEntitiesByClass(Item.class)) {
            if (belongsToMatch(item.getItemStack(), match)) item.remove();
        }
    }

    public void clearPluginOwnedEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isMarkedGem(item.getItemStack())) item.remove();
            }
        }
        trackedEntities.clear();
    }

    public boolean isMarkedGem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(gemKey, PersistentDataType.BYTE);
    }

    private boolean belongsToMatch(ItemStack stack, ArenaMatch match) {
        if (!isMarkedGem(stack)) return false;
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        String arena = container.get(arenaKey, PersistentDataType.STRING);
        String id = container.get(matchKey, PersistentDataType.STRING);
        return match.arena().id().equals(arena) && match.matchId() != null && match.matchId().equals(id);
    }

    private boolean isCentral(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte value = meta.getPersistentDataContainer().get(
                centralKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void dropMarked(Location location, int amount, ArenaMatch match, boolean central) {
        if (location == null || location.getWorld() == null || amount <= 0) return;
        Material material = plugin.gemMaterial();
        String arenaId = match.arena().id();
        Set<UUID> tracked = trackedEntities.computeIfAbsent(arenaId, ignored -> new HashSet<>());
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(64, remaining);
            ItemStack stack = createGem(stackAmount, match, central, material);
            Item item = location.getWorld().dropItem(location, stack);
            item.setPickupDelay(plugin.pickupDelayTicks());
            item.setUnlimitedLifetime(true);
            item.setCanMobPickup(false);
            item.setVelocity(item.getVelocity().zero());
            tracked.add(item.getUniqueId());
            remaining -= stackAmount;
        }
    }

    private ItemStack createGem(int amount, ArenaMatch match, boolean central, Material material) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.gemName()));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(gemKey, PersistentDataType.BYTE, (byte) 1);
            container.set(arenaKey, PersistentDataType.STRING, match.arena().id());
            container.set(matchKey, PersistentDataType.STRING, match.matchId());
            container.set(centralKey, PersistentDataType.BYTE, central ? (byte) 1 : (byte) 0);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void untrack(Item item, ArenaMatch match) {
        Set<UUID> tracked = trackedEntities.get(match.arena().id());
        if (tracked != null) tracked.remove(item.getUniqueId());
    }
}
