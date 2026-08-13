package com.example.vaultrush;

import com.example.vaultrush.menu.MenuTextResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MenuTextResolverTest {
    @Test
    void flatKeysTakePriorityAndEscapedNewlinesAreNormalized() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.menu-title", "扁平标题");
        config.set("messages.menu.title", "旧标题");
        config.set("messages.menu-content", "第一行\\n第二行");

        assertEquals("扁平标题", MenuTextResolver.resolve(config, "menu-title", "默认"));
        assertEquals("第一行\n第二行", MenuTextResolver.resolve(config, "menu-content", "默认"));
    }

    @Test
    void legacyKeysAndSafeFallbackNeverLeakMenuKey() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.menu.title", "旧式标题");
        config.set("messages.menu.use", "旧式说明");
        assertEquals("旧式标题", MenuTextResolver.resolve(config, "menu-title", "默认标题"));
        assertEquals("旧式说明", MenuTextResolver.resolve(config, "menu-use", "默认说明"));
        assertEquals("旧式说明", MenuTextResolver.resolve(config, "menu-content", "默认内容"));

        YamlConfiguration missing = new YamlConfiguration();
        String fallback = MenuTextResolver.resolve(missing, "menu-title", "中文默认标题");
        assertEquals("中文默认标题", fallback);
        assertFalse(fallback.contains("menu"));
    }
}
