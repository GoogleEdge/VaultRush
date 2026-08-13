package com.example.vaultrush.menu;

import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves current and legacy menu configuration keys without exposing key names to players. */
public final class MenuTextResolver {
    private MenuTextResolver() {
    }

    public static String resolve(Configuration configuration, String key, String fallback) {
        String raw = firstUsable(configuration, key, paths(key));
        return normalize(raw == null || raw.isBlank() ? fallback : raw);
    }

    private static String firstUsable(Configuration configuration, String key, List<String> paths) {
        if (configuration == null) return null;
        for (String path : paths) {
            String value = configuration.getString(path);
            if (value != null && !value.isBlank() && !looksLikeKey(value, key)) return value;
        }
        return null;
    }

    private static List<String> paths(String key) {
        List<String> paths = new ArrayList<>();
        paths.add("messages." + key);
        if (!key.startsWith("menu-")) return paths;

        String suffix = key.substring("menu-".length());
        paths.add("messages.menu." + suffix);
        paths.add("menu." + suffix);
        if (key.equals("menu-content")) {
            // Older builds called the shared menu prompt "use".
            paths.add("messages.menu-use");
            paths.add("messages.menu.use");
            paths.add("menu.use");
        }
        if (suffix.endsWith("-description")) {
            String action = suffix.substring(0, suffix.length() - "-description".length());
            paths.add("messages.menu." + action + ".description");
            paths.add("menu." + action + ".description");
            paths.add("messages.menu." + action + "-desc");
            paths.add("menu." + action + "-desc");
        }
        return paths;
    }

    private static boolean looksLikeKey(String value, String key) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String dotted = key.replace('-', '.').toLowerCase(Locale.ROOT);
        return normalized.equals(key.toLowerCase(Locale.ROOT))
                || normalized.equals(dotted)
                || normalized.startsWith("menu.");
    }

    private static String normalize(String value) {
        return value.replace("\\n", "\n");
    }
}
