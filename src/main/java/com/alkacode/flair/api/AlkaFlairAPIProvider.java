package com.alkacode.flair.api;

import com.alkacode.core.scheduler.AlkaScheduler;
import com.alkacode.flair.hook.LuckPermsHook;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.medal.MedalManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.storage.FlairRepository;
import com.alkacode.flair.tag.Tag;
import com.alkacode.flair.tag.TagManager;

import java.util.UUID;

/**
 * Implementacao real da {@link AlkaFlairAPI}. Pra jogador ONLINE (cache carregado),
 * mexe direto no cache via {@link FlairPlayerDataManager} (write-through, reflete na
 * hora). Pra jogador OFFLINE, escreve direto no {@link FlairRepository} sem montar
 * um PlayerFlairData inteiro - so a linha filha do set desbloqueado muda, nao ha
 * necessidade de carregar/salvar a linha principal do jogador pra isso.
 */
public final class AlkaFlairAPIProvider implements AlkaFlairAPI {

    private final FlairPlayerDataManager dataManager;
    private final FlairRepository repository;
    private final TagManager tagManager;
    private final MedalManager medalManager;
    private final AlkaScheduler scheduler;
    private final LuckPermsHook luckPermsHook;

    public AlkaFlairAPIProvider(FlairPlayerDataManager dataManager, FlairRepository repository, TagManager tagManager,
                                 MedalManager medalManager, AlkaScheduler scheduler, LuckPermsHook luckPermsHook) {
        this.dataManager = dataManager;
        this.repository = repository;
        this.tagManager = tagManager;
        this.medalManager = medalManager;
        this.scheduler = scheduler;
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public void addTag(UUID uuid, String tagId) {
        Tag tag = tagManager.get(tagId);
        if (tag == null) {
            return;
        }
        PlayerFlairData cached = dataManager.get(uuid);
        if (cached != null) {
            dataManager.addUnlockedTag(cached, tagId);
        } else {
            scheduler.runAsync(() -> repository.addUnlockedTag(uuid, tagId));
        }
        if (tag.hasPermissionGate()) {
            luckPermsHook.grantPermission(uuid, tag.permission());
        }
    }

    @Override
    public void removeTag(UUID uuid, String tagId) {
        PlayerFlairData cached = dataManager.get(uuid);
        if (cached != null) {
            dataManager.removeUnlockedTag(cached, tagId);
            if (tagId.equalsIgnoreCase(cached.equippedTagId())) {
                cached.equippedTagId(null);
                dataManager.savePlayerRow(cached);
            }
        } else {
            scheduler.runAsync(() -> repository.removeUnlockedTag(uuid, tagId));
        }
    }

    @Override
    public boolean hasTag(UUID uuid, String tagId) {
        PlayerFlairData cached = dataManager.get(uuid);
        return cached != null && cached.unlockedTagIds().contains(tagId.toLowerCase());
    }

    @Override
    public String getEquippedTagId(UUID uuid) {
        PlayerFlairData cached = dataManager.get(uuid);
        return cached != null ? cached.equippedTagId() : null;
    }

    @Override
    public void addMedal(UUID uuid, String medalId) {
        Medal medal = medalManager.get(medalId);
        if (medal == null) {
            return;
        }
        PlayerFlairData cached = dataManager.get(uuid);
        if (cached != null) {
            dataManager.addUnlockedMedal(cached, medalId);
        } else {
            scheduler.runAsync(() -> repository.addUnlockedMedal(uuid, medalId));
        }
        if (medal.permission() != null && !medal.permission().isBlank()) {
            luckPermsHook.grantPermission(uuid, medal.permission());
        }
    }

    @Override
    public void removeMedal(UUID uuid, String medalId) {
        PlayerFlairData cached = dataManager.get(uuid);
        if (cached != null) {
            dataManager.removeUnlockedMedal(cached, medalId);
            dataManager.removeEquippedMedal(cached, medalId);
        } else {
            scheduler.runAsync(() -> {
                repository.removeUnlockedMedal(uuid, medalId);
                repository.removeEquippedMedal(uuid, medalId);
            });
        }
    }

    @Override
    public boolean hasMedal(UUID uuid, String medalId) {
        PlayerFlairData cached = dataManager.get(uuid);
        return cached != null && cached.unlockedMedalIds().contains(medalId.toLowerCase());
    }
}
