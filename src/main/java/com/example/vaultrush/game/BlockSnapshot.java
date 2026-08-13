package com.example.vaultrush.game;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

public final class BlockSnapshot {
    private final BlockKey key;
    private final BlockState state;
    private final ItemStack[] containerContents;

    private BlockSnapshot(BlockKey key, BlockState state,
                          ItemStack[] containerContents) {
        this.key = key;
        this.state = state;
        this.containerContents = cloneItems(containerContents);
    }

    public static BlockSnapshot capture(BlockState original) {
        ItemStack[] contents = original instanceof Container container
                ? container.getSnapshotInventory().getContents() : null;
        return new BlockSnapshot(BlockKey.of(original), original.copy(), contents);
    }

    public BlockKey key() { return key; }

    public boolean restore() {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) return false;
        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        block.setType(state.getType(), false);
        block.setBlockData(state.getBlockData().clone(), false);
        BlockState copy = state.copy(block.getLocation());
        boolean restored = copy.update(true, false);
        if (containerContents == null) return restored;
        BlockState target = block.getState();
        if (!(target instanceof Container container)) return false;
        container.getInventory().setContents(cloneItems(containerContents));
        return container.update(true, false) && restored;
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }
}
