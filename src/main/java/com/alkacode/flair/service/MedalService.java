package com.alkacode.flair.service;

import com.alkacode.flair.floating.FloatingTagManager;
import com.alkacode.flair.hook.LuckPermsHook;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.medal.MedalManager;
import com.alkacode.flair.model.PlayerFlairData;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Regras de equipar/desbloquear medalha - multi-equip ate maxMedalSlots, diferente da tag (uma so por vez). */
public final class MedalService {

    public enum EquipResult { SUCCESS, NOT_UNLOCKED, SLOTS_FULL, NOT_LOADED }

    private final MedalManager medalManager;
    private final FlairPlayerDataManager dataManager;
    private final LuckPermsHook luckPermsHook;
    private final boolean grantPermissionOnUnlock;
    private final FloatingTagManager floatingTagManager;

    public MedalService(MedalManager medalManager, FlairPlayerDataManager dataManager, LuckPermsHook luckPermsHook,
                         boolean grantPermissionOnUnlock, FloatingTagManager floatingTagManager) {
        this.medalManager = medalManager;
        this.dataManager = dataManager;
        this.luckPermsHook = luckPermsHook;
        this.grantPermissionOnUnlock = grantPermissionOnUnlock;
        this.floatingTagManager = floatingTagManager;
    }

    public boolean isUnlocked(Player player, PlayerFlairData data, Medal medal) {
        if (data.unlockedMedalIds().contains(medal.id())) {
            return true;
        }
        return medal.permission() != null && !medal.permission().isBlank() && player.hasPermission(medal.permission());
    }

    public EquipResult equip(Player player, PlayerFlairData data, Medal medal) {
        if (!dataManager.isLoaded(player.getUniqueId())) {
            return EquipResult.NOT_LOADED;
        }
        if (!isUnlocked(player, data, medal)) {
            return EquipResult.NOT_UNLOCKED;
        }
        if (data.equippedMedalIds().contains(medal.id())) {
            return EquipResult.SUCCESS;
        }
        if (data.equippedMedalIds().size() >= data.maxMedalSlots()) {
            return EquipResult.SLOTS_FULL;
        }
        dataManager.addEquippedMedal(data, medal.id());
        if (floatingTagManager != null) {
            floatingTagManager.refresh(player, data);
        }
        return EquipResult.SUCCESS;
    }

    public void unequip(Player player, PlayerFlairData data, Medal medal) {
        dataManager.removeEquippedMedal(data, medal.id());
        if (floatingTagManager != null) {
            floatingTagManager.refresh(player, data);
        }
    }

    /** Desbloqueio direto (/medals add, voucher) - idempotente, concede permission no LP se configurado. */
    public void unlock(UUID uuid, PlayerFlairData data, Medal medal) {
        dataManager.addUnlockedMedal(data, medal.id());
        if (grantPermissionOnUnlock && medal.permission() != null && !medal.permission().isBlank()) {
            luckPermsHook.grantPermission(uuid, medal.permission());
        }
    }

    /** Admin (/medals del) - alvo pode estar offline, so refresca a tag flutuante se estiver online agora. */
    public void revoke(PlayerFlairData data, Medal medal) {
        dataManager.removeUnlockedMedal(data, medal.id());
        dataManager.removeEquippedMedal(data, medal.id());
        Player online = org.bukkit.Bukkit.getPlayer(data.uuid());
        if (online != null && floatingTagManager != null) {
            floatingTagManager.refresh(online, data);
        }
    }

    public void setMaxSlots(PlayerFlairData data, int slots) {
        data.maxMedalSlots(Math.max(0, slots));
        dataManager.savePlayerRow(data);
    }

    public MedalManager medalManager() {
        return medalManager;
    }
}
