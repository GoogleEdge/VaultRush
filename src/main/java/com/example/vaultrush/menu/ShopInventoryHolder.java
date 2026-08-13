package com.example.vaultrush.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class ShopInventoryHolder implements InventoryHolder {
    private final UUID owner;
    private final String matchId;
    private Inventory inventory;

    public ShopInventoryHolder(UUID owner, String matchId) {
        this.owner = owner;
        this.matchId = matchId;
    }

    public UUID owner() { return owner; }
    public String matchId() { return matchId; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
