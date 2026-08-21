package com.alkacode.flair.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.flair.config.MenuConfig;
import com.alkacode.flair.gui.layout.GuiLayoutLoader;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.FlairEconomyService;
import com.alkacode.flair.service.TagService;
import com.alkacode.flair.tag.Tag;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

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
        super(plugin, viewer, MenuConfig.getInstance().title("flair_tag_confirm.title", null), 3, "flair_tag_confirm");
        this.tagService = tagService;
        this.economyService = economyService;
        this.dataManager = dataManager;
        this.tag = tag;
        this.parent = parent;
    }

    @Override
    public void render() {
        GuiLayoutLoader.GuiLayout layout = GuiLayoutLoader.getInstance().getLayout("flair_tag_confirm");
        MenuConfig menu = MenuConfig.getInstance();
        fillBorder(menu.item("common.border", null));
        setItem(layout.firstSlot('P'), buildPreview());

        setItem(layout.firstSlot('C'), menu.item("flair_tag_confirm.confirmar", Map.of(
                        "preco", economyService.formatAmount(tag.priceAmount()), "moeda", tag.priceCurrency())),
                event -> {
                    PlayerFlairData data = dataManager.get(player.getUniqueId());
                    if (data == null) {
                        player.closeInventory();
                        return;
                    }
                    TagService.PurchaseResult result = tagService.purchase(player, data, tag);
                    if (result == TagService.PurchaseResult.SUCCESS) {
                        player.sendMessage(MM.deserialize(menu.text("flair_tag_confirm.compra-sucesso", null)));
                    } else if (result == TagService.PurchaseResult.NOT_ENOUGH_CURRENCY) {
                        player.sendMessage(MM.deserialize(menu.text("flair_tag_confirm.saldo-insuficiente", null)));
                    }
                    parent.open();
                });

        setItem(layout.firstSlot('N'), menu.item("flair_tag_confirm.cancelar", null), event -> parent.open());
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
