package com.example.vaultrush.game;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.UUID;

public record BlockKey(UUID worldId, int x, int y, int z) {
    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(BlockState state) {
        return new BlockKey(state.getWorld().getUID(),
                state.getX(), state.getY(), state.getZ());
    }
}
