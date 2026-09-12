package com.alkacode.flair.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.flair.config.MenuConfig;
import com.alkacode.flair.gui.layout.GuiLayoutLoader;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.FlairEconomyService;
import com.alkacode.flair.service.TagService;
import com.alkacode.flair.tag.Tag;
import com.alkacode.flair.tag.TagCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Grade paginada de tags por categoria - clique equipa (se desbloqueada) ou abre confirmacao de compra. */
public final class TagsMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TagService tagService;
    private final FlairEconomyService economyService;
    private final FlairPlayerDataManager dataManager;
    private String activeCategory;
    private int page;

    public TagsMenu(JavaPlugin plugin, Player viewer, TagService tagService, FlairEconomyService economyService,
                     FlairPlayerDataManager dataManager) {
        super(plugin, viewer, MenuConfig.getInstance().title("flair_tags.title", null), 5, "flair_tags");
        this.tagService = tagService;
        this.economyService = economyService;
        this.dataManager = dataManager;
        List<TagCategory> categories = tagService.tagManager().orderedCategories();
        this.activeCategory = categories.isEmpty() ? "default" : categories.get(0).id();
    }

    @Override
    public void render() {
        GuiLayoutLoader.GuiLayout layout = GuiLayoutLoader.getInstance().getLayout("flair_tags");
        MenuConfig menu = MenuConfig.getInstance();
        fillBorder(menu.item("common.border", null));

        List<Integer> categorySlots = layout.findSlots('C');
        List<TagCategory> categories = tagService.tagManager().orderedCategories();
        for (int i = 0; i < categories.size() && i < categorySlots.size(); i++) {
            TagCategory category = categories.get(i);
            boolean active = category.id().equals(activeCategory);
            String path = active ? "flair_tags.categoria-ativa" : "flair_tags.categoria-inativa";
            String label = (active ? "<bold>" : "") + category.display();
            ItemStack icon = buildCategoryIcon(category, path, label, active, menu);
            setItem(categorySlots.get(i), icon, event -> {
                activeCategory = category.id();
                page = 0;
                refresh();
            });
        }

        renderGrid(layout, menu);
    }

    /** Aba de categoria: usa o icone customizado (tags.yml categories.<id>.icon/itemsadder)
     * quando definido, com glow (menus.yml flair_tags.categoria-*.glow) marcando a ativa;
     * sem icone customizado, cai no vidro generico de sempre. */
    private ItemStack buildCategoryIcon(TagCategory category, String path, String label, boolean active,
                                         MenuConfig menu) {
        if (!category.hasCustomIcon()) {
            return menu.item(path, Map.of("nome", label));
        }
        Material fallback = category.icon().isBlank() ? Material.PAPER : Material.matchMaterial(category.icon());
        if (fallback == null) {
            fallback = Material.PAPER;
        }
        ItemStack item = iaItem(category.itemsAdderId(), fallback);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(component(label));
        if (menu.flag(path + ".glow", active)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void renderGrid(GuiLayoutLoader.GuiLayout layout, MenuConfig menu) {
        List<Integer> gridSlots = layout.findSlots('0');
        List<Tag> tags = tagService.tagManager().tagsInCategory(activeCategory);
        int pageSize = gridSlots.size();
        int maxPage = pageSize == 0 ? 0 : Math.max(0, (tags.size() - 1) / pageSize);
        if (page > maxPage) {
            page = maxPage;
        }
        int from = page * pageSize;

        PlayerFlairData data = dataManager.get(player.getUniqueId());
        for (int i = 0; i < gridSlots.size() && (from + i) < tags.size(); i++) {
            Tag tag = tags.get(from + i);
            boolean unlocked = data != null && tagService.isUnlocked(player, data, tag);
            boolean equipped = data != null && tag.id().equals(data.equippedTagId());
            setItem(gridSlots.get(i), buildTagIcon(tag, unlocked, equipped), event -> onClick(tag, unlocked));
        }

        if (page > 0) {
            setItem(layout.firstSlot('P'), menu.item("flair_tags.prev-page", null), event -> {
                page--;
                refresh();
            });
        }
        if (from + pageSize < tags.size()) {
            setItem(layout.firstSlot('N'), menu.item("flair_tags.next-page", null), event -> {
                page++;
                refresh();
            });
        }
    }

    private void onClick(Tag tag, boolean unlocked) {
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (unlocked) {
            boolean alreadyEquipped = tag.id().equals(data.equippedTagId());
            if (alreadyEquipped && !tagService.forceEquipped()) {
                tagService.unequip(player, data);
            } else if (!alreadyEquipped) {
                tagService.equip(player, data, tag);
            }
            refresh();
            return;
        }
        if (tag.purchasable()) {
            new TagConfirmMenu(plugin, player, tagService, economyService, dataManager, tag, this).open();
        }
    }

    private ItemStack buildTagIcon(Tag tag, boolean unlocked, boolean equipped) {
        MenuConfig menu = MenuConfig.getInstance();
        ItemStack item = tag.item().clone();
        ItemMeta meta = item.getItemMeta();
        String prefixPath = equipped ? "flair_tags.prefixo-equipada"
                : unlocked ? "flair_tags.prefixo-desbloqueada" : "flair_tags.prefixo-bloqueada";
        String prefix = menu.text(prefixPath, null);
        meta.displayName(component(prefix + plain(tag.display())));

        List<Component> lore = new ArrayList<>();
        for (String line : tag.description()) {
            lore.add(component(line));
        }
        lore.add(component(" "));
        if (equipped) {
            lore.add(component(menu.text(tagService.forceEquipped()
                    ? "flair_tags.estado-equipada" : "flair_tags.estado-equipada-clique", null)));
        } else if (unlocked) {
            lore.add(component(menu.text("flair_tags.estado-desbloqueada", null)));
        } else if (tag.purchasable()) {
            lore.add(component(menu.text("flair_tags.estado-comprar-preco",
                    Map.of("preco", economyService.formatAmount(tag.priceAmount()), "moeda", tag.priceCurrency()))));
            lore.add(component(menu.text("flair_tags.estado-comprar-clique", null)));
        } else {
            lore.add(component(menu.text("flair_tags.estado-indisponivel", null)));
        }
        meta.lore(lore);
        if (equipped) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component component(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private String plain(String miniMessage) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(MM.deserialize(miniMessage));
    }
}
