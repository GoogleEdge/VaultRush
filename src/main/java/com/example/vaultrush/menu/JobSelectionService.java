package com.example.vaultrush.menu;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.arena.ArenaDefinition;
import com.example.vaultrush.arena.ArenaMatch;
import com.example.vaultrush.game.QueueService;
import com.example.vaultrush.model.JobType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JobSelectionService implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 14, 15};
    private final VaultRushPlugin plugin;
    private final BedrockMenuBridge bedrockBridge;
    private final NamespacedKey jobKey;
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final Map<UUID, Long> generations = new HashMap<>();

    public JobSelectionService(VaultRushPlugin plugin,
                               BedrockMenuBridge bedrockBridge) {
        this.plugin = plugin;
        this.bedrockBridge = bedrockBridge;
        this.jobKey = new NamespacedKey(plugin, "job_selection");
    }

    public void open(Player player, ArenaDefinition arena) {
        if (player == null || arena == null || !player.isOnline()
                || !player.hasPermission("vaultrush.play")) return;
        if (!arena.isValid()) {
            plugin.send(player, "invalid-arena", Map.of("arena", arena.id()));
            return;
        }
        if (!arena.enabled()) {
            plugin.send(player, "arena-disabled", Map.of("arena", arena.id()));
            return;
        }
        ArenaMatch match = plugin.arenaManager().match(arena);
        if (match.state() != com.example.vaultrush.arena.GameState.WAITING
                && match.state() != com.example.vaultrush.arena.GameState.COUNTDOWN) {
            plugin.send(player, "match-running", Map.of("arena", arena.id()));
            return;
        }
        closeAllInterfaces(player);
        UUID uuid = player.getUniqueId();
        long generation = generations.merge(uuid, 1L, Long::sum);
        boolean bedrock;
        try {
            bedrock = bedrockBridge.isBedrock(uuid);
        } catch (RuntimeException exception) {
            bedrock = false;
        }
        if (bedrock) {
            final long bedrockGeneration = generation;
            List<String> buttons = java.util.Arrays.stream(JobType.values())
                    .map(job -> job.displayName() + "\n"
                            + plugin.jobService().description(job))
                    .toList();
            boolean sent;
            try {
                sent = bedrockBridge.open(uuid, "选择职业",
                        "选择职业后才会加入 " + arena.id() + " 的队列。",
                        buttons, index -> plugin.getServer().getScheduler()
                                .runTask(plugin, () -> selectBedrock(uuid,
                                        arena.id(), bedrockGeneration, index)));
            } catch (RuntimeException exception) {
                sent = false;
            }
            if (sent) return;
            generation = generations.merge(uuid, 1L, Long::sum);
        }
        openJava(player, arena, generation);
    }

    public QueueService.JoinResult select(Player player, ArenaDefinition arena,
                                          JobType job) {
        if (player == null || !player.isOnline()
                || !player.hasPermission("vaultrush.play")
                || arena == null || job == null) {
            return QueueService.JoinResult.INVALID_ARENA;
        }
        ArenaMatch match = plugin.arenaManager().match(arena);
        QueueService.JoinResult result = plugin.queueService()
                .join(player, match, job);
        switch (result) {
            case JOINED -> {
                plugin.send(player, "job-selected", Map.of(
                        "arena", arena.id(),
                        "count", String.valueOf(match.queue().size()),
                        "max", String.valueOf(plugin.maxPlayers()),
                        "job", job.displayName()));
                plugin.matchController().maybeStart(match);
            }
            case ALREADY_QUEUED_OR_PLAYING ->
                    plugin.send(player, "already-queued", Map.of());
            case QUEUE_FULL -> plugin.send(player, "queue-full", Map.of());
            case INVALID_ARENA -> plugin.send(player, "invalid-arena",
                    Map.of("arena", arena.id()));
            case ARENA_DISABLED -> plugin.send(player, "arena-disabled",
                    Map.of("arena", arena.id()));
            case MATCH_RUNNING -> plugin.send(player, "match-running",
                    Map.of("arena", arena.id()));
        }
        return result;
    }

    public void close(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        generations.merge(uuid, 1L, Long::sum);
        bedrockBridge.close(uuid);
        Inventory inventory = open.remove(uuid);
        if (inventory != null
                && player.getOpenInventory().getTopInventory() == inventory) {
            player.closeInventory();
        }
    }

    public void closeAll() {
        for (UUID uuid : List.copyOf(generations.keySet())) {
            generations.merge(uuid, 1L, Long::sum);
            Player player = Bukkit.getPlayer(uuid);
            Inventory inventory = open.remove(uuid);
            if (player != null && inventory != null
                    && player.getOpenInventory().getTopInventory() == inventory) {
                player.closeInventory();
            }
        }
        open.clear();
        bedrockBridge.closeAll();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof JobInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!holder.owner().equals(uuid) || open.get(uuid) != top
                || generations.getOrDefault(uuid, 0L) != holder.generation()
                || !player.hasPermission("vaultrush.play")) return;
        int rawSlot = event.getRawSlot();
        JobType expected = jobAt(rawSlot);
        if (expected == null || rawSlot >= top.getSize()) return;
        ItemStack clicked = top.getItem(rawSlot);
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(jobKey, PersistentDataType.STRING);
        if (JobType.fromId(id) != expected) return;
        ArenaDefinition arena = plugin.arenaManager().get(holder.arenaId());
        close(player);
        if (arena != null) select(player, arena, expected);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof JobInventoryHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof JobInventoryHolder holder)) return;
        UUID uuid = event.getPlayer().getUniqueId();
        if (open.remove(uuid, inventory)) {
            generations.compute(uuid, (key, current) ->
                    current != null && current == holder.generation()
                            ? current + 1L : current);
        }
    }

    private void openJava(Player player, ArenaDefinition arena, long generation) {
        JobInventoryHolder holder = new JobInventoryHolder(
                player.getUniqueId(), arena.id(), generation);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                ChatColor.GOLD + "选择职业 · " + arena.id());
        holder.setInventory(inventory);
        fill(inventory);
        open.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        for (int index = 0; index < JobType.values().length; index++) {
            JobType job = JobType.values()[index];
            ItemStack icon = new ItemStack(job.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + job.displayName());
            meta.setLore(plugin.jobService().lore(job));
            meta.getPersistentDataContainer().set(jobKey,
                    PersistentDataType.STRING, job.id());
            icon.setItemMeta(meta);
            inventory.setItem(SLOTS[index], icon);
        }
    }

    private void selectBedrock(UUID uuid, String arenaId,
                               long generation, int index) {
        if (generations.getOrDefault(uuid, 0L) != generation) return;
        generations.remove(uuid, generation);
        bedrockBridge.close(uuid);
        Player player = Bukkit.getPlayer(uuid);
        ArenaDefinition arena = plugin.arenaManager().get(arenaId);
        JobType job = JobType.fromIndex(index);
        if (player == null || !player.isOnline()
                || !player.hasPermission("vaultrush.play")
                || arena == null || job == null) return;
        select(player, arena, job);
    }

    private JobType jobAt(int slot) {
        for (int index = 0; index < SLOTS.length; index++) {
            if (SLOTS[index] == slot) return JobType.fromIndex(index);
        }
        return null;
    }

    private void closeAllInterfaces(Player player) {
        if (plugin.menuService() != null) plugin.menuService().close(player);
        if (plugin.shopInventoryService() != null) {
            plugin.shopInventoryService().close(player);
        }
        close(player);
    }
}
