package com.alkacode.flair.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.flair.config.MenuConfig;
import com.alkacode.flair.gui.layout.GuiLayoutLoader;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.MedalService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Grade de medalhas - desbloqueadas primeiro (ordenadas por position), bloqueadas
 * depois com icone de cadeado. Clique alterna equipar/desequipar direto (sem menu
 * de selecao de slot separado - toggle simples, ja que o limite e so uma contagem,
 * nao slots numerados/reordenaveis).
 */
public final class MedalsMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MedalService medalService;
    private final FlairPlayerDataManager dataManager;

    public MedalsMenu(JavaPlugin plugin, Player viewer, MedalService medalService, FlairPlayerDataManager dataManager) {
        super(plugin, viewer, MenuConfig.getInstance().title("flair_medals.title", null), 5, "flair_medals");
        this.medalService = medalService;
        this.dataManager = dataManager;
    }

    @Override
    public void render() {
        GuiLayoutLoader.GuiLayout layout = GuiLayoutLoader.getInstance().getLayout("flair_medals");
        MenuConfig menu = MenuConfig.getInstance();
        fillBorder(menu.item("common.border", null));

        PlayerFlairData data = dataManager.get(player.getUniqueId());
        List<Medal> ordered = medalService.medalManager().ordered();
        List<Medal> unlocked = new ArrayList<>();
        List<Medal> locked = new ArrayList<>();
        for (Medal medal : ordered) {
            if (data != null && medalService.isUnlocked(player, data, medal)) {
                unlocked.add(medal);
            } else {
                locked.add(medal);
            }
        }
        List<Medal> display = new ArrayList<>(unlocked);
        display.addAll(locked);

        List<Integer> gridSlots = layout.findSlots('0');
        for (int i = 0; i < gridSlots.size() && i < display.size(); i++) {
            Medal medal = display.get(i);
            boolean isUnlocked = unlocked.contains(medal);
            boolean equipped = data != null && data.equippedMedalIds().contains(medal.id());
            setItem(gridSlots.get(i), buildIcon(medal, isUnlocked, equipped, data), event -> onClick(medal, isUnlocked));
        }

        String slotsInfo = data != null ? data.equippedMedalIds().size() + "/" + data.maxMedalSlots() : "0/0";
        setItem(layout.firstSlot('S'), menu.item("flair_medals.slots-info", Map.of("slots", slotsInfo)));
    }

    private void onClick(Medal medal, boolean unlocked) {
        if (!unlocked) {
            return;
        }
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        if (data.equippedMedalIds().contains(medal.id())) {
            medalService.unequip(data, medal);
        } else {
            MedalService.EquipResult result = medalService.equip(player, data, medal);
            if (result == MedalService.EquipResult.SLOTS_FULL) {
                player.sendMessage(component(MenuConfig.getInstance().text("flair_medals.slots-cheio",
                        Map.of("max", String.valueOf(data.maxMedalSlots())))));
            }
        }
        refresh();
    }

    private ItemStack buildIcon(Medal medal, boolean unlocked, boolean equipped, PlayerFlairData data) {
        MenuConfig menu = MenuConfig.getInstance();
        if (!unlocked) {
            return menu.item("flair_medals.bloqueada", Map.of("nome", plain(medal.name())));
        }

        ItemStack item = medal.item().clone();
        ItemMeta meta = item.getItemMeta();
        String prefixPath = equipped ? "flair_medals.prefixo-equipada" : "flair_medals.prefixo-desbloqueada";
        String prefix = menu.text(prefixPath, null);
        meta.displayName(component(prefix + plain(medal.name())));

        List<Component> lore = new ArrayList<>();
        for (String line : medal.description()) {
            lore.add(component(line));
        }
        if (!medal.rarity().isBlank()) {
            lore.add(component(menu.text("flair_medals.estado-raridade", Map.of("raridade", medal.rarity()))));
        }
        lore.add(component(" "));
        lore.add(component(menu.text(equipped ? "flair_medals.estado-desequipar" : "flair_medals.estado-equipar", null)));
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
