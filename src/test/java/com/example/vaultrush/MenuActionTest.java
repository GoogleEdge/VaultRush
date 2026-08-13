package com.example.vaultrush;

import com.example.vaultrush.menu.MenuAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MenuActionTest {
    @Test
    void menuOrderAndCommandsAreStable() {
        assertEquals(MenuAction.JOIN, MenuAction.fromIndex(0));
        assertEquals(MenuAction.LEAVE, MenuAction.fromIndex(1));
        assertEquals(MenuAction.LIST, MenuAction.fromIndex(2));
        assertEquals(MenuAction.STATUS, MenuAction.fromIndex(3));
        assertEquals(MenuAction.SHOP, MenuAction.fromIndex(4));
        assertEquals("join", MenuAction.JOIN.command());
        assertEquals("status", MenuAction.STATUS.command());
        assertEquals("menu-shop-description", MenuAction.SHOP.descriptionKey());
        assertFalse(MenuAction.SHOP.defaultDescription().isBlank());
    }

    @Test
    void invalidButtonIndexesAreIgnored() {
        assertNull(MenuAction.fromIndex(-1));
        assertNull(MenuAction.fromIndex(5));
    }

    @Test
    void inventoryCommandsUseTheSameAllowList() {
        assertEquals(MenuAction.JOIN, MenuAction.fromCommand("JOIN"));
        assertEquals(MenuAction.SHOP, MenuAction.fromCommand("shop"));
        assertNull(MenuAction.fromCommand("admin"));
        assertNull(MenuAction.fromCommand(null));
    }
}
