package com.example.vaultrush.menu;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class NoopBedrockMenuBridge implements BedrockMenuBridge {
    @Override
    public boolean isBedrock(UUID uuid) {
        return false;
    }

    @Override
    public boolean open(UUID uuid, String title, String content, List<String> buttons, IntConsumer selection) {
        return false;
    }

    @Override
    public void close(UUID uuid) {
    }

    @Override
    public void closeAll() {
    }
}
