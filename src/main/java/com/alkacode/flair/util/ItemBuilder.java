package com.alkacode.flair.util;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Constroi ItemStack de menu a partir de config.yml/tags.yml/medals.yml - so o essencial (material/nome/lore). */
public final class ItemBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ItemBuilder() {
    }

    public static ItemStack fromConfig(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            logger.warning("Material invalido em " + section.getCurrentPath() + " - usando STONE.");
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = section.getString("name");
        if (name != null) {
            meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        }

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList()));
        }

        item.setItemMeta(meta);
        return item;
    }
}
