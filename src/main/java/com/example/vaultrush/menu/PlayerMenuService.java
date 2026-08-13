package com.example.vaultrush.menu;

import com.example.vaultrush.VaultRushPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerMenuService implements Listener {
    private static final int MENU_SIZE = 27;
    private static final int[] ACTION_SLOTS = {10, 11, 12, 14, 15};

    private final VaultRushPlugin plugin;
    private final BedrockMenuBridge bedrockBridge;
    private final NamespacedKey menuActionKey;
    private final Map<UUID, Long> generations = new HashMap<>();
    private final Map<UUID, Inventory> open = new HashMap<>();

    public PlayerMenuService(VaultRushPlugin plugin, BedrockMenuBridge bedrockBridge) {
        this.plugin = plugin;
        this.bedrockBridge = bedrockBridge;
        this.menuActionKey = new NamespacedKey(plugin, "menu_action");
    }

    public void open(Player player) {
        if (!canOpen(player)) return;
        if (plugin.jobSelectionService() != null) {
            plugin.jobSelectionService().close(player);
        }
        if (plugin.shopInventoryService() != null) {
            plugin.shopInventoryService().close(player);
        }

        UUID uuid = player.getUniqueId();
        close(player);
        long generation = nextGeneration(uuid);
        String title = plugin.menuText("menu-title", "&6宝库争夺菜单", Map.of());
        String content = plugin.menuText(
                "menu-content",
                "&f请选择一个操作：\n&7Java 玩家使用箱子菜单，Bedrock 玩家使用中文表单。",
                Map.of());
        List<String> buttons = Arrays.stream(MenuAction.values())
                .map(action -> menuLabel(action) + "\n" + menuDescription(action))
                .toList();

        if (isBedrock(uuid)) {
            final long bedrockGeneration = generation;
            boolean sent = false;
            try {
                sent = bedrockBridge.open(uuid, title, content, buttons,
                        index -> scheduleBedrockSelection(uuid, bedrockGeneration, index));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("无法发送 Bedrock 主菜单，将使用箱子菜单："
                        + exception.getClass().getSimpleName());
            }
            if (sent) return;

            // A failed form must not leave an old callback able to dispatch.
            invalidate(uuid, generation);
            generation = nextGeneration(uuid);
        }

        openJavaInventory(player, generation, title);
    }

    /** Opens the Java inventory menu directly; retained as a small API for callers/tests. */
    public void openJava(Player player) {
        if (!canOpen(player)) return;
        if (plugin.jobSelectionService() != null) {
            plugin.jobSelectionService().close(player);
        }
        if (plugin.shopInventoryService() != null) {
            plugin.shopInventoryService().close(player);
        }
        UUID uuid = player.getUniqueId();
        close(player);
        openJavaInventory(player, nextGeneration(uuid),
                plugin.menuText("menu-title", "&6宝库争夺菜单", Map.of()));
    }

    private void openJavaInventory(Player player, long generation, String title) {
        MainMenuInventoryHolder holder =
                new MainMenuInventoryHolder(player.getUniqueId(), generation);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, title);
        holder.setInventory(inventory);
        fill(inventory);
        open.put(player.getUniqueId(), inventory);
        try {
            player.openInventory(inventory);
        } catch (RuntimeException exception) {
            open.remove(player.getUniqueId(), inventory);
            invalidate(player.getUniqueId(), generation);
            plugin.getLogger().warning("无法打开 Java 主菜单："
                    + exception.getClass().getSimpleName());
        }
    }

    private void fill(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        for (int index = 0; index < MenuAction.values().length; index++) {
            MenuAction action = MenuAction.values()[index];
            ItemStack icon = new ItemStack(iconMaterial(action));
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(menuLabel(action));
            List<String> lore = new ArrayList<>(Arrays.asList(
                    menuDescription(action).split("\\R", -1)));
            lore.add(ChatColor.DARK_GRAY + "点击执行 /vr " + action.command());
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    menuActionKey, PersistentDataType.STRING, action.command());
            icon.setItemMeta(meta);
            inventory.setItem(ACTION_SLOTS[index], icon);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MainMenuInventoryHolder holder)) return;

        // Protect both the menu and the player's inventory from menu interactions.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!holder.owner().equals(uuid)
                || open.get(uuid) != top
                || generations.getOrDefault(uuid, 0L) != holder.generation()
                || !player.isOnline()
                || !player.hasPermission("vaultrush.play")) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;
        MenuAction action = actionAtSlot(rawSlot);
        ItemStack clicked = top.getItem(rawSlot);
        if (action == null || clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String command = meta.getPersistentDataContainer()
                .get(menuActionKey, PersistentDataType.STRING);
        if (command == null || !action.command().equals(command)) return;
        MenuAction encodedAction = MenuAction.fromCommand(command);
        if (encodedAction != action) return;

        close(player);
        player.performCommand("vr " + action.command());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MainMenuInventoryHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof MainMenuInventoryHolder holder)) return;
        UUID uuid = event.getPlayer().getUniqueId();
        if (!open.remove(uuid, inventory)) return;
        generations.compute(uuid, (key, current) ->
                current != null && current == holder.generation()
                        ? current + 1L : current);
    }

    public void close(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        generations.merge(uuid, 1L, Long::sum);
        bedrockBridge.close(uuid);
        Inventory inventory = open.remove(uuid);
        if (inventory != null
                && player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
    }

    public void closeAll() {
        Set<UUID> uuids = new HashSet<>(generations.keySet());
        uuids.addAll(open.keySet());
        for (UUID uuid : uuids) {
            generations.merge(uuid, 1L, Long::sum);
        }
        for (Map.Entry<UUID, Inventory> entry : new HashSet<>(open.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null
                    && player.getOpenInventory().getTopInventory() == entry.getValue()) {
                player.closeInventory();
            }
        }
        open.clear();
        bedrockBridge.closeAll();
    }

    private void scheduleBedrockSelection(UUID uuid, long generation, int index) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (generations.getOrDefault(uuid, 0L) != generation) return;
            generations.remove(uuid, generation);
            bedrockBridge.close(uuid);
            dispatch(uuid, index);
        });
    }

    private void dispatch(UUID uuid, int index) {
        MenuAction action = MenuAction.fromIndex(index);
        Player player = Bukkit.getPlayer(uuid);
        if (action == null || !canOpen(player)) return;
        player.performCommand("vr " + action.command());
    }

    private boolean canOpen(Player player) {
        return player != null && player.isOnline()
                && player.hasPermission("vaultrush.play");
    }

    private boolean isBedrock(UUID uuid) {
        try {
            return bedrockBridge.isBedrock(uuid);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private long nextGeneration(UUID uuid) {
        return generations.merge(uuid, 1L, Long::sum);
    }

    private void invalidate(UUID uuid, long generation) {
        generations.compute(uuid, (key, current) ->
                current != null && current == generation ? current + 1L : current);
    }

    private String menuLabel(MenuAction action) {
        return plugin.menuText(action.labelKey(), defaultLabel(action), Map.of());
    }

    private String menuDescription(MenuAction action) {
        return plugin.menuText(action.descriptionKey(), action.defaultDescription(), Map.of());
    }

    private String defaultLabel(MenuAction action) {
        return switch (action) {
            case JOIN -> "&a加入游戏";
            case LEAVE -> "&e离开游戏";
            case LIST -> "&b查看竞技场";
            case STATUS -> "&d查看状态";
            case SHOP -> "&6战术商店";
        };
    }

    private Material iconMaterial(MenuAction action) {
        return switch (action) {
            case JOIN -> Material.EMERALD_BLOCK;
            case LEAVE -> Material.BARRIER;
            case LIST -> Material.BOOK;
            case STATUS -> Material.COMPASS;
            case SHOP -> Material.CHEST;
        };
    }

    private MenuAction actionAtSlot(int slot) {
        for (int index = 0; index < ACTION_SLOTS.length; index++) {
            if (ACTION_SLOTS[index] == slot) return MenuAction.values()[index];
        }
        return null;
    }
}
