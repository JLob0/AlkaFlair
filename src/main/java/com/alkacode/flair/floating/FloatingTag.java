package com.alkacode.flair.floating;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estado de uma tag 3D flutuante ativa - entidade puramente client-side (so existe
 * como packet, nunca entra no World do Bukkit). entityId negativo pra nunca colidir
 * com o id de uma entidade real do servidor (esses sao sempre positivos).
 */
public final class FloatingTag {

    private final int entityId;
    private final UUID entityUuid;
    private final Player owner;
    private Component text;

    public FloatingTag(Player owner, Component text) {
        this.owner = owner;
        this.text = text;
        this.entityId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1_000_000);
        this.entityUuid = UUID.randomUUID();
    }

    public int entityId() {
        return entityId;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public Player owner() {
        return owner;
    }

    public Component text() {
        return text;
    }

    public void text(Component text) {
        this.text = text;
    }
}
