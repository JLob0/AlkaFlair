package com.alkacode.flair.command;

import com.alkacode.flair.config.FlairConfig;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.medal.Medal;
import com.alkacode.flair.model.PlayerFlairData;
import com.alkacode.flair.service.MedalService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MedalCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMANDS = List.of("info", "add", "del", "give", "setslots", "reload");

    private final JavaPlugin plugin;
    private final MedalService medalService;
    private final FlairPlayerDataManager dataManager;
    private final FlairConfig config;

    public MedalCommand(JavaPlugin plugin, MedalService medalService, FlairPlayerDataManager dataManager, FlairConfig config) {
        this.plugin = plugin;
        this.medalService = medalService;
        this.dataManager = dataManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return openMenu(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender, args);
            case "add" -> add(sender, args);
            case "del" -> del(sender, args);
            case "give" -> give(sender, args);
            case "setslots" -> setSlots(sender, args);
            case "reload" -> reload(sender);
            default -> {
                send(sender, config.message("usage").replace("<usage>", "/medals <info|add|del|give|setslots|reload>"));
                yield true;
            }
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
        new com.alkacode.flair.gui.MedalsMenu(plugin, player, medalService, dataManager).open();
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.medals.info")) {
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
        send(sender, "<gray>Medalhas equipadas: <white>" + data.equippedMedalIds().size() + "/" + data.maxMedalSlots());
        send(sender, "<gray>Medalhas desbloqueadas: <white>" + data.unlockedMedalIds().size());
        return true;
    }

    private boolean add(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.medals.add")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/medals add <jogador> <medalha>"));
            return true;
        }
        Medal medal = medalService.medalManager().get(args[2]);
        if (medal == null) {
            send(sender, config.message("medal-not-found").replace("<id>", args[2]));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        if (data == null) {
            data = dataManager.loadOffline(target.getUniqueId());
        }
        medalService.unlock(target.getUniqueId(), data, medal);
        send(sender, config.message("medal-added").replace("<id>", medal.id()).replace("<player>", args[1]));
        return true;
    }

    private boolean del(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.medals.del")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/medals del <jogador> <medalha>"));
            return true;
        }
        Medal medal = medalService.medalManager().get(args[2]);
        if (medal == null) {
            send(sender, config.message("medal-not-found").replace("<id>", args[2]));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        boolean online = data != null;
        if (!online) {
            data = dataManager.loadOffline(target.getUniqueId());
        }
        medalService.revoke(data, medal);
        send(sender, config.message("medal-removed").replace("<id>", medal.id()).replace("<player>", args[1]));
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.medals.give")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/medals give <jogador> <medalha> [qtd]"));
            return true;
        }
        Medal medal = medalService.medalManager().get(args[2]);
        Player target = Bukkit.getPlayer(args[1]);
        if (medal == null) {
            send(sender, config.message("medal-not-found").replace("<id>", args[2]));
            return true;
        }
        if (target == null) {
            send(sender, config.message("player-not-found"));
            return true;
        }
        int amount = args.length >= 4 ? parseIntOr(args[3], 1) : 1;
        var voucher = com.alkacode.flair.util.VoucherBuilder.medalVoucher(medal, amount);
        var overflow = target.getInventory().addItem(voucher);
        overflow.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        send(sender, "<green>Voucher da medalha <white>" + medal.id() + " <green>entregue para <white>" + target.getName() + "<green>.");
        return true;
    }

    private boolean setSlots(CommandSender sender, String[] args) {
        if (!sender.hasPermission("alkaflair.medals.setslots")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, config.message("usage").replace("<usage>", "/medals setslots <jogador> <n>"));
            return true;
        }
        int slots = parseIntOr(args[2], -1);
        if (slots < 0) {
            send(sender, config.message("usage").replace("<usage>", "/medals setslots <jogador> <n>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        PlayerFlairData data = dataManager.get(target.getUniqueId());
        if (data == null) {
            data = dataManager.loadOffline(target.getUniqueId());
        }
        medalService.setMaxSlots(data, slots);
        send(sender, config.message("medal-slots-set").replace("<player>", args[1]).replace("<amount>", String.valueOf(slots)));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("alkaflair.medals.reload")) {
            send(sender, config.message("no-permission"));
            return true;
        }
        config.reload();
        medalService.medalManager().load();
        send(sender, config.message("reload"));
        return true;
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
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean needsPlayer = List.of("info", "add", "del", "give", "setslots").contains(sub);
        if (args.length == 2 && needsPlayer) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        boolean needsMedal = List.of("add", "del", "give").contains(sub);
        if (args.length == 3 && needsMedal) {
            return filter(List.copyOf(medalService.medalManager().all().keySet()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
