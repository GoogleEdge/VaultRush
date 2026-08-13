package com.example.vaultrush.model;

import org.bukkit.Material;

import java.util.Arrays;

public enum ShopItem {
    SPEED("speed", "迅捷", Material.SUGAR),
    JUMP("jump", "跳跃", Material.RABBIT_FOOT),
    FIREBALL("fireball", "位移烈焰弹", Material.FIRE_CHARGE),
    SHIELD("shield", "战术护盾", Material.SHIELD),
    DAMAGE_BOOST("damage-boost", "伤害增益", Material.BLAZE_POWDER),
    SMOKE("smoke", "烟雾干扰", Material.GRAY_DYE);

    private final String id;
    private final String displayName;
    private final Material icon;

    ShopItem(String id, String displayName, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String configPath() { return "shop.items." + id; }

    public static ShopItem fromId(String id) {
        if (id == null) return null;
        return Arrays.stream(values()).filter(item -> item.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }
}
