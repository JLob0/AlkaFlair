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
 * %alkaflair_tag_source%, %alkaflair_has_tag_<id>%, %alkaflair_can_afford_<id>%,
 * %alkaflair_medals%, %alkaflair_medal_count%, %alkaflair_medal_slots%,
 * %alkaflair_medal_<slot>%, %alkaflair_medal_<slot>_description%,
 * %alkaflair_medal_<slot>_rarity%, %alkaflair_medal_<slot>_source%
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
            String slotResult = medalSlotPlaceholder(data, lower.substring("medal_".length()));
            if (slotResult != null) {
                return slotResult;
            }
        }

        return switch (lower) {
            case "tag" -> data != null ? toLegacy(equippedTagDisplay(data)) : "";
            case "tag_id" -> data != null && data.equippedTagId() != null ? data.equippedTagId() : "none";
            case "tag_prefix" -> data != null ? toLegacy(resolvedPrefix(data)) : "";
            case "tag_suffix" -> data != null ? toLegacy(resolvedSuffix(data)) : "";
            case "tag_count" -> String.valueOf(data != null ? data.unlockedTagIds().size() : 0);
            case "tag_description" -> data != null ? toLegacy(equippedTagField(data, Tag::description)) : "";
            case "tag_rarity" -> data != null ? toLegacy(equippedTagField(data, Tag::rarity)) : "";
            case "tag_source" -> data != null ? toLegacy(equippedTagField(data, Tag::source)) : "";
            case "medals" -> data != null ? toLegacy(equippedMedalsConcat(data)) : "";
            case "medal_count" -> String.valueOf(data != null ? data.unlockedMedalIds().size() : 0);
            case "medal_slots" -> data != null ? data.equippedMedalIds().size() + "/" + data.maxMedalSlots() : "0/0";
            default -> null;
        };
    }

    /** %alkaflair_medal_<slot>%, _description, _rarity, _source - slot e 1-based, na mesma
     * ordem (por Medal#position) usada por equippedMedalsConcat(). Retorna null se o sufixo
     * nao bater com esse padrao (deixa o switch principal seguir/retornar null). */
    private String medalSlotPlaceholder(PlayerFlairData data, String suffix) {
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
            return toLegacy(medal.display());
        }
        return switch (parts[1]) {
            case "description" -> toLegacy(String.join(" ", medal.description()));
            case "rarity" -> toLegacy(medal.rarity());
            case "source" -> toLegacy(medal.source());
            default -> null;
        };
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
        return tag != null ? tag.display() : "";
    }

    private String resolvedPrefix(PlayerFlairData data) {
        if (!data.tagEnabled()) {
            return "";
        }
        if (data.ownPrefix() != null && !data.ownPrefix().isBlank()) {
            return data.ownPrefix();
        }
        if (data.equippedTagId() == null) {
            return "";
        }
        Tag tag = tagManager.get(data.equippedTagId());
        return tag != null ? tag.prefix() : "";
    }

    private String resolvedSuffix(PlayerFlairData data) {
        if (!data.tagEnabled()) {
            return "";
        }
        if (data.ownSuffix() != null && !data.ownSuffix().isBlank()) {
            return data.ownSuffix();
        }
        if (data.equippedTagId() == null) {
            return "";
        }
        Tag tag = tagManager.get(data.equippedTagId());
        return tag != null ? tag.suffix() : "";
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
     * (suffix/mensagem). */
    private String toLegacy(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) {
            return "";
        }
        return LEGACY.serialize(MiniMessage.miniMessage().deserialize(miniMessage)) + "§r";
    }
}
