package com.alkacode.flair;

import com.alkacode.core.api.AlkaAPI;
import com.alkacode.core.plugin.AlkaPlugin;
import com.alkacode.economy.AlkaEconomyPlugin;
import com.alkacode.flair.api.AlkaFlairAPI;
import com.alkacode.flair.api.AlkaFlairAPIProvider;
import com.alkacode.flair.command.MedalCommand;
import com.alkacode.flair.command.TagCommand;
import com.alkacode.flair.config.FlairConfig;
import com.alkacode.flair.hook.LuckPermsHook;
import com.alkacode.flair.hook.PlaceholderAPIHook;
import com.alkacode.flair.listener.PlayerJoinListener;
import com.alkacode.flair.listener.VoucherListener;
import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.medal.MedalManager;
import com.alkacode.flair.service.FlairEconomyService;
import com.alkacode.flair.service.MedalService;
import com.alkacode.flair.service.TagService;
import com.alkacode.flair.storage.FlairRepository;
import com.alkacode.flair.tag.TagManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

/**
 * Tags cosmeticas (prefixo/sufixo equipavel, compravel) + medalhas (badges
 * multi-equip por permissao) num unico plugin - as duas coisas sao a mesma ideia
 * (identidade cosmetica equipavel), sempre exibidas juntas na mesma linha de tab/
 * chat, sem motivo real pra ficarem em plugins separados com uma ponte de
 * reflection entre elas. Ver ALKANETWORKING.md/memoria project-alkaflair pro
 * racional completo.
 */
public final class AlkaFlairPlugin extends AlkaPlugin {

    private FlairConfig config;
    private TagManager tagManager;
    private MedalManager medalManager;
    private FlairPlayerDataManager dataManager;
    private TagService tagService;
    private MedalService medalService;

    @Override
    protected void onPluginEnable() {
        AlkaAPI api = getAlkaAPI();

        if (!(getServer().getPluginManager().getPlugin("AlkaEconomy") instanceof AlkaEconomyPlugin alkaEconomy)) {
            getLogger().severe("AlkaEconomy e obrigatorio e nao foi encontrado. Desativando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        config = new FlairConfig(this);
        tagManager = new TagManager(this);
        medalManager = new MedalManager(this);

        FlairRepository repository = new FlairRepository(api.getDatabase(), getLogger());
        dataManager = new FlairPlayerDataManager(repository, api.getScheduler(), config.medalsDefaultMaxSlots());

        FlairEconomyService economyService = new FlairEconomyService(alkaEconomy.getEconomyManager());
        LuckPermsHook luckPermsHook = new LuckPermsHook(getLogger());

        tagService = new TagService(tagManager, dataManager, economyService, luckPermsHook, config);
        medalService = new MedalService(medalManager, dataManager, luckPermsHook, config.medalsGrantPermissionOnUnlock());

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(dataManager), this);
        getServer().getPluginManager().registerEvents(
                new VoucherListener(this, tagManager, medalManager, tagService, medalService, dataManager), this);

        TagCommand tagCommand = new TagCommand(this, tagService, economyService, dataManager, config);
        getCommand("tags").setExecutor(tagCommand);
        getCommand("tags").setTabCompleter(tagCommand);

        MedalCommand medalCommand = new MedalCommand(this, medalService, dataManager, config);
        getCommand("medals").setExecutor(medalCommand);
        getCommand("medals").setTabCompleter(medalCommand);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(tagManager, medalManager, dataManager, economyService).register();
        }

        getServer().getServicesManager().register(AlkaFlairAPI.class,
                new AlkaFlairAPIProvider(dataManager, repository, tagManager, medalManager, api.getScheduler(), luckPermsHook),
                this, ServicePriority.Normal);

        // cobre /reload do servidor - quem ja estava online nao dispara PlayerJoinEvent de novo
        for (Player player : getServer().getOnlinePlayers()) {
            dataManager.onJoin(player.getUniqueId());
        }

        getLogger().info("AlkaFlair habilitado (" + tagManager.all().size() + " tags, "
                + medalManager.all().size() + " medalhas).");
    }

    @Override
    protected void onPluginDisable() {
        // Sem estado de sessao acumulado - toda mutacao ja e write-through pro banco
        // na hora (ver FlairPlayerDataManager), nada a descarregar aqui.
    }
}
