package com.alkacode.flair.listener;

import com.alkacode.flair.manager.FlairPlayerDataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerJoinListener implements Listener {

    private final FlairPlayerDataManager dataManager;

    public PlayerJoinListener(FlairPlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        dataManager.onJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dataManager.onQuit(event.getPlayer().getUniqueId());
    }
}
