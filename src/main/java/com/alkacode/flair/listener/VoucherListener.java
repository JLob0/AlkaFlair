package com.alkacode.flair.listener;

import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.medal.MedalManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.MedalService;
import com.alkacode.flair.service.TagService;
import com.alkacode.flair.tag.Tag;
import com.alkacode.flair.tag.TagManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Voucher fisico: item com PDC "alkaflair:tag_voucher"/"alkaflair:medal_voucher"
 * (STRING, o id) ou "alkaflair:tag_pack" (STRING_LIST, varios ids de tag de uma
 * vez). Clique direito consome 1 unidade e desbloqueia. Mesmo formato de chave que
 * o restante do ecossistema usa pra vouchers (ver AlkaKits/AlkaVips).
 */
public final class VoucherListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TagManager tagManager;
    private final MedalManager medalManager;
    private final TagService tagService;
    private final MedalService medalService;
    private final FlairPlayerDataManager dataManager;
    private final NamespacedKey tagVoucherKey;
    private final NamespacedKey medalVoucherKey;
    private final NamespacedKey tagPackKey;

    public VoucherListener(JavaPlugin plugin, TagManager tagManager, MedalManager medalManager, TagService tagService,
                            MedalService medalService, FlairPlayerDataManager dataManager) {
        this.tagManager = tagManager;
        this.medalManager = medalManager;
        this.tagService = tagService;
        this.medalService = medalService;
        this.dataManager = dataManager;
        this.tagVoucherKey = new NamespacedKey(plugin, "tag_voucher");
        this.medalVoucherKey = new NamespacedKey(plugin, "medal_voucher");
        this.tagPackKey = new NamespacedKey(plugin, "tag_pack");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Player player = event.getPlayer();

        if (pdc.has(tagVoucherKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            redeemTag(player, pdc.get(tagVoucherKey, PersistentDataType.STRING), item);
        } else if (pdc.has(medalVoucherKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            redeemMedal(player, pdc.get(medalVoucherKey, PersistentDataType.STRING), item);
        } else if (pdc.has(tagPackKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            List<String> ids = List.of(pdc.get(tagPackKey, PersistentDataType.STRING).split(","));
            redeemTagPack(player, ids, item);
        }
    }

    private void redeemTag(Player player, String tagId, ItemStack voucher) {
        Tag tag = tagManager.get(tagId);
        if (tag == null) {
            return;
        }
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null || !dataManager.isLoaded(player.getUniqueId())) {
            return;
        }
        tagService.unlock(player.getUniqueId(), data, tag);
        consumeOne(player, voucher);
        player.sendMessage(MM.deserialize("<green>Você resgatou a tag <white>" + tag.display() + "<green>!"));
    }

    private void redeemTagPack(Player player, List<String> tagIds, ItemStack voucher) {
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null || !dataManager.isLoaded(player.getUniqueId())) {
            return;
        }
        int count = 0;
        for (String tagId : tagIds) {
            Tag tag = tagManager.get(tagId.trim());
            if (tag != null) {
                tagService.unlock(player.getUniqueId(), data, tag);
                count++;
            }
        }
        consumeOne(player, voucher);
        player.sendMessage(MM.deserialize("<green>Você resgatou <white>" + count + " tag(s)<green>!"));
    }

    private void redeemMedal(Player player, String medalId, ItemStack voucher) {
        Medal medal = medalManager.get(medalId);
        if (medal == null) {
            return;
        }
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null || !dataManager.isLoaded(player.getUniqueId())) {
            return;
        }
        medalService.unlock(player.getUniqueId(), data, medal);
        consumeOne(player, voucher);
        player.sendMessage(MM.deserialize("<green>Você resgatou a medalha <white>" + medal.name() + "<green>!"));
    }

    private void consumeOne(Player player, ItemStack voucher) {
        if (voucher.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            voucher.setAmount(voucher.getAmount() - 1);
        }
    }
}
