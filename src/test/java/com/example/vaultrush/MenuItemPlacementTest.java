package com.example.vaultrush;

import com.example.vaultrush.menu.MenuItemPlacement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuItemPlacementTest {
    @Test
    void preferredHotbarSlotIsFirstWhenEmpty() {
        Object[] storage = new Object[36];

        assertEquals(8, MenuItemPlacement.findEmptySlot(storage, 8));
        assertEquals(8, MenuItemPlacement.scanOrder(36, 8).get(0));
    }

    @Test
    void occupiedPreferredSlotIsNeverOverwritten() {
        Object[] storage = new Object[36];
        storage[8] = "player-item";

        assertEquals(0, MenuItemPlacement.findEmptySlot(storage, 8));
        assertEquals("player-item", storage[8]);
    }

    @Test
    void fullStorageReturnsNoSlot() {
        Object[] storage = new Object[36];
        java.util.Arrays.fill(storage, "player-item");

        assertEquals(-1, MenuItemPlacement.findEmptySlot(storage, 8));
        assertTrue(MenuItemPlacement.scanOrder(36, 8).containsAll(
                java.util.stream.IntStream.range(0, 36).boxed().toList()));
    }

    @Test
    void invalidPreferredSlotStillScansEveryStorageSlot() {
        Object[] storage = new Object[3];

        assertEquals(List.of(0, 1, 2), MenuItemPlacement.scanOrder(3, -1));
        assertEquals(0, MenuItemPlacement.findEmptySlot(storage, 99));
    }
}
