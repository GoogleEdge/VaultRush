package com.example.vaultrush.menu;

import com.example.vaultrush.VaultRushPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class FloodgateMenuBridge implements BedrockMenuBridge {
    private final FloodgateApi api;
    private final java.util.Set<UUID> openForms = new java.util.HashSet<>();

    public FloodgateMenuBridge(VaultRushPlugin plugin) {
        this.api = FloodgateApi.getInstance();
        if (api == null) throw new IllegalStateException("Floodgate API is unavailable");
    }

    @Override
    public boolean isBedrock(UUID uuid) {
        try {
            return uuid != null && api.isFloodgatePlayer(uuid);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean open(UUID uuid, String title, String content, List<String> buttons, IntConsumer selection) {
        if (uuid == null || selection == null || !isBedrock(uuid)) return false;
        try {
            SimpleForm.Builder builder = SimpleForm.builder().title(title).content(content);
            for (String button : buttons) builder.button(button);
            builder.validResultHandler((SimpleFormResponse response) -> {
                openForms.remove(uuid);
                int index = response.clickedButtonId();
                if (index >= 0) selection.accept(index);
            });
            boolean sent = api.sendForm(uuid, builder);
            if (sent) openForms.add(uuid);
            return sent;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void close(UUID uuid) {
        if (uuid == null) return;
        openForms.remove(uuid);
        try {
            api.closeForm(uuid);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void closeAll() {
        for (UUID uuid : java.util.Set.copyOf(openForms)) close(uuid);
        openForms.clear();
    }
}
