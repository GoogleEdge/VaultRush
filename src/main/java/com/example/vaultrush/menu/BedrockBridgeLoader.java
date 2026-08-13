package com.example.vaultrush.menu;

import com.example.vaultrush.VaultRushPlugin;
import org.bukkit.plugin.Plugin;

public final class BedrockBridgeLoader {
    private BedrockBridgeLoader() {
    }

    public static BedrockMenuBridge load(VaultRushPlugin plugin) {
        Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            plugin.getLogger().info("Floodgate not found; Bedrock forms are disabled and Java inventory menus remain available.");
            return new NoopBedrockMenuBridge();
        }
        try {
            Class<?> bridgeClass = Class.forName(
                    "com.example.vaultrush.menu.FloodgateMenuBridge",
                    true,
                    plugin.getClass().getClassLoader()
            );
            Object instance = bridgeClass.getConstructor(VaultRushPlugin.class).newInstance(plugin);
            if (instance instanceof BedrockMenuBridge bridge) return bridge;
            throw new IllegalStateException("Floodgate bridge does not implement BedrockMenuBridge");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().warning("Floodgate integration could not be initialized; using Java inventory menus: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return new NoopBedrockMenuBridge();
        }
    }
}
