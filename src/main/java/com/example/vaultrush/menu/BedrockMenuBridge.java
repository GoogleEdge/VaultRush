package com.example.vaultrush.menu;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

public interface BedrockMenuBridge {
    boolean isBedrock(UUID uuid);

    boolean open(UUID uuid, String title, String content, List<String> buttons, IntConsumer selection);

    void close(UUID uuid);

    void closeAll();
}
