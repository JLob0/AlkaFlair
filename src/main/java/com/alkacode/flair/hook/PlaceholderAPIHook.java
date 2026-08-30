package com.alkacode.flair.hook;

import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.medal.MedalManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.FlairEconomyService;
import com.alkacode.flair.tag.Tag;
import com.alkacode.flair.tag.TagManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * %alkaflair_tag%, %alkaflair_tag_prefix%, %alkaflair_tag_suffix%, %alkaflair_tag_id%,
 * %alkaflair_tag_count%, %alkaflair_tag_description%, %alkaflair_tag_rarity%,
 * %alkaflair_tag_source%, %alkaflair_tag_obtained%, %alkaflair_has_tag_<id>%,
 * %alkaflair_can_afford_<id>%, %alkaflair_medals%, %alkaflair_medal_count%,
 * %alkaflair_medal_slots%, %alkaflair_medal_<slot>%, %alkaflair_medal_<slot>_description%,
 * %alkaflair_medal_<slot>_rarity%, %alkaflair_medal_<slot>_source%,
 * %alkaflair_medal_<slot>_obtained% (dd/MM/yyyy HH:mm, vazio se desbloqueado antes de
 * 21/08 - sem historico pra unlocks anteriores a coluna unlocked_epoch existir),
 * %alkaflair_medal_<slot>_exclusive% ("SIM"/"Não" - so nas medalhas, nas tags o campo
 * `obtainable` ja cobre a mesma ideia, exclusive la seria redundante)
 * (slot = posicao 1-based entre as medalhas EQUIPADAS, ordenado por Medal#position -
 * mesma ordem de %alkaflair_medals%), %alkaflair_has_medal_<id>%. Identificador
 * "alkaflair" - grep confirmou nao colidir com nenhuma expansion existente na rede.
 * prefix/suffix priorizam ownPrefix/ownSuffix (setado por admin via /tags setar) sobre
 * a tag equipada, quando setados. description/rarity/source NAO tem esse fallback -
 * sempre vem da tag/medalha configurada em tags.yml/medals.yml (vazio se nao setado).
 */
