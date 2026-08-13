package com.example.vaultrush.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlockChangeJournal<T> {
    public enum RecordResult { RECORDED, ALREADY_RECORDED, FULL }

    private final int maximumEntries;
    private final Map<BlockKey, T> entries = new LinkedHashMap<>();

    public BlockChangeJournal(int maximumEntries) {
        this.maximumEntries = Math.max(1, maximumEntries);
    }

    public RecordResult record(BlockKey key, T value) {
        if (entries.containsKey(key)) return RecordResult.ALREADY_RECORDED;
        if (entries.size() >= maximumEntries) return RecordResult.FULL;
        entries.put(key, value);
        return RecordResult.RECORDED;
    }

    public boolean contains(BlockKey key) { return entries.containsKey(key); }
    public int size() { return entries.size(); }
    public boolean isEmpty() { return entries.isEmpty(); }

    public List<T> valuesInReverseOrder() {
        List<T> values = new ArrayList<>(entries.values());
        Collections.reverse(values);
        return values;
    }

    public void clear() { entries.clear(); }
}
