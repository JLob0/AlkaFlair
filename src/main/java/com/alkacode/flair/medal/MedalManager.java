package com.alkacode.flair.medal;

import com.alkacode.flair.util.ItemBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MedalManager {

    private final JavaPlugin plugin;
    private final Map<String, Medal> medals = new LinkedHashMap<>();

    public MedalManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "medals.yml");
        if (!file.exists()) {
            try (InputStream in = plugin.getResource("medals.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Nao foi possivel criar medals.yml: " + e.getMessage());
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Medal> loaded = new LinkedHashMap<>();
        ConfigurationSection medalsSection = yaml.getConfigurationSection("medals");
        if (medalsSection != null) {
            for (String id : medalsSection.getKeys(false)) {
                ConfigurationSection s = medalsSection.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    loaded.put(id.toLowerCase(), parseMedal(id.toLowerCase(), s));
                } catch (Exception e) {
                    plugin.getLogger().warning("Falha ao carregar a medalha '" + id + "': " + e.getMessage());
                }
            }
        }
        medals.clear();
        medals.putAll(loaded);
    }

    private Medal parseMedal(String id, ConfigurationSection s) {
        return new Medal(
                id,
                s.getString("display", id),
                s.getString("name", id),
                s.getStringList("description"),
                s.getString("permission", ""),
                s.getString("rarity", ""),
                s.getString("source", ""),
                s.getInt("position", 99),
                ItemBuilder.fromConfig(s.getConfigurationSection("item"), plugin.getLogger())
        );
    }

    public Medal get(String id) {
        return id != null ? medals.get(id.toLowerCase()) : null;
    }

    public List<Medal> ordered() {
        List<Medal> result = new ArrayList<>(medals.values());
        result.sort(Comparator.comparingInt(Medal::position));
        return result;
    }

    public Map<String, Medal> all() {
        return medals;
    }
}
