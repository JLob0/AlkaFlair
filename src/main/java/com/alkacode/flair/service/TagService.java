package com.alkacode.flair.service;

import com.alkacode.flair.config.FlairConfig;
import com.alkacode.flair.floating.FloatingTagManager;
import com.alkacode.flair.hook.LuckPermsHook;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.tag.Tag;
import com.alkacode.flair.tag.TagManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Regras de equipar/comprar/dar/remover tag - unico ponto que mexe em PlayerFlairData#equippedTagId/unlockedTagIds. */
public final class TagService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum EquipResult { SUCCESS, NOT_UNLOCKED, COOLDOWN, NOT_LOADED }

    public enum PurchaseResult { SUCCESS, ALREADY_UNLOCKED, NOT_PURCHASABLE, NOT_ENOUGH_CURRENCY, INVALID_CURRENCY }

    private final TagManager tagManager;
    private final FlairPlayerDataManager dataManager;
    private final FlairEconomyService economyService;
    private final LuckPermsHook luckPermsHook;
    private final FlairConfig config;
    private final FloatingTagManager floatingTagManager;

    public TagService(TagManager tagManager, FlairPlayerDataManager dataManager, FlairEconomyService economyService,
                       LuckPermsHook luckPermsHook, FlairConfig config, FloatingTagManager floatingTagManager) {
        this.tagManager = tagManager;
        this.dataManager = dataManager;
        this.economyService = economyService;
        this.luckPermsHook = luckPermsHook;
        this.config = config;
        this.floatingTagManager = floatingTagManager;
    }

    /** Desbloqueada = esta no set do jogador OU (tag tem permission-gate E o jogador tem essa permissao). So funciona pra jogador ONLINE (permission live). */
    public boolean isUnlocked(Player player, PlayerFlairData data, Tag tag) {
        if (data.unlockedTagIds().contains(tag.id())) {
            return true;
        }
        return tag.hasPermissionGate() && player.hasPermission(tag.permission());
    }

    public EquipResult equip(Player player, PlayerFlairData data, Tag tag) {
        if (!dataManager.isLoaded(player.getUniqueId())) {
            return EquipResult.NOT_LOADED;
        }
        if (!isUnlocked(player, data, tag)) {
            return EquipResult.NOT_UNLOCKED;
        }
        if (!player.hasPermission("alkaflair.tags.bypass.cooldown")) {
            long cooldown = config.tagSwitchCooldownSeconds();
            long now = System.currentTimeMillis() / 1000L;
            long remaining = data.lastTagSwitchEpochSeconds() + cooldown - now;
            if (cooldown > 0 && remaining > 0) {
                return EquipResult.COOLDOWN;
            }
            data.lastTagSwitchEpochSeconds(now);
        }
        data.equippedTagId(tag.id());
        dataManager.savePlayerRow(data);
        feedback(player, tag);
        refreshFloating(player, data);
        return EquipResult.SUCCESS;
    }

    public void unequip(Player player, PlayerFlairData data) {
        data.equippedTagId(null);
        dataManager.savePlayerRow(data);
        refreshFloating(player, data);
    }

    /** Recalcula a tag flutuante 3D so se o recurso estiver disponivel (PacketEvents instalado). */
    private void refreshFloating(Player player, PlayerFlairData data) {
        if (floatingTagManager != null) {
            floatingTagManager.refresh(player, data);
        }
    }

    public boolean forceEquipped() {
        return config.tagForceEquipped();
    }

    private void feedback(Player player, Tag tag) {
        if (config.tagTitleEnabled()) {
            var title = MM.deserialize(config.tagTitleFormat().replace("<tag_display>", tag.display()));
            var subtitle = MM.deserialize(config.tagSubtitleFormat());
            player.showTitle(Title.title(title, subtitle));
        }
        String soundName = config.tagSwitchSound();
        if (!soundName.isBlank()) {
            // Sound.valueOf aceita o formato de constante do enum (ex: UI_TOAST_CHALLENGE_COMPLETE),
            // que e o formato que o config.yml usa em todo o ecossistema Alka* (ver vips.yml) - o
            // equivalente via Registry.SOUNDS exigiria a chave namespaced com pontos
            // (ex: "ui.toast_challenge_complete"), formato diferente do que o config guarda.
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT)), 1f, 1f);
            } catch (IllegalArgumentException ignored) {
                // som invalido no config.yml - ignora silenciosamente
            }
        }
    }

    public PurchaseResult purchase(Player player, PlayerFlairData data, Tag tag) {
        if (data.unlockedTagIds().contains(tag.id())) {
            return PurchaseResult.ALREADY_UNLOCKED;
        }
        if (!tag.purchasable()) {
            return PurchaseResult.NOT_PURCHASABLE;
        }
        if (!economyService.isValidCurrency(tag.priceCurrency())) {
            return PurchaseResult.INVALID_CURRENCY;
        }
        if (!economyService.has(player.getUniqueId(), tag.priceCurrency(), tag.priceAmount())) {
            return PurchaseResult.NOT_ENOUGH_CURRENCY;
        }
        economyService.withdraw(player.getUniqueId(), tag.priceCurrency(), tag.priceAmount());
        unlock(player.getUniqueId(), data, tag);
        for (String command : config.economyPurchaseCommands()) {
            String parsed = command.replace("%player%", player.getName())
                    .replace("%tag_id%", tag.id())
                    .replace("%tag_permission%", tag.permission());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        return PurchaseResult.SUCCESS;
    }

    /** Desbloqueio direto (compra, voucher, /tags add) - idempotente, concede permission no LP se configurado. */
    public void unlock(UUID uuid, PlayerFlairData data, Tag tag) {
        dataManager.addUnlockedTag(data, tag.id());
        if (tag.hasPermissionGate()) {
            luckPermsHook.grantPermission(uuid, tag.permission());
        }
    }

    /** Admin (/tags del) - alvo pode estar offline, so refresca a tag flutuante se estiver online agora. */
    public void revoke(PlayerFlairData data, Tag tag) {
        dataManager.removeUnlockedTag(data, tag.id());
        if (tag.id().equals(data.equippedTagId())) {
            data.equippedTagId(null);
            dataManager.savePlayerRow(data);
            Player online = Bukkit.getPlayer(data.uuid());
            if (online != null) {
                refreshFloating(online, data);
            }
        }
    }

    /** onlinePlayer: null se o alvo (possivelmente offline) do /tags setar nao estiver online agora. */
    public void setOwn(Player onlinePlayer, PlayerFlairData data, String prefix, String suffix) {
        data.ownPrefix(prefix);
        data.ownSuffix(suffix);
        dataManager.savePlayerRow(data);
        if (onlinePlayer != null) {
            refreshFloating(onlinePlayer, data);
        }
    }

    public void clearOwn(Player onlinePlayer, PlayerFlairData data) {
        data.ownPrefix(null);
        data.ownSuffix(null);
        dataManager.savePlayerRow(data);
        if (onlinePlayer != null) {
            refreshFloating(onlinePlayer, data);
        }
    }

    public TagManager tagManager() {
        return tagManager;
    }

    /** Re-sincroniza a tag flutuante de todos os players online com o estado atual
     * (config + tags.yml recem-recarregados). Usado no /tags reload pra aplicar mudanca
     * de escala/altura/display-type/item AO VIVO, sem reiniciar o plugin - antes disso
     * a metadata so ia no equip/spawn, entao ajuste visual exigia restart. refresh()
     * respawna se o tipo mudou (texto<->item) ou so re-envia metadata se for o mesmo. */
    public void resyncFloatingTags() {
        if (floatingTagManager == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!dataManager.isLoaded(player.getUniqueId())) {
                continue;
            }
            PlayerFlairData data = dataManager.get(player.getUniqueId());
            if (data != null) {
                // Respawn forcado (destroi + recria) em vez de so re-enviar metadata:
                // garante que o client re-le escala/altura/item do zero. Update-only de
                // metadata em Display entity montada nem sempre re-aplica o transform.
                floatingTagManager.removeLocal(player);
                floatingTagManager.refresh(player, data);
            }
        }
    }
}
