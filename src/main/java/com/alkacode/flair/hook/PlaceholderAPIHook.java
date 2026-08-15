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

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * %alkaflair_tag%, %alkaflair_tag_prefix%, %alkaflair_tag_suffix%, %alkaflair_tag_id%,
 * %alkaflair_tag_count%, %alkaflair_has_tag_<id>%, %alkaflair_can_afford_<id>%,
 * %alkaflair_medals%, %alkaflair_medal_count%, %alkaflair_medal_slots%,
 * %alkaflair_has_medal_<id>%. Identificador "alkaflair" - grep confirmou nao colidir
 * com nenhuma expansion existente na rede. prefix/suffix priorizam ownPrefix/
 * ownSuffix (setado por admin via /tags setar) sobre a tag equipada, quando setados.
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

        return switch (lower) {
            case "tag" -> data != null ? toLegacy(equippedTagDisplay(data)) : "";
            case "tag_id" -> data != null && data.equippedTagId() != null ? data.equippedTagId() : "none";
            case "tag_prefix" -> data != null ? toLegacy(resolvedPrefix(data)) : "";
            case "tag_suffix" -> data != null ? toLegacy(resolvedSuffix(data)) : "";
            case "tag_count" -> String.valueOf(data != null ? data.unlockedTagIds().size() : 0);
            case "medals" -> data != null ? toLegacy(equippedMedalsConcat(data)) : "";
            case "medal_count" -> String.valueOf(data != null ? data.unlockedMedalIds().size() : 0);
            case "medal_slots" -> data != null ? data.equippedMedalIds().size() + "/" + data.maxMedalSlots() : "0/0";
            default -> null;
        };
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
