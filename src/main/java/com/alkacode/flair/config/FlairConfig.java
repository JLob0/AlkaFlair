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

    public boolean floatingTagEnabled() {
        return config.getBoolean("floating-tag.enabled", true);
    }

    public double floatingTagOffsetY() {
        return config.getDouble("floating-tag.offset-y", 0.4);
    }

    public double floatingTagScale() {
        return config.getDouble("floating-tag.scale", 1.0);
    }

    public double floatingTagViewDistance() {
        return config.getDouble("floating-tag.view-distance", 48);
    }

    public int floatingTagBackgroundColor() {
        return config.getInt("floating-tag.background-color", 0);
    }

    /** 0-255 (0 = texto/icone totalmente transparente, 255 = totalmente opaco/normal). */
    public int floatingTagTextOpacity() {
        return Math.max(0, Math.min(255, config.getInt("floating-tag.text-opacity", 255)));
    }

    public int floatingTagLineWidth() {
        return config.getInt("floating-tag.line-width", 200);
    }

    public boolean floatingTagSeeThrough() {
        return config.getBoolean("floating-tag.see-through", true);
    }

    public boolean floatingTagShowMedals() {
        return config.getBoolean("floating-tag.show-medals", false);
    }

    // ---- Modo ITEM (ItemDisplay) - usado por tags com floating-item (escudos animados) ----

    public double floatingItemScale() {
        return config.getDouble("floating-tag.item-scale", 0.6);
    }

    public double floatingItemOffsetY() {
        return config.getDouble("floating-tag.item-offset-y", 0.5);
    }

    /** display_type do ItemDisplay (0-8). 6=GUI (chapado de frente, tipo inventario),
     * 8=FIXED (tipo item frame). Default GUI - calibrar no jogo. */
    public int floatingItemDisplayType() {
        return config.getInt("floating-tag.item-display-type", 6);
    }

    public String floatingTagSeparator() {
        return config.getString("floating-tag.separator", " ");
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
