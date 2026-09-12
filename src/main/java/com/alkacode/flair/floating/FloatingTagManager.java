package com.alkacode.flair.floating;

import com.alkacode.flair.config.FlairConfig;
import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.medal.MedalManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.tag.Tag;
import com.alkacode.flair.tag.TagManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.world.Location;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Tag cosmetica 3D flutuando acima da cabeca do jogador - um TextDisplay
 * puramente client-side (packet-only, nunca existe no World do Bukkit) montado
 * como passageiro do player via Set Passengers. Zero entidade real, zero task de
 * teleporte (o cliente sincroniza a posicao do passageiro sozinho).
 *
 * <p>Indices de entity data verificados contra o protocolo (Display base comeca em
 * 8, TextDisplay em 23) - nao vem de reflection em campo interno do NMS/Paper de
 * proposito: esse projeto ja levou um susto com paper-api SNAPSHOT resolvendo pra
 * conteudo mais novo silenciosamente (ver memoria project-paper-snapshot-drift),
 * o que quebraria nomes de campo mojang-mapped sem aviso. Indice de protocolo e
 * constante do jogo, nao do build - so muda em breaking change real do MC.</p>
 */
public final class FloatingTagManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Display (base) - rotacao fica no default do client (sem rotacao) sem precisar declarar.
    private static final int DATA_TRANSLATION = 11;
    private static final int DATA_SCALE = 12;
    private static final int DATA_BILLBOARD_CONSTRAINTS = 15;

    // TextDisplay
    private static final int DATA_TEXT = 23;
    private static final int DATA_LINE_WIDTH = 24;
    private static final int DATA_BACKGROUND_COLOR = 25;
    private static final int DATA_TEXT_OPACITY = 26;
    private static final int DATA_STYLE_FLAGS = 27;

    // ItemDisplay - mesma base Display (8-22); primeiro campo especifico (item) em 23,
    // display_type em 24. Indices verificados igual os do TextDisplay (protocolo, nao NMS).
    private static final int DATA_ITEM = 23;
    private static final int DATA_DISPLAY_TYPE = 24;

    private static final byte BILLBOARD_CENTER = 3;
    private static final byte STYLE_FLAG_SEE_THROUGH = 0x02;

    // dado ja salvo malformado (gradient sem cor, etc) vaza tag MiniMessage crua -
    // mesmo guard de PlaceholderAPIHook#safeMiniMessage, texto flutuante nunca deve
    // mostrar "<gradient:>" cru acima da cabeca do jogador.
    private static final Pattern LEAKED_TAG = Pattern.compile("<[a-zA-Z_][a-zA-Z0-9_]*(:[^<>]*)?>");

    private final TagManager tagManager;
    private final MedalManager medalManager;
    private final FlairConfig config;
    private final Map<UUID, FloatingTag> activeTags = new ConcurrentHashMap<>();

    public FloatingTagManager(TagManager tagManager, MedalManager medalManager, FlairConfig config) {
        this.tagManager = tagManager;
        this.medalManager = medalManager;
        this.config = config;
    }

    /** Recalcula e sincroniza a tag flutuante de um jogador a partir do estado atual (tag equipada + medalhas). Chame apos qualquer equip/unequip/setOwn/clearOwn. */
    public void refresh(Player player, PlayerFlairData data) {
        if (!config.floatingTagEnabled()) {
            return;
        }
        Content content = buildContent(player, data);
        if (content.isEmpty()) {
            unequipTag(player);
            return;
        }
        FloatingTag existing = activeTags.get(player.getUniqueId());
        // Trocar entre modo texto e modo item exige respawn (tipo de entidade diferente) -
        // so da pra fazer updateTag (metadata) se o tipo bater com o que ja esta ativo.
        if (existing != null && existing.isItem() == content.isItem()) {
            updateTag(player, content);
        } else {
            equipTag(player, content);
        }
    }

    /** Chamado no join (com delay): mostra pro jogador que entrou as tags de todo mundo que ele ja pode ver, e publica a dele propria (se ja tiver uma equipada) pros outros. */
    public void onPlayerReady(Player player, PlayerFlairData data) {
        sendNearbyTags(player);
        refresh(player, data);
    }

    /** Troca de mundo: o Set Passengers e uma entidade fake fora do World, entao o
     * client NAO desmancha ela sozinho quando o player (vehicle real) some do mundo
     * antigo - precisa destruir explicitamente pra nao deixar um TextDisplay orfao
     * flutuando pros jogadores de la, e recriar do zero pros do mundo novo. */
    public void relocate(Player player, PlayerFlairData data) {
        if (!config.floatingTagEnabled()) {
            return;
        }
        unequipTag(player);
        Content content = buildContent(player, data);
        if (!content.isEmpty()) {
            equipTag(player, content);
        }
        sendNearbyTags(player);
    }

    /** Chamado no quit - broadcast de destroy pros viewers antes de limpar o cache local.
     * Nao da pra confiar que o client desmancha o TextDisplay sozinho quando o vehicle
     * (o player) desconecta - em disconnect abrupto (crash, fecha o client, timeout) o
     * passenger fica orfao flutuando no ar pros outros, so desmontado sem ser destruido. */
    public void removeLocal(Player player) {
        unequipTag(player);
    }

    private void equipTag(Player player, Content content) {
        unequipTag(player);
        FloatingTag tag = content.isItem()
                ? FloatingTag.ofItem(player, content.item)
                : FloatingTag.ofText(player, content.text);
        activeTags.put(player.getUniqueId(), tag);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (canReceive(viewer, player)) {
                sendSpawnPacket(viewer, tag);
                sendMountPacket(viewer, player, tag);
                sendMetadataPacket(viewer, tag);
            }
        }
    }

    private void updateTag(Player player, Content content) {
        FloatingTag tag = activeTags.get(player.getUniqueId());
        if (tag == null) {
            return;
        }
        if (!tag.isItem()) {
            tag.text(content.text); // modo item: o item e final (mesmo escudo), so re-envia metadata
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (canReceive(viewer, player)) {
                sendMetadataPacket(viewer, tag);
            }
        }
    }

    private void unequipTag(Player player) {
        FloatingTag tag = activeTags.remove(player.getUniqueId());
        if (tag == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendDestroyPacket(viewer, tag);
        }
    }

    private void sendNearbyTags(Player viewer) {
        for (FloatingTag tag : activeTags.values()) {
            Player owner = tag.owner();
            if (owner.equals(viewer) || !canReceive(viewer, owner)) {
                continue;
            }
            sendSpawnPacket(viewer, tag);
            sendMountPacket(viewer, owner, tag);
            sendMetadataPacket(viewer, tag);
        }
    }

    private boolean canReceive(Player viewer, Player owner) {
        if (viewer.equals(owner) || !viewer.isOnline() || !owner.isOnline()) {
            return false;
        }
        if (!viewer.canSee(owner) || !viewer.getWorld().equals(owner.getWorld())) {
            return false;
        }
        double maxDistance = config.floatingTagViewDistance();
        return viewer.getLocation().distanceSquared(owner.getLocation()) <= maxDistance * maxDistance;
    }

    /** O que sobe pra tag flutuante: um ITEM (ItemDisplay, escudo animado do IA) ou um
     * TEXTO (TextDisplay, glifo/prefixo). Modo item tem prioridade quando a tag equipada
     * tem `floating-item` e o item resolve no IA; senao cai no texto (que ja filtra so
     * tags floating:true + ownPrefix). */
    private Content buildContent(Player player, PlayerFlairData data) {
        if (!data.tagEnabled()) {
            return Content.EMPTY;
        }
        // Modo ITEM: tag equipada tem floating-item. ownPrefix (override manual do admin)
        // ganha do item - se setado, vai pro modo texto abaixo.
        String tagId = data.equippedTagId();
        boolean ownPrefixSet = data.ownPrefix() != null && !data.ownPrefix().isBlank();
        if (tagId != null && !ownPrefixSet) {
            Tag tag = tagManager.get(tagId);
            if (tag != null && tag.floatingItem() != null && !tag.floatingItem().isBlank()) {
                ItemStack item = resolveItemStack(tag.floatingItem());
                if (item != null) {
                    return Content.item(item);
                }
                // Item nao resolveu (IA ausente / id errado): cai no texto, que mostra o
                // glifo estatico do prefixo (%img_rank_shield_X%) como degradacao graciosa.
            }
        }
        // Modo TEXTO (comportamento atual): so tags floating:true + ownPrefix, + medalhas
        // se floating-tag.show-medals estiver ligado (default false).
        Component result = floatingPrefix(player, data);
        if (config.floatingTagShowMedals()) {
            String separator = config.floatingTagSeparator();
            for (Medal medal : equippedMedalsOrdered(data)) {
                result = result.append(Component.text(separator)).append(safeDeserialize(player, medal.display()));
            }
        }
        if (PlainTextComponentSerializer.plainText().serialize(result).isBlank()) {
            return Content.EMPTY;
        }
        return Content.text(result);
    }

    /** Resolve o ItemStack de um item do ItemsAdder (ex: "myranks:shield_diamond_anim")
     * via reflection na API oficial (nunca import direto - regra do ecossistema), e
     * converte pro ItemStack do PacketEvents. null se o IA nao estiver presente, o id nao
     * existir, ou a API mudar - o chamador cai no glifo de texto nesse caso. */
    private ItemStack resolveItemStack(String iaId) {
        try {
            Class<?> cls = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object cs = cls.getMethod("getInstance", String.class).invoke(null, iaId);
            if (cs == null) {
                return null;
            }
            org.bukkit.inventory.ItemStack bukkit =
                    (org.bukkit.inventory.ItemStack) cls.getMethod("getItemStack").invoke(cs);
            return bukkit == null ? null : SpigotConversionUtil.fromBukkitItemStack(bukkit);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Conteudo da tag flutuante: exatamente um de texto/item, ou vazio. */
    private static final class Content {
        static final Content EMPTY = new Content(null, null);
        final Component text;
        final ItemStack item;

        private Content(Component text, ItemStack item) {
            this.text = text;
            this.item = item;
        }

        static Content text(Component text) {
            return new Content(text, null);
        }

        static Content item(ItemStack item) {
            return new Content(null, item);
        }

        boolean isEmpty() {
            return text == null && item == null;
        }

        boolean isItem() {
            return item != null;
        }
    }

    /** Prefixo que sobe pra tag flutuante 3D. Diferente do prefixo de chat/TAB: aqui so
     * sobem tags marcadas com {@code floating: true} (icones grandes tipo knight/shields).
     * Tag de texto comum (dragao, membro) fica no chat/TAB mas NAO flutua acima da cabeca.
     * ownPrefix setado por admin sempre flutua (override manual explicito). */
    private Component floatingPrefix(Player player, PlayerFlairData data) {
        if (data.ownPrefix() != null && !data.ownPrefix().isBlank()) {
            return safeDeserialize(player, data.ownPrefix());
        }
        if (data.equippedTagId() == null) {
            return Component.empty();
        }
        Tag tag = tagManager.get(data.equippedTagId());
        if (tag == null || !tag.floating()) {
            return Component.empty();
        }
        return safeDeserialize(player, tag.prefix());
    }

    private List<Medal> equippedMedalsOrdered(PlayerFlairData data) {
        return data.equippedMedalIds().stream()
                .map(medalManager::get)
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(Medal::position))
                .toList();
    }

    /** Resolve %placeholders% do PAPI (ex: %img_rank_knight% do ItemsAdder) ANTES do
     * MiniMessage, pra permitir icones/dados dinamicos em prefix/display de tag e
     * medalha - so na tag flutuante, que roda 100% em Java (o resto do ecossistema
     * ja resolve isso via TAB/nChat, que fazem sua propria expansao de PAPI). */
    private String applyPlaceholders(Player player, String text) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    private Component safeDeserialize(Player player, String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) {
            return Component.empty();
        }
        String resolved = applyPlaceholders(player, miniMessage);
        Component parsed;
        try {
            parsed = MM.deserialize(resolved);
        } catch (Exception e) {
            return Component.text(MM.stripTags(resolved));
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(parsed);
        if (LEAKED_TAG.matcher(plain).find()) {
            return Component.text(MM.stripTags(resolved));
        }
        return parsed;
    }

    // ================================================================================
    // PACKETS (PacketEvents) - sem NMS, sem reflection em campo interno do Paper.
    // ================================================================================

    private void sendSpawnPacket(Player viewer, FloatingTag tag) {
        // Y aqui so importa no frame antes de montar - depois do Set Passengers o
        // cliente reposiciona sozinho no ponto de montagem padrao (perto da cabeca).
        // A altura de verdade e controlada por DATA_TRANSLATION no metadata abaixo.
        Player owner = tag.owner();
        Location location = new Location(
                owner.getLocation().getX(),
                owner.getLocation().getY(),
                owner.getLocation().getZ(),
                0f, 0f
        );
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                tag.entityId(), tag.entityUuid(),
                tag.isItem() ? EntityTypes.ITEM_DISPLAY : EntityTypes.TEXT_DISPLAY,
                location, 0f, 0, null
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawn);
    }

    private void sendMountPacket(Player viewer, Player vehicle, FloatingTag passenger) {
        WrapperPlayServerSetPassengers mount = new WrapperPlayServerSetPassengers(
                vehicle.getEntityId(), new int[]{ passenger.entityId() }
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, mount);
    }

    private void sendMetadataPacket(Player viewer, FloatingTag tag) {
        // offset/scale sao ADITIVOS acima do ponto de montagem padrao (perto do topo da
        // hitbox do player). Item e texto tem valores proprios no config (renderizam em
        // tamanhos-base diferentes) - calibrar vendo no jogo.
        List<EntityData<?>> values = new ArrayList<>();

        if (tag.isItem()) {
            float scale = (float) config.floatingItemScale();
            values.add(new EntityData<>(DATA_TRANSLATION, EntityDataTypes.VECTOR3F,
                    new Vector3f(0f, (float) config.floatingItemOffsetY(), 0f)));
            values.add(new EntityData<>(DATA_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
            values.add(new EntityData<>(DATA_BILLBOARD_CONSTRAINTS, EntityDataTypes.BYTE, BILLBOARD_CENTER));
            values.add(new EntityData<>(DATA_ITEM, EntityDataTypes.ITEMSTACK, tag.item()));
            values.add(new EntityData<>(DATA_DISPLAY_TYPE, EntityDataTypes.BYTE, (byte) config.floatingItemDisplayType()));
        } else {
            int flags = config.floatingTagSeeThrough() ? STYLE_FLAG_SEE_THROUGH : 0;
            float scale = (float) config.floatingTagScale();
            values.add(new EntityData<>(DATA_TRANSLATION, EntityDataTypes.VECTOR3F,
                    new Vector3f(0f, (float) config.floatingTagOffsetY(), 0f)));
            values.add(new EntityData<>(DATA_SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
            values.add(new EntityData<>(DATA_BILLBOARD_CONSTRAINTS, EntityDataTypes.BYTE, BILLBOARD_CENTER));
            values.add(new EntityData<>(DATA_TEXT, EntityDataTypes.ADV_COMPONENT, tag.text()));
            values.add(new EntityData<>(DATA_LINE_WIDTH, EntityDataTypes.INT, config.floatingTagLineWidth()));
            values.add(new EntityData<>(DATA_BACKGROUND_COLOR, EntityDataTypes.INT, config.floatingTagBackgroundColor()));
            values.add(new EntityData<>(DATA_TEXT_OPACITY, EntityDataTypes.BYTE, (byte) config.floatingTagTextOpacity()));
            values.add(new EntityData<>(DATA_STYLE_FLAGS, EntityDataTypes.BYTE, (byte) flags));
        }

        WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata(tag.entityId(), values);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadata);
    }

    private void sendDestroyPacket(Player viewer, FloatingTag tag) {
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(tag.entityId());
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroy);
    }
}
