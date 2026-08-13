package com.alkacode.flair.api;

import java.util.UUID;

/**
 * API publica do AlkaFlair, registrada no {@link org.bukkit.plugin.ServicesManager}
 * (mesmo padrao do AlkaTimeAPI) - pensada pra integracoes futuras tipo "AlkaVips
 * concede uma tag automaticamente na compra do VIP" sem o consumidor precisar
 * importar nenhuma classe interna do AlkaFlair (so esta interface, resolvida via
 * reflection - consumidores NUNCA devem importar com.alkacode.flair.* direto, ver
 * convencao do ecossistema).
 */
public interface AlkaFlairAPI {

    /** Desbloqueia a tag pro jogador (idempotente) - funciona online ou offline. Nao equipa automaticamente. */
    void addTag(UUID uuid, String tagId);

    /** Remove a tag desbloqueada do jogador - desequipa se estava equipada. */
    void removeTag(UUID uuid, String tagId);

    /** true se o jogador tem a tag desbloqueada. So confiavel pra jogador ONLINE (checa cache em memoria). */
    boolean hasTag(UUID uuid, String tagId);

    /** Id da tag equipada agora, ou null. So confiavel pra jogador ONLINE. */
    String getEquippedTagId(UUID uuid);

    /** Desbloqueia a medalha pro jogador (idempotente) - funciona online ou offline. Nao equipa automaticamente. */
    void addMedal(UUID uuid, String medalId);

    /** Remove a medalha desbloqueada do jogador - desequipa se estava equipada. */
    void removeMedal(UUID uuid, String medalId);

    /** true se o jogador tem a medalha desbloqueada. So confiavel pra jogador ONLINE. */
    boolean hasMedal(UUID uuid, String medalId);
}
