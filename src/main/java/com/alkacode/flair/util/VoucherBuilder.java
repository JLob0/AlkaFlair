package com.alkacode.flair.util;

import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.tag.Tag;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Constroi os vouchers fisicos (item + PDC) que {@link com.alkacode.flair.listener.VoucherListener} resgata. */
public final class VoucherBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private VoucherBuilder() {
    }

    public static ItemStack tagVoucher(Tag tag, int amount) {
        ItemStack item = new ItemStack(Material.NAME_TAG, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<white>Voucher: " + tag.display()).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize("<gray>Clique direito para resgatar.").decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(new NamespacedKey("alkaflair", "tag_voucher"), PersistentDataType.STRING, tag.id());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack tagPackVoucher(List<String> tagIds) {
        ItemStack item = new ItemStack(Material.CHEST, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<white>Pacote de Tags").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize("<gray>Contém <white>" + tagIds.size() + " <gray>tag(s).").decoration(TextDecoration.ITALIC, false),
                MM.deserialize("<gray>Clique direito para resgatar.").decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(new NamespacedKey("alkaflair", "tag_pack"), PersistentDataType.STRING,
                String.join(",", tagIds));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack medalVoucher(Medal medal, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<white>Voucher: " + medal.name()).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(MM.deserialize("<gray>Clique direito para resgatar.").decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(new NamespacedKey("alkaflair", "medal_voucher"), PersistentDataType.STRING, medal.id());
        item.setItemMeta(meta);
        return item;
    }
}
