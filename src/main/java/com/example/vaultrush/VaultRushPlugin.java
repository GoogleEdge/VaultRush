package com.example.vaultrush;

import com.example.vaultrush.arena.ArenaManager;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.command.VaultRushCommand;
import com.example.vaultrush.game.CleanupService;
import com.example.vaultrush.game.GemService;
import com.example.vaultrush.game.JobService;
import com.example.vaultrush.game.MatchController;
import com.example.vaultrush.game.QueueService;
import com.example.vaultrush.game.ScoreboardService;
import com.example.vaultrush.game.ShopService;
import com.example.vaultrush.game.VaultService;
import com.example.vaultrush.game.WorldProtectionService;
import com.example.vaultrush.listener.CombatRuleListener;
import com.example.vaultrush.listener.PlayerListener;
import com.example.vaultrush.listener.WorldProtectionListener;
import com.example.vaultrush.menu.BedrockBridgeLoader;
import com.example.vaultrush.menu.BedrockMenuBridge;
import com.example.vaultrush.menu.JobSelectionService;
import com.example.vaultrush.menu.PlayerMenuService;
import com.example.vaultrush.menu.ShopInventoryService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class VaultRushPlugin extends JavaPlugin {
    private static final Map<String, String> MESSAGE_FALLBACKS = Map.ofEntries(
            Map.entry("join-guidance", "&e欢迎来到宝库争夺！输入 &f/vr menu &e可重新打开游戏菜单。"),
            Map.entry("job-selected", "&a你已选择 &f%job%&a，并加入 &f%arena%&a 队列。（&f%count%&a/&f%max%&a）"),
            Map.entry("job-cooldown", "&e%job% 的主动技能仍在冷却，还需 &f%seconds% &e秒。"),
            Map.entry("job-activated", "&a已使用 %job% 的主动技能：&f%ability%&a。"),
            Map.entry("job-no-target", "&e附近没有可被侦察脉冲标记的敌人。"),
            Map.entry("job-usage", "&e用法：&f/vr job <assault|scout|guardian|engineer|illusionist> [竞技场]"),
            Map.entry("match-running", "&e竞技场 &f%arena% &e的比赛已经开始。"),
            Map.entry("arena-unavailable", "&c竞技场 &f%arena% &c当前不可用，倒计时已取消。"),
            Map.entry("admin-set-blocked", "&c竞技场 &f%arena% &c已启用或不在等待状态；请先停用后再修改位置。"),
            Map.entry("world-protection-locked", "&c比赛开始前地图受到保护，不能修改方块。"),
            Map.entry("world-protection-not-participant", "&c只有当前比赛的参赛者可以修改这个世界。"),
            Map.entry("world-protection-limit", "&c本局地图改动已达到安全上限，不能继续修改新方块。"),
            Map.entry("world-protection-start-failed", "&c地图保护无法启动，本场比赛未开始；你仍保留在队列中。"),
            Map.entry("world-protection-world-conflict", "&c竞技场 &f%arena% &c无法启用：世界 &f%world% &c已被另一个竞技场独占。")
    );
    private ArenaManager arenaManager;
    private QueueService queueService;
    private MatchController matchController;
    private GemService gemService;
    private VaultService vaultService;
    private ScoreboardService scoreboardService;
    private CleanupService cleanupService;
    private PlayerMenuService menuService;
    private ShopService shopService;
    private ShopInventoryService shopInventoryService;
    private JobService jobService;
    private JobSelectionService jobSelectionService;
    private WorldProtectionService worldProtectionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arenaManager = new ArenaManager(this);
        arenaManager.load();
        worldProtectionService = new WorldProtectionService(this, arenaManager);
        scoreboardService = new ScoreboardService(this);
        gemService = new GemService(this, arenaManager);
        vaultService = new VaultService(this, arenaManager);
        cleanupService = new CleanupService(this, arenaManager, gemService, scoreboardService);
        queueService = new QueueService(this, arenaManager);
        matchController = new MatchController(this, arenaManager, queueService, gemService, vaultService,
                scoreboardService, cleanupService);
        BedrockMenuBridge bedrockBridge = BedrockBridgeLoader.load(this);
        menuService = new PlayerMenuService(this, bedrockBridge);
        shopService = new ShopService(this, arenaManager, vaultService);
        shopInventoryService = new ShopInventoryService(this, shopService, bedrockBridge);
        jobService = new JobService(this);
        jobSelectionService = new JobSelectionService(this, bedrockBridge);

        VaultRushCommand command = new VaultRushCommand(this);
        if (getCommand("vaultrush") != null) {
            getCommand("vaultrush").setExecutor(command);
            getCommand("vaultrush").setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatRuleListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(menuService, this);
        getServer().getPluginManager().registerEvents(shopInventoryService, this);
        getServer().getPluginManager().registerEvents(jobSelectionService, this);
        gemService.clearPluginOwnedEntities();
        getLogger().info("VaultRush enabled with " + arenaManager.all().size() + " configured arena(s).");
    }

    @Override
    public void onDisable() {
        if (jobSelectionService != null) jobSelectionService.closeAll();
        if (shopInventoryService != null) shopInventoryService.closeAll();
        if (menuService != null) menuService.closeAll();
        if (cleanupService != null) cleanupService.cleanupAll();
    }

    public void reloadPluginConfig() {
        if (jobSelectionService != null) jobSelectionService.closeAll();
        if (shopInventoryService != null) shopInventoryService.closeAll();
        if (menuService != null) menuService.closeAll();
        if (cleanupService != null) cleanupService.cleanupAll();
        reloadConfig();
        arenaManager.load();
        gemService.clearPluginOwnedEntities();
    }

    public ArenaManager arenaManager() { return arenaManager; }
    public QueueService queueService() { return queueService; }
    public MatchController matchController() { return matchController; }
    public GemService gemService() { return gemService; }
    public VaultService vaultService() { return vaultService; }
    public ScoreboardService scoreboardService() { return scoreboardService; }
    public CleanupService cleanupService() { return cleanupService; }
    public PlayerMenuService menuService() { return menuService; }
    public ShopService shopService() { return shopService; }
    public ShopInventoryService shopInventoryService() { return shopInventoryService; }
    public JobService jobService() { return jobService; }
    public JobSelectionService jobSelectionService() { return jobSelectionService; }
    public WorldProtectionService worldProtectionService() { return worldProtectionService; }

    public int minPlayers() {
        int teamLimit = Math.max(1,
                getConfig().getInt("settings.team-size", 4)) * 2;
        return Math.min(teamLimit,
                Math.max(2, getConfig().getInt("settings.min-players", 2)));
    }
    public int maxPlayers() {
        int teamLimit = Math.max(1,
                getConfig().getInt("settings.team-size", 4)) * 2;
        int configured = Math.max(minPlayers(),
                getConfig().getInt("settings.max-players", 8));
        return Math.min(configured, teamLimit);
    }
    public int countdownSeconds() { return Math.max(0, getConfig().getInt("settings.countdown-seconds", 10)); }
    public int matchDurationSeconds() { return Math.max(1, getConfig().getInt("settings.match-duration-seconds", 600)); }
    public int scoreToWin() { return Math.max(0, getConfig().getInt("settings.score-to-win", 10)); }
    public int gemSpawnIntervalSeconds() { return Math.max(1, getConfig().getInt("settings.gem-spawn-interval-seconds", 8)); }
    public int maxVaultGems() { return Math.max(1, getConfig().getInt("settings.max-vault-gems", 3)); }
    public int deathGems() { return getConfig().getInt("settings.death-gems", 1); }
    public boolean dropCarriedGems() { return getConfig().getBoolean("settings.drop-carried-gems", true); }
    public double vaultRadius() { return Math.max(0.1, getConfig().getDouble("settings.vault-radius", 3.0)); }
    public double depositVerticalRadius() {
        return Math.max(0.1, getConfig().getDouble("settings.deposit-vertical-radius", 4.0));
    }
    public int respawnDelayTicks() { return Math.max(0, getConfig().getInt("settings.respawn-delay-ticks", 20)); }
    public int pickupDelayTicks() { return Math.max(0, getConfig().getInt("settings.pickup-delay-ticks", 10)); }
    public int kitBlocks() { return Math.max(0, getConfig().getInt("settings.kit.blocks", 32)); }
    public int kitFood() { return Math.max(0, getConfig().getInt("settings.kit.food", 8)); }

    public Material gemMaterial() { return material("settings.gem-material", Material.EMERALD); }
    public String gemName() { return getConfig().getString("settings.gem-name", "&aVault Gem"); }
    public Material kitSword() { return material("settings.kit.sword", Material.STONE_SWORD); }
    public Material kitBlockMaterial() { return material("settings.kit.block-material", Material.WHITE_WOOL); }
    public boolean autoOpenMenuOnJoin() { return getConfig().getBoolean("settings.menu.auto-open-on-join", true); }

    public String text(String key, Map<String, String> replacements) {
        String fallback = MESSAGE_FALLBACKS.getOrDefault(key, key);
        String raw = getConfig().getString("messages." + key, fallback);
        if (raw == null || raw.equals(key)) raw = fallback;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String menuText(String key, String fallback, Map<String, String> replacements) {
        String raw = com.example.vaultrush.menu.MenuTextResolver.resolve(getConfig(), key, fallback);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String message(String key, Map<String, String> replacements) {
        return prefix() + text(key, replacements);
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sender.sendMessage(message(key, replacements));
    }

    public void broadcast(ArenaMatch match, String key, Map<String, String> replacements) {
        if (match == null) return;
        for (java.util.UUID uuid : new java.util.HashSet<>(match.sessions().keySet())) {
            Player player = getServer().getPlayer(uuid);
            if (player != null) send(player, key, replacements);
        }
    }

    public void broadcastQueued(ArenaMatch match, String key, Map<String, String> replacements) {
        if (match == null) return;
        for (java.util.UUID uuid : new java.util.HashSet<>(match.queue())) {
            Player player = getServer().getPlayer(uuid);
            if (player != null) send(player, key, replacements);
        }
    }

    private String prefix() {
        String value = getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private Material material(String path, Material fallback) {
        String name = getConfig().getString(path, fallback.name());
        Material material = name == null ? null : Material.matchMaterial(name);
        return material == null ? fallback : material;
    }
}
