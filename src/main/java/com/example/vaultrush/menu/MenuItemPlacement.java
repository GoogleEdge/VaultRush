package com.example.vaultrush.menu;

import java.util.ArrayList;
import java.util.List;

/** Pure slot-selection helpers for the persistent menu item. */
public final class MenuItemPlacement {
    private MenuItemPlacement() {
    }

    public static List<Integer> scanOrder(int size, int preferredSlot) {
        List<Integer> slots = new ArrayList<>();
        if (preferredSlot >= 0 && preferredSlot < size) slots.add(preferredSlot);
        for (int slot = 0; slot < size; slot++) {
            if (slot != preferredSlot) slots.add(slot);
        }
        return slots;
    }

    public static int findEmptySlot(Object[] storage, int preferredSlot) {
        if (storage == null) return -1;
        for (int slot : scanOrder(storage.length, preferredSlot)) {
            if (storage[slot] == null) return slot;
        }
        return -1;
    }
}
