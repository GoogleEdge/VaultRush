package com.example.vaultrush.arena;

import org.bukkit.ChatColor;

public enum Team {
    RED("红队", ChatColor.RED),
    BLUE("蓝队", ChatColor.BLUE);

    private final String displayName;
    private final ChatColor color;

    Team(String displayName, ChatColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String displayName(com.example.vaultrush.VaultRushPlugin plugin) {
        return plugin.menuText("team-" + name().toLowerCase(java.util.Locale.ROOT),
                displayName, java.util.Map.of());
    }

    public ChatColor color() {
        return color;
    }

    public String coloredName() {
        return color + displayName;
    }

    public String coloredName(com.example.vaultrush.VaultRushPlugin plugin) {
        return color + displayName(plugin);
    }
}
