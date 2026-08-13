package com.example.vaultrush.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import io.papermc.paper.event.block.CompostItemEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.block.VaultChangeStateEvent;
import org.bukkit.event.block.VaultDisplayItemEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerLecternPageChangeEvent;
import com.example.vaultrush.VaultRushPlugin;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import com.example.vaultrush.game.ProtectionPolicy;
import com.example.vaultrush.game.WorldProtectionService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Cake;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.ChiseledBookshelf;
import org.bukkit.block.data.type.Jukebox;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.SculkBloomEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCreatePortalEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class WorldProtectionListener implements Listener {
    private final WorldProtectionService protection;

    public WorldProtectionListener(VaultRushPlugin plugin) {
        this.protection = plugin.worldProtectionService();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent) return;
        if (!protection.record(event.getPlayer(),
                event.getBlockReplacedState()).allowsEvent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        if (!protection.recordAll(event.getPlayer(),
                event.getReplacedBlockStates()).allowsEvent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!protection.record(event.getPlayer(),
                event.getBlock()).allowsEvent()) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !protection.isProtected(block.getWorld())) return;
        if (!isJournaledInteraction(block)) return;
        ProtectionPolicy.Decision decision = protection.decision(
                event.getPlayer(), block.getWorld());
        if (decision != ProtectionPolicy.Decision.ALLOWED) {
            protection.sendDenied(event.getPlayer(), decision);
            event.setCancelled(true);
            return;
        }
        if (!protection.record(event.getPlayer(), block).allowsEvent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompost(CompostItemEvent event) {
        if (protection.isProtected(event.getBlock().getWorld())) {
            event.setWillRaiseLevel(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVaultDisplayItem(VaultDisplayItemEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVaultChangeState(VaultChangeStateEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlowerPot(PlayerFlowerPotManipulateEvent event) {
        cancelProtected(event.getFlowerpot(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShearBlock(PlayerShearBlockEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShearEntity(BlockShearEntityEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        cancelProtected(event.getBed(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInsertLecternBook(PlayerInsertLecternBookEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTakeLecternBook(PlayerTakeLecternBookEvent event) {
        cancelProtected(event.getLectern().getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLecternPageChange(PlayerLecternPageChangeEvent event) {
        cancelProtected(event.getLectern().getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!protection.record(event.getPlayer(),
                event.getHarvestedBlock()).allowsEvent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!protection.record(event.getPlayer(),
                event.getBlock()).allowsEvent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstone(BlockRedstoneEvent event) {
        if (protection.isProtected(event.getBlock().getWorld())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseLoot(BlockDispenseLootEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protection.isProtected(event.getBlock().getWorld())) {
            protection.sendDenied(event.getPlayer(), protection.decision(
                    event.getPlayer(), event.getBlock().getWorld()));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protection.isProtected(event.getBlock().getWorld())) {
            protection.sendDenied(event.getPlayer(), protection.decision(
                    event.getPlayer(), event.getBlock().getWorld()));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (isPlayerOwnedInventory(inventory)) return;
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null
                || !protection.isProtected(location.getWorld())) return;
        if (event.getPlayer() instanceof Player player) {
            protection.sendDenied(player,
                    protection.decision(player, location.getWorld()));
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        Inventory top = event.getView().getTopInventory();
        if (clicked != null && protectedInventory(clicked)) {
            event.setCancelled(true);
            return;
        }
        if (protectedInventory(top)
                && (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (event.getRawSlots().stream()
                .anyMatch(slot -> slot < top.getSize()
                        && protectedInventory(event.getView().getInventory(slot)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (protectedInventory(event.getSource())
                || protectedInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (protectedInventory(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (protection.isProtected(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (protection.isProtected(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluid(FluidLevelChangeEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (protection.isProtected(event.getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeaves(LeavesDecayEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSculkBloom(SculkBloomEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDestroy(BlockDestroyEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (protection.isProtected(event.getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortalCreate(EntityCreatePortalEvent event) {
        if (protection.isProtected(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrewingFuel(BrewingStandFuelEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!protection.isRestoring(event.getBlock().getWorld())) {
            cancelProtected(event.getBlock(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        cancelProtected(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (protection.isProtected(event.getRightClicked().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (protection.isProtected(event.getRightClicked().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (protection.isProtected(event.getItemFrame().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (protection.isProtected(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (protection.isProtected(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (protection.isProtected(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (protection.isProtected(event.getVehicle().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (protection.isProtected(event.getVehicle().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (protection.isProtected(event.getVehicle().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (protection.isProtected(event.getEntity().getWorld())) {
            event.setFire(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (protection.isProtected(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (protection.isProtected(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    private boolean isJournaledInteraction(Block block) {
        org.bukkit.block.data.BlockData data = block.getBlockData();
        return data instanceof NoteBlock
                || data instanceof Openable
                || data instanceof Powerable
                || data instanceof Cake
                || data instanceof Campfire
                || data instanceof ChiseledBookshelf
                || data instanceof Jukebox
                || data instanceof RespawnAnchor
                || block.getType() == org.bukkit.Material.END_PORTAL_FRAME;
    }

    private boolean protectedInventory(Inventory inventory) {
        if (inventory == null || isPlayerOwnedInventory(inventory)) return false;
        Location location = inventory.getLocation();
        World world = location == null ? null : location.getWorld();
        if (world != null && protection.isProtected(world)) return true;
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof org.bukkit.entity.Entity entity) {
            return protection.isProtected(entity.getWorld());
        }
        return false;
    }

    private boolean isPlayerOwnedInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Player;
    }

    private void cancelProtected(Block block,
                                 org.bukkit.event.Cancellable event) {
        if (block != null && protection.isProtected(block.getWorld())) {
            event.setCancelled(true);
        }
    }
}
