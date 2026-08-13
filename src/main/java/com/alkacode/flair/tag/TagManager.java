package com.alkacode.flair.tag;

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

/** Carrega tags.yml (categorias + tags) - reload total substitui os dois mapas de uma vez. */
public final class TagManager {

    private final JavaPlugin plugin;
    private final Map<String, Tag> tags = new LinkedHashMap<>();
    private final Map<String, TagCategory> categories = new LinkedHashMap<>();

    public TagManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "tags.yml");
        if (!file.exists()) {
            try (InputStream in = plugin.getResource("tags.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Nao foi possivel criar tags.yml: " + e.getMessage());
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        Map<String, TagCategory> loadedCategories = new LinkedHashMap<>();
        ConfigurationSection categoriesSection = yaml.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String id : categoriesSection.getKeys(false)) {
                ConfigurationSection s = categoriesSection.getConfigurationSection(id);
                if (s == null) continue;
                loadedCategories.put(id.toLowerCase(), new TagCategory(id.toLowerCase(),
                        s.getString("display", id), s.getInt("position", 99)));
            }
        }

        Map<String, Tag> loadedTags = new LinkedHashMap<>();
        ConfigurationSection tagsSection = yaml.getConfigurationSection("tags");
        if (tagsSection != null) {
            for (String id : tagsSection.getKeys(false)) {
                ConfigurationSection s = tagsSection.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    loadedTags.put(id.toLowerCase(), parseTag(id.toLowerCase(), s));
                } catch (Exception e) {
                    plugin.getLogger().warning("Falha ao carregar a tag '" + id + "': " + e.getMessage());
                }
            }
        }

        categories.clear();
        categories.putAll(loadedCategories);
        tags.clear();
        tags.putAll(loadedTags);
    }

    private Tag parseTag(String id, ConfigurationSection s) {
        ConfigurationSection priceSection = s.getConfigurationSection("price");
        String priceCurrency = priceSection != null ? priceSection.getString("currency", "coins") : "coins";
        double priceAmount = priceSection != null ? priceSection.getDouble("amount", 0) : 0;

        return new Tag(
                id,
                s.getString("display", id),
                s.getString("prefix", ""),
                s.getString("suffix", ""),
                s.getStringList("description"),
                s.getString("category", "default").toLowerCase(),
                s.getInt("position", 99),
                s.getBoolean("purchasable", false),
                s.getBoolean("obtainable", true),
                priceCurrency,
                priceAmount,
                s.getString("permission", ""),
                s.getBoolean("glow", false),
                s.getStringList("aliases"),
                ItemBuilder.fromConfig(s.getConfigurationSection("item"), plugin.getLogger())
        );
    }

    public Tag get(String id) {
        return id != null ? tags.get(id.toLowerCase()) : null;
    }

    /** Resolve por id ou por alias configurado (/tags <alias> equipa direto). */
    public Tag getByIdOrAlias(String input) {
        Tag byId = get(input);
        if (byId != null) {
            return byId;
        }
        String lower = input.toLowerCase();
        for (Tag tag : tags.values()) {
            if (tag.aliases().contains(lower)) {
                return tag;
            }
        }
        return null;
    }

    public TagCategory category(String id) {
        return categories.get(id.toLowerCase());
    }

    public List<TagCategory> orderedCategories() {
        List<TagCategory> result = new ArrayList<>(categories.values());
        result.sort(Comparator.comparingInt(TagCategory::position));
        return result;
    }

    public List<Tag> orderedTags() {
        List<Tag> result = new ArrayList<>(tags.values());
        result.sort(Comparator.comparingInt(Tag::position));
        return result;
    }

    public List<Tag> tagsInCategory(String categoryId) {
        List<Tag> result = new ArrayList<>();
        for (Tag tag : orderedTags()) {
            if (tag.category().equalsIgnoreCase(categoryId)) {
                result.add(tag);
            }
        }
        return result;
    }

    public Map<String, Tag> all() {
        return tags;
    }
}
