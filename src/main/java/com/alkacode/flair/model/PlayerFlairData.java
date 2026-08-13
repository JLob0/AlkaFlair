package com.alkacode.flair.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Estado por jogador - tags e medalhas desbloqueadas/equipadas num unico objeto
 * (uma linha em {@code alka_flair_players} + duas tabelas filhas pros sets
 * desbloqueados, ver FlairRepository). Mutavel de proposito (mesmo padrao de
 * VipPlayerData/PlayerTimeManager) - o cache em memoria e a copia de trabalho,
 * write-through pro banco a cada mudanca.
 */
public final class PlayerFlairData {

    private final UUID uuid;
    private final Set<String> unlockedTagIds = new HashSet<>();
    private final Set<String> unlockedMedalIds = new HashSet<>();
    private final Set<String> equippedMedalIds = new HashSet<>();
    private String equippedTagId;
    private String ownPrefix;
    private String ownSuffix;
    private boolean tagEnabled = true;
    private int maxMedalSlots;
    private long lastTagSwitchEpochSeconds;

    public PlayerFlairData(UUID uuid, int defaultMaxMedalSlots) {
        this.uuid = uuid;
        this.maxMedalSlots = defaultMaxMedalSlots;
    }

    public UUID uuid() {
        return uuid;
    }

    public Set<String> unlockedTagIds() {
        return unlockedTagIds;
    }

    public Set<String> unlockedMedalIds() {
        return unlockedMedalIds;
    }

    public Set<String> equippedMedalIds() {
        return equippedMedalIds;
    }

    public String equippedTagId() {
        return equippedTagId;
    }

    public void equippedTagId(String equippedTagId) {
        this.equippedTagId = equippedTagId;
    }

    public String ownPrefix() {
        return ownPrefix;
    }

    public void ownPrefix(String ownPrefix) {
        this.ownPrefix = ownPrefix;
    }

    public String ownSuffix() {
        return ownSuffix;
    }

    public void ownSuffix(String ownSuffix) {
        this.ownSuffix = ownSuffix;
    }

    public boolean tagEnabled() {
        return tagEnabled;
    }

    public void tagEnabled(boolean tagEnabled) {
        this.tagEnabled = tagEnabled;
    }

    public int maxMedalSlots() {
        return maxMedalSlots;
    }

    public void maxMedalSlots(int maxMedalSlots) {
        this.maxMedalSlots = maxMedalSlots;
    }

    public long lastTagSwitchEpochSeconds() {
        return lastTagSwitchEpochSeconds;
    }

    public void lastTagSwitchEpochSeconds(long value) {
        this.lastTagSwitchEpochSeconds = value;
    }
}
