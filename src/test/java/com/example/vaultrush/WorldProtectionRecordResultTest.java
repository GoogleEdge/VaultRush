package com.example.vaultrush;

import com.example.vaultrush.game.WorldProtectionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProtectionRecordResultTest {
    @Test
    void unprotectedWorldsPassThroughWithoutRecording() {
        assertTrue(WorldProtectionService.RecordResult.UNPROTECTED
                .allowsEvent());
        assertTrue(WorldProtectionService.RecordResult.ALLOWED
                .allowsEvent());
    }

    @Test
    void deniedAndFullJournalsCancelTheMutation() {
        assertFalse(WorldProtectionService.RecordResult.DENIED
                .allowsEvent());
        assertFalse(WorldProtectionService.RecordResult.FULL
                .allowsEvent());
    }
}
