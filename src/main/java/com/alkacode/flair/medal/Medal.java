package com.alkacode.flair.medal;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Badge cosmetico - varias podem estar equipadas ao mesmo tempo (ate o limite de
 * slots do jogador), diferente de {@link com.alkacode.flair.tag.Tag} (uma so por
 * vez). Sem preco: desbloqueia por permissao ja concedida em algum grupo, ou por
 * /medals add / voucher admin-dado.
 */
public record Medal(
        String id,
        String display,
        String name,
        List<String> description,
        String permission,
        String rarity,
        String source,
        int position,
        ItemStack item
) {
}
