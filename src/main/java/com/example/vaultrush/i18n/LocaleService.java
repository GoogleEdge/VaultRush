package com.example.vaultrush.i18n;

import com.example.vaultrush.VaultRushPlugin;
import com.example.vaultrush.menu.MenuTextResolver;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Resolves server-level locale text while preserving legacy config overrides. */
public final class LocaleService {
    private final VaultRushPlugin plugin;
    private Configuration chinese;
    private Configuration english;

    public LocaleService(VaultRushPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        chinese = load("locales/zh.yml");
        english = load("locales/en.yml");
    }

    public String resolve(String key, String fallback) {
        return resolve(plugin.getConfig(), chinese, english, language(), key, fallback);
    }

    /** Pure resolver entry point used by tests and other integrations. */
    public static String resolve(Configuration server, Configuration chinese,
                                Configuration english, String language,
                                String key, String fallback) {
        if (key == null || key.isBlank()) return safe(fallback);

        String override = configuredValue(server, "messages.override." + key, key);
        if (override != null) return override;

        String legacy = MenuTextResolver.resolve(server, key, "");
        String bundledChinese = MenuTextResolver.resolve(chinese, key, "");
        if (!legacy.isBlank() && !sameText(legacy, bundledChinese)) return legacy;

        Configuration selected = "en".equalsIgnoreCase(
                language == null ? "" : language.trim()) ? english : chinese;
        String value = MenuTextResolver.resolve(selected, key, "");
        if (!value.isBlank()) return value;
        if (selected != chinese) {
            value = MenuTextResolver.resolve(chinese, key, "");
            if (!value.isBlank()) return value;
        }
        return safe(fallback);
    }

    public String language() {
        String configured = plugin.getConfig().getString("settings.language", "zh");
        return "en".equalsIgnoreCase(configured == null ? "" : configured.trim())
                ? "en" : "zh";
    }

    public String resolve(String key, String fallback,
                          Map<String, String> replacements) {
        String value = resolve(key, fallback);
        if (replacements == null || replacements.isEmpty()) return value;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return value;
    }

    /** Resolves a setting-backed visible value while preserving explicit custom settings. */
    public String resolveSetting(String key, String path, String fallback) {
        String configured = plugin.getConfig().getString(path);
        String bundledChinese = MenuTextResolver.resolve(chinese, key, "");
        if (configured != null && !configured.isBlank()
                && !sameText(configured, bundledChinese)) {
            return configured.replace("\\n", "\n");
        }
        return resolve(key, fallback);
    }

    private static String configuredValue(Configuration configuration,
                                         String path, String key) {
        if (configuration == null) return null;
        String value = configuration.getString(path);
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        String dottedKey = key.replace('-', '.').toLowerCase(Locale.ROOT);
        if (trimmed.equals(normalizedKey) || trimmed.equals(dottedKey)
                || trimmed.startsWith("menu.")) return null;
        return value.replace("\\n", "\n");
    }

    private static boolean sameText(String first, String second) {
        return normalizeForComparison(first).equals(normalizeForComparison(second));
    }

    private static String normalizeForComparison(String value) {
        return value == null ? "" : value.replace("\\n", "\n").stripTrailing();
    }

    private Configuration load(String path) {
        InputStream input = plugin.getResource(path);
        if (input == null) {
            return new YamlConfiguration();
        }
        try (InputStream stream = input;
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            plugin.getLogger().warning("无法读取语言资源 " + path + "："
                    + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private static String safe(String fallback) {
        return fallback == null ? "" : fallback.replace("\\n", "\n");
    }
}
