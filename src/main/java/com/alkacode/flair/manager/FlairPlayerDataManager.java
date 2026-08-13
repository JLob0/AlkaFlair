package com.alkacode.flair.manager;

import com.alkacode.core.scheduler.AlkaScheduler;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.storage.FlairRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache em memoria de {@link PlayerFlairData} por jogador online - write-through
 * (toda mutacao atualiza o cache na hora e agenda a persistencia async, mesmo
 * padrao do KitProgressManager). {@code isLoaded} deve ser checado antes de
 * qualquer acao que dependa do estado do jogador (equipar/comprar) pra nunca agir
 * sobre um cache ainda vazio por causa do load assincrono do join nao ter terminado.
 */
public final class FlairPlayerDataManager {

    private final FlairRepository repository;
    private final AlkaScheduler scheduler;
    private final int defaultMaxMedalSlots;
    private final Map<UUID, PlayerFlairData> cache = new ConcurrentHashMap<>();

    public FlairPlayerDataManager(FlairRepository repository, AlkaScheduler scheduler, int defaultMaxMedalSlots) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.defaultMaxMedalSlots = defaultMaxMedalSlots;
    }

    public void onJoin(UUID uuid) {
        scheduler.runAsync(() -> {
            PlayerFlairData loaded = repository.load(uuid, defaultMaxMedalSlots);
            cache.put(uuid, loaded);
        });
    }

    public void onQuit(UUID uuid) {
        cache.remove(uuid);
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public PlayerFlairData get(UUID uuid) {
        return cache.get(uuid);
    }

    /** Usado por comandos admin em jogadores OFFLINE - carrega, aplica a mutacao, salva tudo, sem entrar no cache de jogador online. */
    public PlayerFlairData loadOffline(UUID uuid) {
        return repository.load(uuid, defaultMaxMedalSlots);
    }

    public void savePlayerRow(PlayerFlairData data) {
        scheduler.runAsync(() -> repository.savePlayerRow(data));
    }

    public void addUnlockedTag(PlayerFlairData data, String tagId) {
        if (data.unlockedTagIds().add(tagId)) {
            scheduler.runAsync(() -> repository.addUnlockedTag(data.uuid(), tagId));
        }
    }

    public void removeUnlockedTag(PlayerFlairData data, String tagId) {
        if (data.unlockedTagIds().remove(tagId)) {
            scheduler.runAsync(() -> repository.removeUnlockedTag(data.uuid(), tagId));
        }
    }

    public void addUnlockedMedal(PlayerFlairData data, String medalId) {
        if (data.unlockedMedalIds().add(medalId)) {
            scheduler.runAsync(() -> repository.addUnlockedMedal(data.uuid(), medalId));
        }
    }

    public void removeUnlockedMedal(PlayerFlairData data, String medalId) {
        if (data.unlockedMedalIds().remove(medalId)) {
            scheduler.runAsync(() -> repository.removeUnlockedMedal(data.uuid(), medalId));
        }
    }

    public void addEquippedMedal(PlayerFlairData data, String medalId) {
        if (data.equippedMedalIds().add(medalId)) {
            scheduler.runAsync(() -> repository.addEquippedMedal(data.uuid(), medalId));
        }
    }

    public void removeEquippedMedal(PlayerFlairData data, String medalId) {
        if (data.equippedMedalIds().remove(medalId)) {
            scheduler.runAsync(() -> repository.removeEquippedMedal(data.uuid(), medalId));
        }
    }
}
