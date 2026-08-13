package com.example.vaultrush.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class MainMenuInventoryHolder implements InventoryHolder {
    private final UUID owner;
    private final long generation;
    private Inventory inventory;

    public MainMenuInventoryHolder(UUID owner, long generation) {
        this.owner = owner;
        this.generation = generation;
    }

    public UUID owner() {
        return owner;
    }

    public long generation() {
        return generation;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
