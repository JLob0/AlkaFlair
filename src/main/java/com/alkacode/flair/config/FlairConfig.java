package com.alkacode.flair.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Wrapper tipado sobre config.yml - recarregado inteiro em /tags reload ou /medals reload. */
public final class FlairConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public FlairConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public int tagSwitchCooldownSeconds() {
        return config.getInt("tags.switch-cooldown-seconds", 3);
    }

    public boolean tagForceEquipped() {
        return config.getBoolean("tags.force-equipped", false);
    }

    public boolean tagTitleEnabled() {
        return config.getBoolean("tags.title.enabled", true);
    }

    public String tagTitleFormat() {
        return config.getString("tags.title.title", "<white><tag_display>");
    }

    public String tagSubtitleFormat() {
        return config.getString("tags.title.subtitle", "");
    }

    public String tagSwitchSound() {
        return config.getString("tags.sound", "");
    }

    public int medalsDefaultMaxSlots() {
        return config.getInt("medals.default-max-slots", 3);
    }

    public boolean medalsGrantPermissionOnUnlock() {
        return config.getBoolean("medals.grant-permission-on-unlock", true);
    }

    public List<String> economyPurchaseCommands() {
        return config.getStringList("economy.purchase-commands");
    }

    public String prefix() {
        return config.getString("messages.prefix", "");
    }

    public String message(String path) {
        return config.getString("messages." + path, "<red>Mensagem ausente: " + path);
    }
}
