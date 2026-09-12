package com.alkacode.flair.tag;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Cosmetico de prefixo/sufixo equipavel. Nao substitui o grupo do LuckPerms - so
 * sobrepoe visualmente via placeholder, consumido pelo TAB/nChat. {@code permission}
 * e opcional: quando presente, conta como uma via A MAIS de desbloqueio (alem de
 * purchasable/admin-add) - util pra ligar uma tag a um rank sem duplicar controle
 * de acesso em dois lugares.
 */
public record Tag(
        String id,
        String display,
        String prefix,
        String suffix,
        List<String> description,
        String rarity,
        String source,
        String category,
        int position,
        boolean purchasable,
        boolean obtainable,
        String priceCurrency,
        double priceAmount,
        String permission,
        boolean glow,
        boolean floating,
        String floatingItem,
        List<String> aliases,
        ItemStack item
) {
    public boolean hasPermissionGate() {
        return permission != null && !permission.isBlank();
    }
}
