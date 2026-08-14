package com.example.vaultrush;

import com.example.vaultrush.i18n.LocaleService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaleServiceTest {
    @Test
    void selectsEnglishWhenServerLanguageIsEnglish() {
        YamlConfiguration server = new YamlConfiguration();
        server.set("settings.language", "en");
        YamlConfiguration chinese = messages("中文标题");
        YamlConfiguration english = messages("English title");

        assertEquals("English title", LocaleService.resolve(
                server, chinese, english, "en", "menu-title", "fallback"));
    }

    @Test
    void missingEnglishKeyFallsBackToChinese() {
        YamlConfiguration server = new YamlConfiguration();
        server.set("settings.language", "en");
        YamlConfiguration chinese = messages("中文标题");
        YamlConfiguration english = new YamlConfiguration();

        assertEquals("中文标题", LocaleService.resolve(
                server, chinese, english, "en", "menu-title", "fallback"));
    }

    @Test
    void explicitOverrideAndLegacyCustomTextTakePriority() {
        YamlConfiguration server = new YamlConfiguration();
        server.set("settings.language", "en");
        server.set("messages.menu-title", "旧配置标题");
        server.set("messages.override.menu-title", "明确覆盖标题");
        YamlConfiguration chinese = messages("中文标题");
        YamlConfiguration english = messages("English title");

        assertEquals("明确覆盖标题", LocaleService.resolve(
                server, chinese, english, "en", "menu-title", "fallback"));

        server.set("messages.override.menu-title", null);
        assertEquals("旧配置标题", LocaleService.resolve(
                server, chinese, english, "en", "menu-title", "fallback"));
    }

    @Test
    void defaultChineseBlockScalarDoesNotOverrideEnglish() {
        YamlConfiguration server = new YamlConfiguration();
        server.set("messages.menu-content", "第一行\n第二行\n");
        YamlConfiguration chinese = new YamlConfiguration();
        chinese.set("messages.menu-content", "第一行\\n第二行");
        YamlConfiguration english = new YamlConfiguration();
        english.set("messages.menu-content", "First line\\nSecond line");

        assertEquals("First line\nSecond line", LocaleService.resolve(
                server, chinese, english, "en", "menu-content", "fallback"));
    }

    @Test
    void bundledLocaleResourcesHaveMatchingMessageKeys() throws Exception {
        YamlConfiguration chinese = load("locales/zh.yml");
        YamlConfiguration english = load("locales/en.yml");
        Set<String> chineseKeys = chinese.getConfigurationSection("messages").getKeys(false);
        Set<String> englishKeys = english.getConfigurationSection("messages").getKeys(false);

        assertEquals(chineseKeys, englishKeys);
        assertEquals("&b打开菜单", chinese.getString("messages.menu-item-name"));
        assertEquals("&bOpen Menu", english.getString("messages.menu-item-name"));
        assertEquals("&a宝石", chinese.getString("messages.gem-name"));
        assertEquals("&aVault Gem", english.getString("messages.gem-name"));
        assertTrue(englishKeys.contains("shop-ability-use"));
        assertTrue(englishKeys.contains("shop-bedrock-entry"));
    }

    private static YamlConfiguration messages(String title) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("messages.menu-title", title);
        return configuration;
    }

    private static YamlConfiguration load(String path) throws Exception {
        try (InputStream input = LocaleServiceTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertTrue(input != null, "Missing test resource: " + path);
            try (InputStreamReader reader = new InputStreamReader(
                    input, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }
}
