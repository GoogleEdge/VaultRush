package com.example.vaultrush;

import com.example.vaultrush.arena.Team;
import com.example.vaultrush.model.PlayerSession;
import com.example.vaultrush.model.ShopItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopStateTest {
    @Test
    void itemIdsRemainStable() {
        assertEquals(ShopItem.SPEED, ShopItem.fromId("speed"));
        assertEquals(ShopItem.DAMAGE_BOOST, ShopItem.fromId("damage-boost"));
        assertNull(ShopItem.fromId("unknown"));
    }

    @Test
    void balanceNeverGoesNegative() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), Team.RED, null);
        session.addTacticalCurrency(5);
        assertFalse(session.spendTacticalCurrency(6));
        assertEquals(5, session.tacticalCurrency());
        assertTrue(session.spendTacticalCurrency(5));
        assertEquals(0, session.tacticalCurrency());
    }

    @Test
    void purchasesCooldownsAndEffectsAreMatchLocal() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), Team.BLUE, null);
        long now = 10_000L;
        session.recordPurchase(ShopItem.FIREBALL, now + 12_000L);
        assertEquals(1, session.purchases(ShopItem.FIREBALL));
        assertEquals(12_000L, session.cooldownRemaining(ShopItem.FIREBALL, now));
        session.activateShield(now + 8_000L);
        session.activateDamageBoost(now + 8_000L);
        assertTrue(session.shieldActive(now));
        assertTrue(session.damageBoostActive(now));
        session.clearTacticalEffects();
        assertFalse(session.shieldActive(now));
        assertFalse(session.damageBoostActive(now));
    }
}
