package com.alkacode.flair.gui;

import com.alkacode.core.gui.BaseGui;
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

/** Grade paginada de tags por categoria - clique equipa (se desbloqueada) ou abre confirmacao de compra. */
public final class TagsMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] CATEGORY_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final TagService tagService;
    private final FlairEconomyService economyService;
    private final FlairPlayerDataManager dataManager;
    private String activeCategory;

    public TagsMenu(JavaPlugin plugin, Player viewer, TagService tagService, FlairEconomyService economyService,
                     FlairPlayerDataManager dataManager) {
        super(plugin, viewer, "<dark_gray>Tags", 5, "flair_tags");
        this.tagService = tagService;
        this.economyService = economyService;
        this.dataManager = dataManager;
        List<TagCategory> categories = tagService.tagManager().orderedCategories();
        this.activeCategory = categories.isEmpty() ? "default" : categories.get(0).id();
    }

    @Override
    public void render() {
        fillBorder(createItem(Material.BLACK_STAINED_GLASS_PANE, " "));

        List<TagCategory> categories = tagService.tagManager().orderedCategories();
        for (int i = 0; i < categories.size() && i < CATEGORY_SLOTS.length; i++) {
            TagCategory category = categories.get(i);
            boolean active = category.id().equals(activeCategory);
            String label = (active ? "<bold>" : "") + category.display();
            setItem(CATEGORY_SLOTS[i],
                    createItem(active ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, label),
                    event -> {
                        activeCategory = category.id();
                        refresh();
                    });
        }

        renderGrid();
    }

    private void renderGrid() {
        List<Tag> tags = tagService.tagManager().tagsInCategory(activeCategory);
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        for (int i = 0; i < GRID_SLOTS.length && i < tags.size(); i++) {
            Tag tag = tags.get(i);
            boolean unlocked = data != null && tagService.isUnlocked(player, data, tag);
            boolean equipped = data != null && tag.id().equals(data.equippedTagId());
            setItem(GRID_SLOTS[i], buildTagIcon(tag, unlocked, equipped), event -> onClick(tag, unlocked));
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
                tagService.unequip(data);
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
        ItemStack item = tag.item().clone();
        ItemMeta meta = item.getItemMeta();
        String prefix = equipped ? "<green><bold>✔ " : unlocked ? "<white>" : "<gray>";
        meta.displayName(component(prefix + plain(tag.display())));

        List<Component> lore = new ArrayList<>();
        for (String line : tag.description()) {
            lore.add(component(line));
        }
        lore.add(component(" "));
        if (equipped) {
            lore.add(component(tagService.forceEquipped()
                    ? "<green>Equipada"
                    : "<green>Equipada <gray>(clique para remover)"));
        } else if (unlocked) {
            lore.add(component("<yellow>Clique para equipar"));
        } else if (tag.purchasable()) {
            lore.add(component("<gold>Preço: <white>" + economyService.formatAmount(tag.priceAmount()) + " " + tag.priceCurrency()));
            lore.add(component("<yellow>Clique para comprar"));
        } else {
            lore.add(component("<red>Indisponível"));
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
