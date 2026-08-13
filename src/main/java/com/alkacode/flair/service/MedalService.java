package com.alkacode.flair.service;

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

    public MedalService(MedalManager medalManager, FlairPlayerDataManager dataManager, LuckPermsHook luckPermsHook,
                         boolean grantPermissionOnUnlock) {
        this.medalManager = medalManager;
        this.dataManager = dataManager;
        this.luckPermsHook = luckPermsHook;
        this.grantPermissionOnUnlock = grantPermissionOnUnlock;
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
        return EquipResult.SUCCESS;
    }

    public void unequip(PlayerFlairData data, Medal medal) {
        dataManager.removeEquippedMedal(data, medal.id());
    }

    /** Desbloqueio direto (/medals add, voucher) - idempotente, concede permission no LP se configurado. */
    public void unlock(UUID uuid, PlayerFlairData data, Medal medal) {
        dataManager.addUnlockedMedal(data, medal.id());
        if (grantPermissionOnUnlock && medal.permission() != null && !medal.permission().isBlank()) {
            luckPermsHook.grantPermission(uuid, medal.permission());
        }
    }

    public void revoke(PlayerFlairData data, Medal medal) {
        dataManager.removeUnlockedMedal(data, medal.id());
        dataManager.removeEquippedMedal(data, medal.id());
    }

    public void setMaxSlots(PlayerFlairData data, int slots) {
        data.maxMedalSlots(Math.max(0, slots));
        dataManager.savePlayerRow(data);
    }

    public MedalManager medalManager() {
        return medalManager;
    }
}
