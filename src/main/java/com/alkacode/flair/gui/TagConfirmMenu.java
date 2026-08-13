package com.alkacode.flair.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.FlairEconomyService;
import com.alkacode.flair.service.TagService;
import com.alkacode.flair.tag.Tag;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Confirma a compra de uma tag - mostra preco e prefixo antes de cobrar de verdade. */
public final class TagConfirmMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TagService tagService;
    private final FlairEconomyService economyService;
    private final FlairPlayerDataManager dataManager;
    private final Tag tag;
    private final TagsMenu parent;

    public TagConfirmMenu(JavaPlugin plugin, Player viewer, TagService tagService, FlairEconomyService economyService,
                           FlairPlayerDataManager dataManager, Tag tag, TagsMenu parent) {
        super(plugin, viewer, "<dark_gray>Confirmar Compra", 3, "flair_tag_confirm");
        this.tagService = tagService;
        this.economyService = economyService;
        this.dataManager = dataManager;
        this.tag = tag;
        this.parent = parent;
    }

    @Override
    public void render() {
        fillBorder(createItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        setItem(13, buildPreview());

        setItem(11, createItem(Material.LIME_WOOL, "<green><bold>Confirmar",
                "<gray>Comprar por <white>" + economyService.formatAmount(tag.priceAmount()) + " " + tag.priceCurrency()),
                event -> {
                    PlayerFlairData data = dataManager.get(player.getUniqueId());
                    if (data == null) {
                        player.closeInventory();
                        return;
                    }
                    TagService.PurchaseResult result = tagService.purchase(player, data, tag);
                    if (result == TagService.PurchaseResult.SUCCESS) {
                        player.sendMessage(MM.deserialize("<green>Tag comprada com sucesso!"));
                    } else if (result == TagService.PurchaseResult.NOT_ENOUGH_CURRENCY) {
                        player.sendMessage(MM.deserialize("<red>Voce nao tem saldo suficiente."));
                    }
                    parent.open();
                });

        setItem(15, createItem(Material.RED_WOOL, "<red><bold>Cancelar", ""), event -> parent.open());
    }

    private ItemStack buildPreview() {
        ItemStack item = tag.item().clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(tag.display()).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize("<gray>Prefixo: " + tag.prefix()).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }
}
