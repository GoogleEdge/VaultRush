package com.example.vaultrush.command;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaDefinition;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.arena.GameState;
import com.example.vaultrush.arena.Team;
import com.example.vaultrush.game.QueueService;
import com.example.vaultrush.game.ShopService;
import com.example.vaultrush.model.JobType;
import com.example.vaultrush.model.ShopItem;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VaultRushCommand implements CommandExecutor, TabCompleter {
    private final VaultRushPlugin plugin;

    public VaultRushCommand(VaultRushPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.send(sender, "help", Map.of());
            return true;
        }
        String root = args[0].toLowerCase(java.util.Locale.ROOT);
        if (root.equals("admin")) return admin(sender, label, args);
        if (!sender.hasPermission("vaultrush.play")) {
            plugin.send(sender, "no-permission", Map.of());
            return true;
        }
        return switch (root) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "list" -> list(sender);
            case "status" -> status(sender, args);
            case "menu" -> menu(sender);
            case "jobs" -> jobs(sender, args);
            case "job" -> job(sender, args);
            case "shop" -> shop(sender, args);
            default -> {
                plugin.send(sender, "help", Map.of());
                yield true;
            }
        };
    }

    private boolean join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only", Map.of());
            return true;
        }
        ArenaDefinition arena;
        if (args.length >= 2) arena = plugin.arenaManager().get(args[1]);
        else arena = plugin.queueService().defaultArena();
        if (arena == null) {
            if (args.length >= 2) plugin.send(sender, "unknown-arena", Map.of("arena", args[1]));
            else plugin.send(sender, "no-arenas", Map.of());
            return true;
        }
        if (!arena.isValid()) {
            plugin.send(sender, "invalid-arena", Map.of("arena", arena.id()));
            return true;
        }
        if (!arena.enabled()) {
            plugin.send(sender, "arena-disabled", Map.of("arena", arena.id()));
            return true;
        }
        plugin.jobSelectionService().open(player, arena);
        return true;
    }

    private boolean jobs(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only", Map.of());
            return true;
        }
        ArenaDefinition arena = args.length >= 2
                ? plugin.arenaManager().get(args[1])
                : plugin.queueService().defaultArena();
        if (arena == null) {
            plugin.send(sender, args.length >= 2 ? "unknown-arena" : "no-arenas",
                    args.length >= 2 ? Map.of("arena", args[1]) : Map.of());
            return true;
        }
        plugin.jobSelectionService().open(player, arena);
        return true;
    }

    private boolean job(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only", Map.of());
            return true;
        }
        if (args.length < 2) {
            plugin.send(player, "job-usage", Map.of());
            return true;
        }
        JobType job = JobType.fromId(args[1]);
        if (job == null) {
            plugin.send(player, "job-usage", Map.of());
            return true;
        }
        ArenaDefinition arena = args.length >= 3
                ? plugin.arenaManager().get(args[2])
                : plugin.queueService().defaultArena();
        if (arena == null) {
            plugin.send(player, args.length >= 3 ? "unknown-arena" : "no-arenas",
                    args.length >= 3 ? Map.of("arena", args[2]) : Map.of());
            return true;
        }
        plugin.jobSelectionService().select(player, arena, job);
        return true;
    }

    private boolean menu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only", Map.of());
            return true;
        }
        plugin.menuService().open(player);
        return true;
    }

    private boolean shop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only", Map.of());
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("buy")) {
            ShopItem item = ShopItem.fromId(args[2]);
            if (item == null) {
                plugin.send(player, "shop-usage", Map.of());
                return true;
            }
            ShopService.PurchaseResult result = plugin.shopService().purchase(player, item, null);
            plugin.shopService().sendFailure(player, item, result);
            return true;
        }
        plugin.shopInventoryService().open(player);
        return true;
    }

    private boolean leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only", Map.of());
            return true;
        }
        if (!plugin.matchController().leave(player)) plugin.send(player, "not-in-game", Map.of());
        else plugin.send(player, "left", Map.of());
        return true;
    }

    private boolean list(CommandSender sender) {
        if (plugin.arenaManager().all().isEmpty()) {
            plugin.send(sender, "no-arenas", Map.of());
            return true;
        }
        for (ArenaDefinition arena : plugin.arenaManager().all()) {
            ArenaMatch match = plugin.arenaManager().match(arena);
            plugin.send(sender, "list-entry", Map.of(
                    "arena", arena.id(),
                    "state", stateText(match.state()),
                    "queue", String.valueOf(match.queue().size()),
                    "red", String.valueOf(match.scores().get(Team.RED)),
                    "blue", String.valueOf(match.scores().get(Team.BLUE))
            ));
        }
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        ArenaDefinition arena = args.length >= 2 ? plugin.arenaManager().get(args[1]) : null;
        if (arena == null && sender instanceof Player player) {
            ArenaMatch playerMatch = plugin.matchController().matchFor(player.getUniqueId());
            if (playerMatch != null) arena = playerMatch.arena();
        }
        if (arena == null) arena = plugin.queueService().defaultArena();
        if (arena == null) {
            plugin.send(sender, "no-arenas", Map.of());
            return true;
        }
        ArenaMatch match = plugin.arenaManager().match(arena);
        plugin.send(sender, "status", Map.of(
                "arena", arena.id(),
                "state", stateText(match.state()),
                "queue", String.valueOf(match.queue().size()),
                "red", String.valueOf(match.scores().get(Team.RED)),
                "blue", String.valueOf(match.scores().get(Team.BLUE))
        ));
        return true;
    }

    private boolean admin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vaultrush.admin")) {
            plugin.send(sender, "no-permission", Map.of());
            return true;
        }
        if (args.length < 2) {
            plugin.send(sender, "admin-help", Map.of());
            return true;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("reload")) {
            plugin.reloadPluginConfig();
            plugin.send(sender, "reload", Map.of());
            return true;
        }
        if (args.length < 3) {
            plugin.send(sender, "missing-arena", Map.of());
            return true;
        }
        String id = args[2];
        switch (action) {
            case "create" -> {
                if (plugin.arenaManager().create(id)) {
                    plugin.arenaManager().save();
                    plugin.send(sender, "admin-created", Map.of("arena", id.toLowerCase(java.util.Locale.ROOT)));
                } else plugin.send(sender, "arena-create-failed", Map.of());
            }
            case "delete" -> {
                ArenaDefinition arena = plugin.arenaManager().get(id);
                if (arena == null) {
                    plugin.send(sender, "unknown-arena", Map.of("arena", id));
                    return true;
                }
                ArenaMatch match = plugin.arenaManager().match(arena);
                plugin.matchController().forceStop(match);
                plugin.arenaManager().removeMatch(match);
                plugin.arenaManager().delete(id);
                plugin.arenaManager().save();
                plugin.send(sender, "admin-deleted", Map.of("arena", id));
            }
            case "set" -> setLocation(sender, id, args);
            case "enable", "disable" -> setEnabled(sender, id, action.equals("enable"));
            case "start" -> start(sender, id);
            case "stop" -> stop(sender, id);
            default -> plugin.send(sender, "unknown-admin-action", Map.of());
        }
        return true;
    }

    private void setLocation(CommandSender sender, String id, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only", Map.of());
            return;
        }
        if (args.length < 4) {
            plugin.send(sender, "location-help", Map.of());
            return;
        }
        ArenaDefinition arena = plugin.arenaManager().get(id);
        if (arena == null) {
            plugin.send(sender, "unknown-arena", Map.of("arena", id));
            return;
        }
        ArenaMatch match = plugin.arenaManager().match(arena);
        if (arena.enabled() || match.state() != GameState.WAITING) {
            plugin.send(sender, "admin-set-blocked", Map.of("arena", arena.id()));
            return;
        }
        Location location = player.getLocation();
        switch (args[3].toLowerCase(java.util.Locale.ROOT)) {
            case "lobby" -> arena.setLobby(location);
            case "red-spawn" -> arena.setRedSpawn(location);
            case "blue-spawn" -> arena.setBlueSpawn(location);
            case "red-deposit" -> arena.setRedDeposit(location);
            case "blue-deposit" -> arena.setBlueDeposit(location);
            case "vault" -> arena.setVault(location);
            default -> {
                plugin.send(sender, "unknown-location-type", Map.of());
                return;
            }
        }
        plugin.arenaManager().save();
        plugin.send(sender, "admin-set", Map.of("arena", arena.id(), "type", args[3]));
    }

    private void setEnabled(CommandSender sender, String id, boolean enabled) {
        ArenaDefinition arena = plugin.arenaManager().get(id);
        if (arena == null) {
            plugin.send(sender, "unknown-arena", Map.of("arena", id));
            return;
        }
        if (enabled && !arena.isValid()) {
            plugin.send(sender, "invalid-arena", Map.of("arena", id));
            return;
        }
        if (enabled && plugin.arenaManager().hasEnabledWorldConflict(arena)) {
            plugin.send(sender, "world-protection-world-conflict",
                    Map.of("arena", arena.id(), "world", arena.worldName()));
            return;
        }
        if (!enabled) plugin.matchController().forceStop(plugin.arenaManager().match(arena));
        arena.setEnabled(enabled);
        plugin.arenaManager().save();
        plugin.send(sender, enabled ? "admin-enabled" : "admin-disabled", Map.of("arena", id));
    }

    private void start(CommandSender sender, String id) {
        ArenaDefinition arena = plugin.arenaManager().get(id);
        if (arena == null) {
            plugin.send(sender, "unknown-arena", Map.of("arena", id));
            return;
        }
        ArenaMatch match = plugin.arenaManager().match(arena);
        if (!arena.isValid() || !arena.enabled()) {
            plugin.send(sender, "invalid-arena", Map.of("arena", id));
            return;
        }
        if (match.queue().size() < plugin.minPlayers()) {
            plugin.send(sender, "not-enough-players", Map.of());
            return;
        }
        plugin.matchController().start(match);
    }

    private void stop(CommandSender sender, String id) {
        ArenaDefinition arena = plugin.arenaManager().get(id);
        if (arena == null) {
            plugin.send(sender, "unknown-arena", Map.of("arena", id));
            return;
        }
        plugin.matchController().forceStop(plugin.arenaManager().match(arena));
        plugin.send(sender, "forced-stop", Map.of());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(List.of("join", "leave", "list", "status", "menu", "jobs", "job", "shop", "admin"), args[0]);
        if (args[0].equalsIgnoreCase("job")) {
            if (args.length == 2) {
                return partial(java.util.Arrays.stream(JobType.values())
                        .map(JobType::id).toList(), args[1]);
            }
            if (args.length == 3) {
                List<String> ids = new ArrayList<>();
                for (ArenaDefinition arena : plugin.arenaManager().all()) ids.add(arena.id());
                return partial(ids, args[2]);
            }
        }
        if (args[0].equalsIgnoreCase("shop")) {
            if (args.length == 2) return partial(List.of("buy"), args[1]);
            if (args.length == 3 && args[1].equalsIgnoreCase("buy")) {
                return partial(java.util.Arrays.stream(ShopItem.values()).map(ShopItem::id).toList(), args[2]);
            }
        }
        if (args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) return partial(List.of("create", "delete", "set", "enable", "disable", "start", "stop", "reload"), args[1]);
            if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("reload")) {
                List<String> ids = new ArrayList<>();
                for (ArenaDefinition arena : plugin.arenaManager().all()) ids.add(arena.id());
                return partial(ids, args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                return partial(List.of("lobby", "red-spawn", "blue-spawn", "red-deposit", "blue-deposit", "vault"), args[3]);
            }
        }
        if ((args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("status")) && args.length == 2) {
            List<String> ids = new ArrayList<>();
            for (ArenaDefinition arena : plugin.arenaManager().all()) ids.add(arena.id());
            return partial(ids, args[1]);
        }
        return Collections.emptyList();
    }

    private String stateText(GameState state) {
        String key = switch (state) {
            case WAITING -> "state-waiting";
            case COUNTDOWN -> "state-countdown";
            case RUNNING -> "state-running";
            case ENDING -> "state-ending";
        };
        return plugin.text(key, Map.of());
    }

    private static List<String> partial(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) result.add(value);
        return result;
    }
}
