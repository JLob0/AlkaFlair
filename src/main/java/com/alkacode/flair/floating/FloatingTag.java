package com.alkacode.flair.floating;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Estado de uma tag 3D flutuante ativa - entidade puramente client-side (so existe
 * como packet, nunca entra no World do Bukkit). entityId negativo pra nunca colidir
 * com o id de uma entidade real do servidor (esses sao sempre positivos).
 *
 * <p>Dois modos, mutuamente exclusivos: TEXTO (TextDisplay, glifo/prefixo - o padrao)
 * ou ITEM (ItemDisplay com um item do ItemsAdder - usado pelos escudos animados, cuja
 * animacao e nativa do client via .mcmeta). {@link #isItem()} distingue - o tipo da
 * entidade spawnada e a metadata dependem disso, entao trocar de modo exige respawn.</p>
 */
public final class FloatingTag {

    private final int entityId;
    private final UUID entityUuid;
    private final Player owner;
    private Component text;      // modo texto (null no modo item)
    private final ItemStack item; // modo item (null no modo texto)

    private FloatingTag(Player owner, Component text, ItemStack item) {
        this.owner = owner;
        this.text = text;
        this.item = item;
        this.entityId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1_000_000);
        this.entityUuid = UUID.randomUUID();
    }

    public static FloatingTag ofText(Player owner, Component text) {
        return new FloatingTag(owner, text, null);
    }

    public static FloatingTag ofItem(Player owner, ItemStack item) {
        return new FloatingTag(owner, null, item);
    }

    public boolean isItem() {
        return item != null;
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

    public ItemStack item() {
        return item;
    }
}
