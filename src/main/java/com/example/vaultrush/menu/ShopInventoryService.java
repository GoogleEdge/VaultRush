package com.example.vaultrush.menu;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.game.ShopService;
import com.example.vaultrush.model.PlayerSession;
import com.example.vaultrush.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShopInventoryService implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};
    private final VaultRushPlugin plugin;
    private final ShopService shopService;
    private final BedrockMenuBridge bedrockBridge;
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final Map<UUID, Long> bedrockGenerations = new HashMap<>();

    public ShopInventoryService(VaultRushPlugin plugin, ShopService shopService, BedrockMenuBridge bedrockBridge) {
        this.plugin = plugin;
        this.shopService = shopService;
        this.bedrockBridge = bedrockBridge;
    }

    public void open(Player player) {
        if (player == null || !player.isOnline()) return;
        if (plugin.menuService() != null) plugin.menuService().close(player);
        if (plugin.jobSelectionService() != null) {
            plugin.jobSelectionService().close(player);
        }
        ShopService.PurchaseResult access = shopService.accessResult(player);
        if (access != ShopService.PurchaseResult.SUCCESS) {
            shopService.sendFailure(player, null, access);
            return;
        }
        boolean bedrock;
        try {
            bedrock = bedrockBridge.isBedrock(player.getUniqueId());
        } catch (RuntimeException exception) {
            bedrock = false;
        }
        if (bedrock) {
            openBedrock(player);
            return;
        }
        openJava(player);
    }

    public void openJava(Player player) {
        if (player == null) return;
        bedrockGenerations.merge(player.getUniqueId(), 1L, Long::sum);
        bedrockBridge.close(player.getUniqueId());
        ArenaMatch match = shopService.runningMatch(player);
        if (match == null || !shopService.canOpen(player)) return;
        Inventory previous = open.remove(player.getUniqueId());
        if (previous != null
                && player.getOpenInventory().getTopInventory() == previous) {
            player.closeInventory();
        }
        ShopInventoryHolder holder = new ShopInventoryHolder(player.getUniqueId(), match.matchId());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                plugin.menuText("shop-menu-title", "&2宝库争夺战术商店", Map.of()));
        holder.setInventory(inventory);
        fill(inventory, player);
        open.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    public void openChat(Player player) {
        PlayerSession session = shopService.session(player);
        player.sendMessage(ChatColor.GOLD + plugin.menuText("shop-menu-header",
                "=== 战术商店（余额 %balance%）===",
                Map.of("balance", String.valueOf(session == null ? 0 : session.tacticalCurrency()))));
        for (ShopItem item : ShopItem.values()) {
            player.sendMessage(ChatColor.YELLOW + plugin.menuText(
                    "shop-chat-entry",
                    "%id% - %item% | %description% | %cost% 战术币 | %status%  /vr shop buy %command%",
                    Map.of(
                            "id", item.id(),
                            "item", item.displayName(plugin),
                            "description", shopService.description(item),
                            "cost", String.valueOf(shopService.cost(item)),
                            "status", shopService.status(session, item),
                            "command", item.id())));
        }
    }

    private void openBedrock(Player player) {
        ArenaMatch match = shopService.runningMatch(player);
        PlayerSession session = shopService.session(player);
        if (match == null || session == null) return;
        List<String> buttons = java.util.Arrays.stream(ShopItem.values())
                .map(item -> plugin.menuText(
                        "shop-bedrock-entry",
                        "%item%\\n价格：%cost% 战术币 · %status%",
                        Map.of(
                                "item", item.displayName(plugin),
                                "cost", String.valueOf(shopService.cost(item)),
                                "status", shopService.status(session, item)))
                        + "\\n" + shopService.description(item))
                .toList();
        UUID uuid = player.getUniqueId();
        String matchId = match.matchId();
        long generation = bedrockGenerations.merge(uuid, 1L, Long::sum);
        boolean sent;
        try {
            sent = bedrockBridge.open(uuid,
                    plugin.menuText("shop-menu-title", "&2宝库争夺战术商店", Map.of()),
                    plugin.menuText("shop-menu-content",
                            "余额：%balance% 战术币\n购买后道具进入背包，右键使用。",
                            Map.of("balance", String.valueOf(session.tacticalCurrency()))),
                    buttons,
                    index -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (bedrockGenerations.getOrDefault(uuid, 0L) != generation) return;
                        bedrockGenerations.remove(uuid, generation);
                        Player current = Bukkit.getPlayer(uuid);
                        ShopItem item = index >= 0 && index < ShopItem.values().length
                                ? ShopItem.values()[index] : null;
                        if (current == null || item == null) return;
                        boolean stillBedrock;
                        try {
                            stillBedrock = bedrockBridge.isBedrock(uuid);
                        } catch (RuntimeException exception) {
                            stillBedrock = false;
                        }
                        if (!stillBedrock) return;
                        ShopService.PurchaseResult result = shopService.purchase(current, item, matchId);
                        shopService.sendFailure(current, item, result);
                        if (shopService.canOpen(current)) openBedrock(current);
                    }));
        } catch (RuntimeException exception) {
            sent = false;
        }
        if (!sent) {
            bedrockGenerations.compute(uuid, (key, current) ->
                    current != null && current == generation ? current + 1L : current);
            openChat(player);
        }
    }

    private void fill(Inventory inventory, Player player) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        PlayerSession session = shopService.session(player);
        for (int index = 0; index < ShopItem.values().length; index++) {
            ShopItem item = ShopItem.values()[index];
            ItemStack icon = new ItemStack(item.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + item.displayName(plugin));
            meta.setLore(shopService.lore(session, item));
            icon.setItemMeta(meta);
            inventory.setItem(SLOTS[index], icon);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.owner().equals(player.getUniqueId())
                || open.get(player.getUniqueId()) != top) return;
        ArenaMatch match = shopService.runningMatch(player);
        if (match == null || !java.util.Objects.equals(
                holder.matchId(), match.matchId())) {
            close(player);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        ShopItem item = itemAt(event.getRawSlot());
        if (item == null) return;
        ShopService.PurchaseResult result = shopService.purchase(player, item, holder.matchId());
        shopService.sendFailure(player, item, result);
        if (shopService.canOpen(player)) fill(event.getView().getTopInventory(), player);
        else player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ShopInventoryHolder) {
            open.remove(event.getPlayer().getUniqueId(), event.getInventory());
        }
    }

    public void close(Player player) {
        if (player == null) return;
        bedrockGenerations.merge(player.getUniqueId(), 1L, Long::sum);
        bedrockBridge.close(player.getUniqueId());
        Inventory inventory = open.remove(player.getUniqueId());
        if (inventory != null && player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
    }

    public void closeAll() {
        for (UUID uuid : List.copyOf(open.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) close(player);
        }
        open.clear();
        for (UUID uuid : List.copyOf(bedrockGenerations.keySet())) bedrockGenerations.merge(uuid, 1L, Long::sum);
        bedrockBridge.closeAll();
    }

    private ShopItem itemAt(int slot) {
        for (int index = 0; index < SLOTS.length; index++) if (SLOTS[index] == slot) return ShopItem.values()[index];
        return null;
    }
}
