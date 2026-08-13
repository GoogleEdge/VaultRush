package com.example.vaultrush.model;

import com.example.vaultrush.arena.Team;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerSession {
    private final UUID uniqueId;
    private final Team team;
    private final PlayerSnapshot snapshot;
    private final JobType job;
    private long jobCooldownUntil;
    private long jobPassiveCooldownUntil;
    private int carriedGems;
    private boolean respawnPending;
    private int tacticalCurrency;
    private final Map<ShopItem, Integer> purchases = new EnumMap<>(ShopItem.class);
    private final Map<ShopItem, Long> cooldowns = new EnumMap<>(ShopItem.class);
    private long shieldUntil;
    private long damageBoostUntil;

    public PlayerSession(UUID uniqueId, Team team, PlayerSnapshot snapshot) {
        this(uniqueId, team, snapshot, JobType.ASSAULT);
    }

    public PlayerSession(UUID uniqueId, Team team, PlayerSnapshot snapshot, JobType job) {
        this.uniqueId = uniqueId;
        this.team = team;
        this.snapshot = snapshot;
        this.job = job == null ? JobType.ASSAULT : job;
    }

    public UUID uniqueId() { return uniqueId; }
    public Team team() { return team; }
    public PlayerSnapshot snapshot() { return snapshot; }
    public JobType job() { return job; }
    public long jobCooldownRemaining(long now) {
        return Math.max(0L, jobCooldownUntil - now);
    }
    public void startJobCooldown(long until) {
        jobCooldownUntil = Math.max(jobCooldownUntil, until);
    }
    public boolean jobPassiveReady(long now) {
        return jobPassiveCooldownUntil <= now;
    }
    public void startJobPassiveCooldown(long until) {
        jobPassiveCooldownUntil = Math.max(jobPassiveCooldownUntil, until);
    }
    public int carriedGems() { return carriedGems; }
    public void addCarriedGems(int amount) { carriedGems += Math.max(0, amount); }
    public void setCarriedGems(int amount) { carriedGems = Math.max(0, amount); }
    public boolean respawnPending() { return respawnPending; }
    public void setRespawnPending(boolean value) { respawnPending = value; }
    public int tacticalCurrency() { return tacticalCurrency; }
    public void addTacticalCurrency(int amount) { tacticalCurrency += Math.max(0, amount); }
    public boolean spendTacticalCurrency(int amount) {
        int safe = Math.max(0, amount);
        if (tacticalCurrency < safe) return false;
        tacticalCurrency -= safe;
        return true;
    }
    public int purchases(ShopItem item) { return purchases.getOrDefault(item, 0); }
    public void recordPurchase(ShopItem item, long cooldownUntil) {
        purchases.put(item, purchases(item) + 1);
        cooldowns.put(item, Math.max(0L, cooldownUntil));
    }
    public long cooldownRemaining(ShopItem item, long now) {
        return Math.max(0L, cooldowns.getOrDefault(item, 0L) - now);
    }
    public boolean shieldActive(long now) { return shieldUntil > now; }
    public void activateShield(long until) { shieldUntil = Math.max(shieldUntil, until); }
    public boolean damageBoostActive(long now) { return damageBoostUntil > now; }
    public void activateDamageBoost(long until) { damageBoostUntil = Math.max(damageBoostUntil, until); }
    public void clearTacticalEffects() {
        shieldUntil = 0L;
        damageBoostUntil = 0L;
    }
}
