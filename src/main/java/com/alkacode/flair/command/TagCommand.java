package com.alkacode.flair.command;

import com.alkacode.flair.config.FlairConfig;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.FlairEconomyService;
import com.alkacode.flair.service.TagService;
import com.alkacode.flair.tag.Tag;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /tags - menu no vazio; /tags <tag|alias> equipa direto sem abrir GUI (mesmo
 * padrao de quick-equip do LeafTags). Subcomandos admin nao suportados: "forcar"
 * (abrir menu remotamente em outro jogador) - deliberadamente fora do escopo desta
 * versao, facil de adicionar depois se pedido.
 */
public final class TagCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of(
            "info", "add", "del", "dar", "darpacote", "setar", "limpar", "equipar", "reload");

    private final JavaPlugin plugin;
    private final TagService tagService;
    private final FlairEconomyService economyService;
    private final FlairPlayerDataManager dataManager;
    private final FlairConfig config;

    public TagCommand(JavaPlugin plugin, TagService tagService, FlairEconomyService economyService,
                       FlairPlayerDataManager dataManager, FlairConfig config) {
        this.plugin = plugin;
        this.tagService = tagService;
        this.economyService = economyService;
        this.dataManager = dataManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return openMenu(sender);
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        return switch (first) {
            case "info" -> info(sender, args);
            case "add" -> add(sender, args);
            case "del" -> del(sender, args);
            case "dar" -> dar(sender, args);
            case "darpacote" -> darPacote(sender, args);
            case "setar" -> setar(sender, args);
            case "limpar" -> limpar(sender, args);
            case "equipar" -> equipar(sender, args);
            case "reload" -> reload(sender);
            default -> quickEquip(sender, args[0]);
        };
    }

    private boolean openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, config.message("player-only"));
            return true;
        }
        if (!dataManager.isLoaded(player.getUniqueId())) {
            send(sender, "<red>Seus dados ainda estao carregando, tente novamente em instantes.");
            return true;
        }
        new com.alkacode.flair.gui.TagsMenu(plugin, player, tagService, economyService, dataManager).open();
        return true;
    }

    private boolean quickEquip(CommandSender sender, String tagIdOrAlias) {
        if (!(sender instanceof Player player)) {
            send(sender, config.message("player-only"));
            return true;
        }
        Tag tag = tagService.tagManager().getByIdOrAlias(tagIdOrAlias);
        if (tag == null) {
            send(sender, config.message("tag-not-found").replace("<id>", tagIdOrAlias));
            return true;
        }
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            send(sender, "<red>Seus dados ainda estao carregando, tente novamente em instantes.");
            return true;
        }
        TagService.EquipResult result = tagService.equip(player, data, tag);
        switch (result) {
            case SUCCESS -> send(sender, config.message("tag-equipped").replace("<tag_display>", tag.display()));
            case NOT_UNLOCKED -> send(sender, config.message("tag-not-unlocked"));
            case COOLDOWN -> send(sender, config.message("tag-cooldown").replace("<seconds>",
                    String.valueOf(config.tagSwitchCooldownSeconds())));
            case NOT_LOADED -> send(sender, "<red>Seus dados ainda estao carregando, tente novamente em instantes.");
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.info")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        OfflinePlayer target = args.length >= 2 ? Bukkit.getOfflinePlayer(args[1])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            send(sender, config.message("player-only"));
            return true;
        }
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        if (data == null) {
            data = dataManager.loadOffline(target.getUniqueId());
        }
        send(sender, "<gray>Tag equipada: <white>" + (data.equippedTagId() != null ? data.equippedTagId() : "nenhuma"));
        send(sender, "<gray>Tags desbloqueadas: <white>" + data.unlockedTagIds().size());
        return true;
    }

    private boolean add(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.add")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/tags add <jogador> <tag>"));
            return true;
        }
        withTargetAndTag(sender, args[1], args[2], (uuid, data, tag) -> {
            tagService.unlock(uuid, data, tag);
            send(sender, config.message("tag-added").replace("<id>", tag.id()).replace("<player>", args[1]));
        });
        return true;
    }

    private boolean del(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.del")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/tags del <jogador> <tag>"));
            return true;
        }
        withTargetAndTag(sender, args[1], args[2], (uuid, data, tag) -> {
            tagService.revoke(data, tag);
            dataManager.savePlayerRow(data);
            send(sender, config.message("tag-removed").replace("<id>", tag.id()).replace("<player>", args[1]));
        });
        return true;
    }

    private boolean dar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.dar")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/tags dar <jogador> <tag> [qtd]"));
            return true;
        }
        Tag tag = tagService.tagManager().get(args[2]);
        Player target = Bukkit.getPlayer(args[1]);
        if (tag == null) {
            send(sender, config.message("tag-not-found").replace("<id>", args[2]));
            return true;
        }
        if (target == null) {
            send(sender, config.message("player-not-found"));
            return true;
        }
        int amount = args.length >= 4 ? parseIntOr(args[3], 1) : 1;
        var voucher = com.alkacode.flair.util.VoucherBuilder.tagVoucher(tag, amount);
        giveOrDrop(target, voucher);
        send(sender, "<green>Voucher da tag <white>" + tag.id() + " <green>entregue para <white>" + target.getName() + "<green>.");
        return true;
    }

    private boolean darPacote(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.darpacote")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/tags darpacote <jogador> <tag1,tag2,...>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            send(sender, config.message("player-not-found"));
            return true;
        }
        List<String> ids = List.of(args[2].split(","));
        var voucher = com.alkacode.flair.util.VoucherBuilder.tagPackVoucher(ids);
        giveOrDrop(target, voucher);
        send(sender, "<green>Pacote de tags entregue para <white>" + target.getName() + "<green>.");
        return true;
    }

    private boolean setar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.setar")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/tags setar <jogador> <prefixo> [sufixo]"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        boolean online = data != null;
        if (!online) {
            data = dataManager.loadOffline(target.getUniqueId());
        }
        String suffix = args.length >= 4 ? args[3] : "";
        tagService.setOwn(data, args[2], suffix);
        if (!online) {
            dataManager.savePlayerRow(data);
        }
        send(sender, config.message("tag-own-set").replace("<player>", args[1]));
        return true;
    }

    private boolean limpar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.limpar")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 2) {
            send(sender, config.message("usage").replace("<usage>", "/tags limpar <jogador>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        boolean online = data != null;
        if (!online) {
            data = dataManager.loadOffline(target.getUniqueId());
        }
        tagService.clearOwn(data);
        if (!online) {
            dataManager.savePlayerRow(data);
        }
        send(sender, config.message("tag-own-cleared").replace("<player>", args[1]));
        return true;
    }

    private boolean equipar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.tags.equipar")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/tags equipar <jogador> <tag>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        Tag tag = tagService.tagManager().get(args[2]);
        if (target == null) {
            send(sender, config.message("player-not-found"));
            return true;
        }
        if (tag == null) {
            send(sender, config.message("tag-not-found").replace("<id>", args[2]));
            return true;
        }
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        if (data == null) {
            send(sender, "<red>Dados do jogador ainda carregando.");
            return true;
        }
        tagService.unlock(target.getUniqueId(), data, tag);
        tagService.equip(target, data, tag);
        send(sender, "<green>Tag <white>" + tag.id() + " <green>equipada em <white>" + target.getName() + "<green>.");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("alkaflair.tags.reload")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        config.reload();
        com.alkacode.flair.config.MenuConfig.getInstance().reload();
        tagService.tagManager().load();
        send(sender, config.message("reload"));
        return true;
    }

    private interface TargetTagAction {
        void run(java.util.UUID uuid, PlayerFlairData data, Tag tag);
    }

    private void withTargetAndTag(CommandSender sender, String playerName, String tagId, TargetTagAction action) {
        Tag tag = tagService.tagManager().get(tagId);
        if (tag == null) {
            send(sender, config.message("tag-not-found").replace("<id>", tagId));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        if (data == null) {
            data = dataManager.loadOffline(target.getUniqueId());
        }
        // unlock()/revoke() ja persistem o set desbloqueado direto no repositorio (via
        // FlairPlayerDataManager quando online, ou seriam perdidos quando offline - por
        // isso add/del chamam addUnlockedTag/removeUnlockedTag do repositorio via o
        // service, nao dependem do cache pra jogador offline).
        action.run(target.getUniqueId(), data, tag);
    }

    private void giveOrDrop(Player target, org.bukkit.inventory.ItemStack item) {
        var overflow = target.getInventory().addItem(item);
        overflow.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
    }

    private int parseIntOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(MM.deserialize(config.prefix() + miniMessage));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Server server = plugin.getServer();
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(SUBCOMMANDS);
            options.addAll(tagService.tagManager().all().keySet());
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean needsPlayer = List.of("info", "add", "del", "dar", "darpacote", "setar", "limpar", "equipar").contains(sub);
        if (args.length == 2 && needsPlayer) {
            return filter(server.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        boolean needsTag = List.of("add", "del", "dar", "equipar").contains(sub);
        if (args.length == 3 && needsTag) {
            return filter(List.copyOf(tagService.tagManager().all().keySet()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
