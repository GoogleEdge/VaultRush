package com.example.vaultrush.model;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PlayerSnapshot {
    private final Location location;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final GameMode gameMode;
    private final double health;
    private final int food;
    private final float saturation;
    private final float exp;
    private final int level;
    private final int totalExperience;
    private final List<PotionEffect> effects;
    private final boolean allowFlight;
    private final boolean flying;
    private final Scoreboard scoreboard;

    private PlayerSnapshot(Location location, ItemStack[] contents, ItemStack[] armor, GameMode gameMode,
                           double health, int food, float saturation, float exp, int level,
                           int totalExperience, Collection<PotionEffect> effects, boolean allowFlight,
                           boolean flying, Scoreboard scoreboard) {
        this.location = location;
        this.contents = cloneItems(contents);
        this.armor = cloneItems(armor);
        this.gameMode = gameMode;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.exp = exp;
        this.level = level;
        this.totalExperience = totalExperience;
        this.effects = new ArrayList<>(effects);
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.scoreboard = scoreboard;
    }

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(
                player.getLocation().clone(),
                player.getInventory().getContents(),
                player.getInventory().getArmorContents(),
                player.getGameMode(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getActivePotionEffects(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getScoreboard()
        );
    }

    public void restore(Player player) {
        player.getInventory().setContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.setGameMode(gameMode);
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setExp(exp);
        player.setLevel(level);
        player.setTotalExperience(totalExperience);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        for (PotionEffect effect : effects) player.addPotionEffect(effect);
        player.setAllowFlight(allowFlight);
        if (allowFlight) player.setFlying(flying);
        else player.setFlying(false);
        player.setScoreboard(scoreboard);
        if (player.getMaxHealth() > 0) player.setHealth(Math.min(health, player.getMaxHealth()));
        if (location.getWorld() != null && !location.getWorld().equals(player.getWorld())) player.teleport(location);
        else player.teleport(location);
        player.updateInventory();
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }
}
