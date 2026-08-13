package com.example.vaultrush.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class JobInventoryHolder implements InventoryHolder {
    private final UUID owner;
    private final String arenaId;
    private final long generation;
    private Inventory inventory;

    public JobInventoryHolder(UUID owner, String arenaId, long generation) {
        this.owner = owner;
        this.arenaId = arenaId;
        this.generation = generation;
    }

    public UUID owner() { return owner; }
    public String arenaId() { return arenaId; }
    public long generation() { return generation; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
