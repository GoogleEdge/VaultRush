package com.example.vaultrush;

import com.example.vaultrush.game.ShopService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopAccessTest {
    @Test
    void purchaseResultContainsAllAccessStates() {
        assertEquals(ShopService.PurchaseResult.DISABLED, ShopService.PurchaseResult.valueOf("DISABLED"));
        assertEquals(ShopService.PurchaseResult.NOT_RUNNING, ShopService.PurchaseResult.valueOf("NOT_RUNNING"));
        assertEquals(ShopService.PurchaseResult.NOT_AT_DEPOSIT, ShopService.PurchaseResult.valueOf("NOT_AT_DEPOSIT"));
    }
}
