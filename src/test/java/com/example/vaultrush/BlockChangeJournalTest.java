package com.example.vaultrush;

import com.example.vaultrush.game.BlockChangeJournal;
import com.example.vaultrush.game.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockChangeJournalTest {
    @Test
    void firstWriteWinsAndRestoreOrderIsReversed() {
        BlockChangeJournal<String> journal = new BlockChangeJournal<>(3);
        UUID world = UUID.randomUUID();
        BlockKey first = new BlockKey(world, 1, 2, 3);
        BlockKey second = new BlockKey(world, 4, 5, 6);

        assertEquals(BlockChangeJournal.RecordResult.RECORDED,
                journal.record(first, "original-first"));
        assertEquals(BlockChangeJournal.RecordResult.ALREADY_RECORDED,
                journal.record(first, "replacement"));
        assertEquals(BlockChangeJournal.RecordResult.RECORDED,
                journal.record(second, "original-second"));
        assertEquals(List.of("original-second", "original-first"),
                journal.valuesInReverseOrder());
    }

    @Test
    void fullJournalRejectsNewCoordinatesAndCanBeCleared() {
        BlockChangeJournal<String> journal = new BlockChangeJournal<>(1);
        UUID world = UUID.randomUUID();
        journal.record(new BlockKey(world, 0, 0, 0), "first");
        assertEquals(BlockChangeJournal.RecordResult.FULL,
                journal.record(new BlockKey(world, 1, 0, 0), "second"));
        assertEquals(1, journal.size());
        journal.clear();
        assertTrue(journal.isEmpty());
    }

    @Test
    void worldIdIsPartOfBlockIdentity() {
        BlockKey first = new BlockKey(UUID.randomUUID(), 7, 8, 9);
        BlockKey second = new BlockKey(UUID.randomUUID(), 7, 8, 9);
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }
}
