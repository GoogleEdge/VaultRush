package com.example.vaultrush.model;

import org.bukkit.Material;

import java.util.Arrays;

public enum JobType {
    ASSAULT("assault", "突击手", "冲锋", Material.IRON_SWORD),
    SCOUT("scout", "侦察者", "侦察脉冲", Material.COMPASS),
    GUARDIAN("guardian", "守护者", "坚守护盾", Material.SHIELD),
    ENGINEER("engineer", "工程师", "快速施工", Material.IRON_PICKAXE),
    ILLUSIONIST("illusionist", "幻术师", "幻影", Material.ENDER_EYE);

    private final String id;
    private final String displayName;
    private final String abilityName;
    private final Material icon;

    JobType(String id, String displayName, String abilityName, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.abilityName = abilityName;
        this.icon = icon;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String abilityName() { return abilityName; }
    public Material icon() { return icon; }
    public String configPath() { return "jobs." + id; }

    public static JobType fromId(String id) {
        if (id == null) return null;
        return Arrays.stream(values())
                .filter(job -> job.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public static JobType fromIndex(int index) {
        JobType[] values = values();
        return index < 0 || index >= values.length ? null : values[index];
    }
}
