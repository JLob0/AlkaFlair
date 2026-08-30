package com.alkacode.flair.floating;

import com.alkacode.flair.manager.FlairPlayerDataManager;
import com.alkacode.flair.model.PlayerFlairData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/**
 * Lifecycle da tag flutuante fora do fluxo normal de equipar/desequipar (que ja e
 * coberto direto em TagService/MedalService): entrada no servidor (mostrar tags
 * alheias + publicar a propria), troca de mundo e teleport (evita TextDisplay
 * orfao pros jogadores do mundo antigo / tag que nao aparece pra quem entrou em
 * range so depois do TP) e saida do servidor (evita tag orfa em disconnect abrupto).
 */
public final class FloatingTagListener implements Listener {

    private final Plugin plugin;
    private final FloatingTagManager floatingTagManager;
    private final FlairPlayerDataManager dataManager;

    public FloatingTagListener(Plugin plugin, FloatingTagManager floatingTagManager, FlairPlayerDataManager dataManager) {
        this.plugin = plugin;
        this.floatingTagManager = floatingTagManager;
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // delay pro client terminar de carregar E pro load assincrono de PlayerFlairData
        // (FlairPlayerDataManager#onJoin, disparado pelo PlayerJoinListener) terminar.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerFlairData data = dataManager.get(player.getUniqueId());
            if (data != null) {
                floatingTagManager.onPlayerReady(player, data);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // broadcast de destroy pros viewers + limpa cache local - ver javadoc de
        // FloatingTagManager#removeLocal (nao da pra confiar em disconnect abrupto).
        floatingTagManager.removeLocal(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerFlairData data = dataManager.get(player.getUniqueId());
        if (data != null) {
            floatingTagManager.relocate(player, data);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return; // troca de mundo e coberta por onWorldChange, ja com o mundo trocado
        }
        Player player = event.getPlayer();
        // roda 1 tick depois: durante o proprio evento player.getLocation() ainda
        // reporta a posicao ANTIGA (o teleport so acontece de fato apos os listeners
        // rodarem), e o canReceive() de FloatingTagManager depende da posicao real
        // pra decidir quem esta perto o suficiente pra ver a tag flutuante.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerFlairData data = dataManager.get(player.getUniqueId());
            if (data != null) {
                floatingTagManager.relocate(player, data);
            }
        });
    }
}