public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private final TagManager tagManager;
    private final MedalManager medalManager;
    private final FlairPlayerDataManager dataManager;
    private final FlairEconomyService economyService;

    public PlaceholderAPIHook(TagManager tagManager, MedalManager medalManager, FlairPlayerDataManager dataManager,
                               FlairEconomyService economyService) {
        this.tagManager = tagManager;
        this.medalManager = medalManager;
        this.dataManager = dataManager;
        this.economyService = economyService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "alkaflair";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MestreDEV";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params == null) {
            return "";
        }
        String lower = params.toLowerCase(Locale.ROOT);

        if (lower.startsWith("has_tag_")) {
            return String.valueOf(player != null && hasTag(player, lower.substring("has_tag_".length())));
        }
        if (lower.startsWith("can_afford_")) {
            return String.valueOf(player != null && canAfford(player, lower.substring("can_afford_".length())));
        }
        if (lower.startsWith("has_medal_")) {
            return String.valueOf(player != null && hasMedal(player, lower.substring("has_medal_".length())));
        }

        if (player == null) {
            return "";
        }
        PlayerFlairData data = dataManager.get(player.getUniqueId());

        if (lower.startsWith("medal_")) {
            String slotResult = medalSlotPlaceholder(player, data, lower.substring("medal_".length()));
            if (slotResult != null) {
                return slotResult;
            }
        }

        return switch (lower) {
            case "tag" -> data != null ? toLegacy(player, equippedTagDisplay(data)) : "";
            case "tag_id" -> data != null && data.equippedTagId() != null ? data.equippedTagId() : "none";
            case "tag_prefix" -> data != null ? toLegacy(player, resolvedPrefix(data)) : "";
            case "tag_suffix" -> data != null ? toLegacy(player, resolvedSuffix(data)) : "";
            case "tag_count" -> String.valueOf(data != null ? data.unlockedTagIds().size() : 0);
            case "tag_description" -> data != null ? toLegacy(player, equippedTagField(data, Tag::description)) : "";
            case "tag_rarity" -> data != null ? toLegacy(player, equippedTagField(data, Tag::rarity)) : "";
            case "tag_source" -> data != null ? toLegacy(player, equippedTagField(data, Tag::source)) : "";
            case "tag_obtained" -> data != null ? formatObtained(data.unlockedTagEpochs().get(data.equippedTagId())) : "";
            case "medals" -> data != null ? toLegacy(player, equippedMedalsConcat(data)) : "";
            case "medal_count" -> String.valueOf(data != null ? data.unlockedMedalIds().size() : 0);
            case "medal_slots" -> data != null ? data.equippedMedalIds().size() + "/" + data.maxMedalSlots() : "0/0";
            default -> null;
        };
    }

    /** %alkaflair_medal_<slot>%, _description, _rarity, _source - slot e 1-based, na mesma
     * ordem (por Medal#position) usada por equippedMedalsConcat(). Retorna null se o sufixo
     * nao bater com esse padrao (deixa o switch principal seguir/retornar null). */
    private String medalSlotPlaceholder(Player player, PlayerFlairData data, String suffix) {
        String[] parts = suffix.split("_", 2);
        int slot;
        try {
            slot = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (data == null) {
            return "";
        }
        List<Medal> equipped = equippedMedalsOrdered(data);
        if (slot < 1 || slot > equipped.size()) {
            return "";
        }
        Medal medal = equipped.get(slot - 1);
        if (parts.length == 1) {
            return toLegacy(player, medal.display());
        }
        return switch (parts[1]) {
            case "description" -> toLegacy(player, String.join(" ", medal.description()));
            case "rarity" -> toLegacy(player, medal.rarity());
            case "source" -> toLegacy(player, medal.source());
            case "obtained" -> formatObtained(data.unlockedMedalEpochs().get(medal.id()));
            case "exclusive" -> medal.exclusive() ? "§a§lSIM" : "§cNão";
            default -> null;
        };
    }

    private static final java.time.format.DateTimeFormatter OBTAINED_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** epoch em segundos -> "dd/MM/yyyy HH:mm". Null/0 = nunca registrado (unlock de
     * antes da coluna unlocked_epoch existir, 21/08) - vazio, sem inventar data. */
    private String formatObtained(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds <= 0) {
            return "";
        }
        return OBTAINED_FORMAT.format(java.time.Instant.ofEpochSecond(epochSeconds)
                .atZone(java.time.ZoneId.systemDefault()));
    }

    private List<Medal> equippedMedalsOrdered(PlayerFlairData data) {
        return data.equippedMedalIds().stream()
                .map(medalManager::get)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(Medal::position))
                .collect(Collectors.toList());
    }

    private String equippedTagField(PlayerFlairData data, java.util.function.Function<Tag, Object> field) {
        if (!data.tagEnabled() || data.equippedTagId() == null) {
            return "";
        }
        Tag tag = tagManager.get(data.equippedTagId());
        if (tag == null) {
            return "";
        }
        Object value = field.apply(tag);
        if (value instanceof List<?> lines) {
            return String.join(" ", (List<String>) lines);
        }
        return String.valueOf(value);
    }

    private boolean hasTag(Player player, String tagId) {
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            return false;
        }
        if (data.unlockedTagIds().contains(tagId)) {
            return true;
        }
        Tag tag = tagManager.get(tagId);
        return tag != null && tag.hasPermissionGate() && player.hasPermission(tag.permission());
    }

    private boolean canAfford(Player player, String tagId) {
        Tag tag = tagManager.get(tagId);
        if (tag == null || !tag.purchasable()) {
            return false;
        }
        return economyService.has(player.getUniqueId(), tag.priceCurrency(), tag.priceAmount());
    }

    private boolean hasMedal(Player player, String medalId) {
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            return false;
        }
        if (data.unlockedMedalIds().contains(medalId)) {
            return true;
        }
        Medal medal = medalManager.get(medalId);
        return medal != null && medal.permission() != null && !medal.permission().isBlank()
                && player.hasPermission(medal.permission());
    }

    private String equippedTagDisplay(PlayerFlairData data) {
        if (!data.tagEnabled() || data.equippedTagId() == null) {
            return "";
        }
        Tag tag = tagManager.get(data.equippedTagId());
        return tag != null ? safeMiniMessage(tag.display()) : "";
    }

    private String resolvedPrefix(PlayerFlairData data) {
        if (!data.tagEnabled()) {
            return "";
        }
        if (data.ownPrefix() != null && !data.ownPrefix().isBlank()) {
            return safeMiniMessage(data.ownPrefix());
        }
        if (data.equippedTagId() == null) {
            return "";
        }
        Tag tag = tagManager.get(data.equippedTagId());
        return tag != null ? safeMiniMessage(tag.prefix()) : "";
    }

    private String resolvedSuffix(PlayerFlairData data) {
        if (!data.tagEnabled()) {
            return "";
        }
        if (data.ownSuffix() != null && !data.ownSuffix().isBlank()) {
            return safeMiniMessage(data.ownSuffix());
        }
        if (data.equippedTagId() == null) {
            return "";
        }
        Tag tag = tagManager.get(data.equippedTagId());
        return tag != null ? safeMiniMessage(tag.suffix()) : "";
    }

    // ATENCAO - mesmo bug do AlkaClans (ver TagFormatter#resolvedTagFormatted):
    // dado ja salvo malformado (ex "<gradient:>", cor vazia) vazava cru pro
    // nChat/TAB porque o MiniMessage nao lanca excecao pra tag com argumento
    // invalido, so trata como texto literal. TagCommand#setar agora valida antes
    // de salvar, mas dado ja corrompido antes desse fix precisa se autocorrigir
    // aqui na leitura - fallback pro texto sem formatacao nenhuma, nunca a tag crua.
    private static final java.util.regex.Pattern LEAKED_TAG =
            java.util.regex.Pattern.compile("<[a-zA-Z_][a-zA-Z0-9_]*(:[^<>]*)?>");

    private String safeMiniMessage(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) {
            return "";
        }
        net.kyori.adventure.text.Component parsed;
        try {
            parsed = MiniMessage.miniMessage().deserialize(miniMessage);
        } catch (Exception e) {
            return MiniMessage.miniMessage().stripTags(miniMessage);
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(parsed);
        if (LEAKED_TAG.matcher(plain).find()) {
            return MiniMessage.miniMessage().stripTags(miniMessage);
        }
        return miniMessage;
    }

    private String equippedMedalsConcat(PlayerFlairData data) {
        return data.equippedMedalIds().stream()
                .map(medalManager::get)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(Medal::position))
                .map(Medal::display)
                .collect(Collectors.joining(" "));
    }

    // character(SECTION_CHAR) (nao legacyAmpersand()) + useUnusualXRepeatedCharacterHexFormat():
    // consumidores (TAB, nosso proprio placar via AlkaEssentials) so entendem codigo real "§",
    // nunca texto "&" cru - texto "&" ja causou o placar quebrar de verdade uma vez (nome de
    // mina com gradient no AlkaMines, corrigido v1.0.83) - mesma classe de bug aqui.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /** Converte o display (escrito em MiniMessage nas configs) pra codigos legado reais (§).
     * Vazio vira vazio. O §r final impede a cor/estilo da tag de vazar pro que vier depois
     * (suffix/mensagem).
     *
     * <p>Resolve qualquer %placeholder% aninhado ANTES do MiniMessage (ex: uma tag com
     * "%img_rank_knight%" no prefix, ItemsAdder) - o proprio PAPI so resolve
     * %alkaflair_tag_prefix% numa passada so, nao re-escaneia o valor QUE ELE MESMO
     * devolveu procurando outro %placeholder% dentro (bug real 30/08: icone aparecia
     * cru no chat via nChat mesmo a tag flutuante 3D ja resolvendo certo - o
     * FloatingTagManager faz essa mesma resolucao, so que numa classe separada).</p> */
    private String toLegacy(Player player, String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) {
            return "";
        }
        String resolved = miniMessage.indexOf('%') >= 0
                ? me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, miniMessage)
                : miniMessage;
        return LEGACY.serialize(MiniMessage.miniMessage().deserialize(resolved)) + "§r";
    }
}
