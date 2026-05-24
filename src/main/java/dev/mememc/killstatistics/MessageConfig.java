package dev.mememc.killstatistics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MessageConfig {

    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public MessageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String getString(String path, String fallback) {
        String value = messages.getString(path, fallback);
        return colorize(value);
    }

    public List<String> getStringList(String path, List<String> fallback) {
        List<String> fromConfig = messages.getStringList(path);
        if (fromConfig == null || fromConfig.isEmpty()) {
            return fallback.stream().map(this::colorize).collect(Collectors.toList());
        }
        List<String> out = new ArrayList<>();
        for (String line : fromConfig) {
            out.add(colorize(line));
        }
        return out;
    }

    public String format(String template, Map<String, String> replacements) {
        String result = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
