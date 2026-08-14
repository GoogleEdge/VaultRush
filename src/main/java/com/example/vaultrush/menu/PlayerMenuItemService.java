package com.example.vaultrush.menu;

import com.example.vaultrush.VaultRushPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns the persistent physical item used to open the player menu. */
public final class PlayerMenuItemService implements Listener {
    public enum EnsureResult {
        PRESENT,
        ADDED,
        FULL
    }

    public enum ActivationResult {
        NONE,
        OPENED,
        INVALID
    }

    private static final int PREFERRED_SLOT = 8;
    private static final long FULL_NOTICE_COOLDOWN_MILLIS = 5000L;

    private final VaultRushPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey tokenKey;
    private final Map<UUID, Long> fullNoticeTimes = new HashMap<>();

    public PlayerMenuItemService(VaultRushPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "menu_item");
        this.ownerKey = new NamespacedKey(plugin, "menu_item_owner");
        this.tokenKey = new NamespacedKey(plugin, "menu_item_token");
    }

    /** Reconciles one player's item without moving an existing valid item. */
    public EnsureResult ensure(Player player) {
        if (player == null || !player.isOnline()) return EnsureResult.FULL;

        String token = playerToken(player);
        ItemStack[] storage = player.getInventory().getStorageContents();
        boolean storageChanged = false;
        int validSlot = -1;
        for (int slot : MenuItemPlacement.scanOrder(storage.length, PREFERRED_SLOT)) {
            ItemStack stack = storage[slot];
            if (!isMarked(stack)) continue;
            if (validSlot < 0 && isValid(stack, player.getUniqueId(), token)) {
                validSlot = slot;
                ItemStack desired = createItem(player, token);
                if (!sameItem(stack, desired)) {
                    storage[slot] = desired;
                    storageChanged = true;
                }
            } else {
                // Remove duplicate, malformed, stale, or foreign plugin markers.
                storage[slot] = null;
                storageChanged = true;
            }
        }

        boolean validNonStorage = hasValidNonStorageItem(
                player, player.getUniqueId(), token);
        if (validSlot >= 0) {
            // A valid item already exists in ordinary storage; clear stray
            // copies outside storage and retain the storage item in place.
            boolean nonStorageChanged = clearMarkedNonStorageItems(
                    player, player.getUniqueId(), token, false);
            writeInventory(player, storage, storageChanged, nonStorageChanged);
            return EnsureResult.PRESENT;
        }

        int emptySlot = MenuItemPlacement.findEmptySlot(storage, PREFERRED_SLOT);
        if (emptySlot < 0) {
            // Do not destroy a valid externally-moved item merely because the
            // ordinary storage is full. Invalid/foreign markers are still removed.
            boolean nonStorageChanged = clearMarkedNonStorageItems(
                    player, player.getUniqueId(), token, validNonStorage);
            writeInventory(player, storage, storageChanged, nonStorageChanged);
            notifyFull(player);
            return EnsureResult.FULL;
        }
        boolean nonStorageChanged = clearMarkedNonStorageItems(
                player, player.getUniqueId(), token, false);
        storage[emptySlot] = createItem(player, token);
        writeInventory(player, storage, true, nonStorageChanged);
        return EnsureResult.ADDED;
    }

    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) ensure(player);
    }

    public void forgetPlayer(UUID uuid) {
        if (uuid != null) fullNoticeTimes.remove(uuid);
    }

    /**
     * Handles a held item only when it carries this plugin's marker. The caller
     * should cancel the interaction for every result except NONE.
     */
    public ActivationResult activate(Player player, EquipmentSlot hand) {
        if (player == null || hand == null) return ActivationResult.NONE;
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!isMarked(stack)) return ActivationResult.NONE;
        if (!isValid(stack, player.getUniqueId(), playerToken(player))) {
            clearHand(player, hand);
            plugin.send(player, "menu-item-invalid", Map.of());
            return ActivationResult.INVALID;
        }
        return open(player);
    }

    private ActivationResult open(Player player) {
        plugin.menuService().open(player);
        return ActivationResult.OPENED;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean marked = isMarked(event.getCurrentItem())
                || isMarked(event.getCursor())
                || hotbarSourceIsMarked(event)
                || offhandSourceIsMarked(event);
        if (!marked
                && event.getAction()
                == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR
                && cursorCouldCollectMarked(event.getView(), event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (marked && !markerCanStayInPlayerStorage(event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        boolean markerInDrag = isMarked(event.getOldCursor())
                || isMarked(event.getCursor())
                || event.getNewItems().values().stream().anyMatch(this::isMarked);
        if (markerInDrag) {
            if (!dragTouchesOnlyPlayerStorage(event)
                    || dragWouldTouchMarkedSlot(event)
                    || event.getNewItems().values().stream()
                    .filter(this::isMarked)
                    .count() > 1) {
                event.setCancelled(true);
            }
            return;
        }
        if (dragWouldTouchMarkedSlot(event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getItemDrop() != null
                && isMarked(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isMarked(event.getMainHandItem()) || isMarked(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isMarked(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (event.getItem() != null
                && isMarked(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (event.getItem() != null
                && isMarked(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (isMarked(event.getEntity().getItemStack())
                || isMarked(event.getTarget().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (isMarked(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseLoot(BlockDispenseLootEvent event) {
        if (event.getDispensedLoot() != null
                && event.getDispensedLoot().stream().anyMatch(this::isMarked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isMarked(event.getItemInHand())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isMarked(event.getPlayerItem()) || isMarked(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (isMarked(event.getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isMarked(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent event) {
        if (isMarked(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(PlayerItemBreakEvent event) {
        if (isMarked(event.getBrokenItem())) scheduleEnsure(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMarked);
        event.getItemsToKeep().removeIf(this::isMarked);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleEnsure(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forgetPlayer(event.getPlayer().getUniqueId());
    }

    private void scheduleEnsure(Player player) {
        if (player == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) ensure(player);
        });
    }

    private boolean markerCanStayInPlayerStorage(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return false;
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_CURSOR
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_CURSOR
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.CLONE_STACK
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                || event.getClick().isCreativeAction()
                || offhandSourceIsMarked(event)) return false;
        if (!(event.getClickedInventory() instanceof PlayerInventory inventory)
                || inventory != player.getInventory()
                || event.getSlot() < 0
                || event.getSlot() >= player.getInventory().getStorageContents().length) {
            return false;
        }
        return !isMarked(event.getCurrentItem())
                || event.getCursor() == null
                || event.getCursor().getType().isAir();
    }

    private boolean dragWouldTouchMarkedSlot(InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (isMarked(event.getView().getItem(rawSlot))) return true;
        }
        return false;
    }

    private boolean dragTouchesOnlyPlayerStorage(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return false;
        int storageSize = player.getInventory().getStorageContents().length;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) return false;
            int playerSlot = rawSlot - topSize;
            if (playerSlot < 0 || playerSlot >= storageSize) return false;
        }
        return true;
    }

    private boolean hotbarSourceIsMarked(InventoryClickEvent event) {
        if (event.getClick() != org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            return false;
        }
        int button = event.getHotbarButton();
        if (!(event.getWhoClicked() instanceof Player player)
                || button < 0 || button > 8) return false;
        return isMarked(player.getInventory().getItem(button));
    }

    private boolean offhandSourceIsMarked(InventoryClickEvent event) {
        if (event.getClick() != org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                || !(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        return isMarked(player.getInventory().getItemInMainHand())
                || isMarked(player.getInventory().getItemInOffHand());
    }

    private boolean cursorCouldCollectMarked(InventoryView view, ItemStack cursor) {
        if (cursor == null || cursor.getType().isAir()) return false;
        return containsMarkedType(view.getTopInventory(), cursor.getType())
                || containsMarkedType(view.getBottomInventory(), cursor.getType());
    }

    private boolean containsMarkedType(Inventory inventory, Material material) {
        if (inventory == null || material == null) return false;
        for (ItemStack stack : inventory.getContents()) {
            if (isMarked(stack) && stack.getType() == material) return true;
        }
        return false;
    }

    private String playerToken(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        String token = data.get(tokenKey, PersistentDataType.STRING);
        if (token == null || token.isBlank()) {
            token = UUID.randomUUID().toString();
            data.set(tokenKey, PersistentDataType.STRING, token);
        }
        return token;
    }

    private ItemStack createItem(Player player, String token) {
        Material material = plugin.menuItemMaterial();
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(plugin.menuText("menu-item-name", "&b打开菜单", Map.of()));
        String lore = plugin.menuText(
                "menu-item-lore", "&7右键打开宝库争夺菜单。", Map.of());
        meta.setLore(List.of(lore.split("\\R", -1)));
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        data.set(ownerKey, PersistentDataType.STRING,
                player.getUniqueId().toString());
        data.set(tokenKey, PersistentDataType.STRING, token);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isMarked(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(markerKey, PersistentDataType.BYTE);
    }

    private boolean isMarkedMenuItem(ItemStack stack) {
        if (!isMarked(stack)) return false;
        Byte marker = stack.getItemMeta().getPersistentDataContainer()
                .get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isValid(ItemStack stack, UUID owner, String token) {
        if (!isMarkedMenuItem(stack) || stack.getAmount() != 1
                || owner == null || token == null) return false;
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        return owner.toString().equals(data.get(ownerKey, PersistentDataType.STRING))
                && token.equals(data.get(tokenKey, PersistentDataType.STRING));
    }

    private boolean hasValidNonStorageItem(Player player, UUID owner, String token) {
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (isValid(stack, owner, token)) return true;
        }
        for (ItemStack stack : player.getInventory().getExtraContents()) {
            if (isValid(stack, owner, token)) return true;
        }
        return isValid(player.getInventory().getItemInOffHand(), owner, token);
    }

    private boolean clearMarkedNonStorageItems(Player player, UUID owner,
                                            String token, boolean preserveValid) {
        boolean changed = false;
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean armorChanged = false;
        boolean preserved = false;
        for (int index = 0; index < armor.length; index++) {
            ItemStack stack = armor[index];
            if (!isMarked(stack)) continue;
            boolean valid = isValid(stack, owner, token);
            if (preserveValid && valid && !preserved) {
                preserved = true;
                continue;
            }
            armor[index] = null;
            armorChanged = true;
        }
        if (armorChanged) {
            player.getInventory().setArmorContents(armor);
            changed = true;
        }

        ItemStack[] extra = player.getInventory().getExtraContents();
        boolean extraChanged = false;
        for (int index = 0; index < extra.length; index++) {
            ItemStack stack = extra[index];
            if (!isMarked(stack)) continue;
            boolean valid = isValid(stack, owner, token);
            if (preserveValid && valid && !preserved) {
                preserved = true;
                continue;
            }
            extra[index] = null;
            extraChanged = true;
        }
        if (extraChanged) {
            player.getInventory().setExtraContents(extra);
            changed = true;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isMarked(offhand)) {
            boolean valid = isValid(offhand, owner, token);
            if (!(preserveValid && valid && !preserved)) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                changed = true;
            }
        }
        return changed;
    }

    private void writeInventory(Player player, ItemStack[] storage,
                                boolean storageChanged, boolean otherChanged) {
        if (storageChanged) player.getInventory().setStorageContents(storage);
        if (storageChanged || otherChanged) player.updateInventory();
    }

    private boolean sameItem(ItemStack first, ItemStack second) {
        return first == second || first != null && first.equals(second);
    }

    private void clearHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    private void notifyFull(Player player) {
        long now = System.currentTimeMillis();
        long previous = fullNoticeTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < FULL_NOTICE_COOLDOWN_MILLIS) return;
        fullNoticeTimes.put(player.getUniqueId(), now);
        plugin.send(player, "menu-item-inventory-full", Map.of());
    }
}
