package com.example.vaultrush.menu;

public enum MenuAction {
    JOIN("join", "menu-join", "menu-join-description", "加入一个宝库争夺竞技场队列。"),
    LEAVE("leave", "menu-leave", "menu-leave-description", "离开当前队列或比赛。"),
    LIST("list", "menu-list", "menu-list-description", "查看竞技场状态、队列人数和比分。"),
    STATUS("status", "menu-status", "menu-status-description", "查看当前比赛状态和比分。"),
    SHOP("shop", "menu-shop", "menu-shop-description", "在己方交付点打开战术商店。");

    private final String command;
    private final String labelKey;
    private final String descriptionKey;
    private final String defaultDescription;

    MenuAction(String command, String labelKey, String descriptionKey, String defaultDescription) {
        this.command = command;
        this.labelKey = labelKey;
        this.descriptionKey = descriptionKey;
        this.defaultDescription = defaultDescription;
    }

    public String command() { return command; }
    public String labelKey() { return labelKey; }
    public String descriptionKey() { return descriptionKey; }
    public String defaultDescription() { return defaultDescription; }

    public static MenuAction fromIndex(int index) {
        MenuAction[] values = values();
        return index < 0 || index >= values.length ? null : values[index];
    }

    public static MenuAction fromCommand(String command) {
        if (command == null) return null;
        for (MenuAction action : values()) {
            if (action.command.equalsIgnoreCase(command)) return action;
        }
        return null;
    }
}
