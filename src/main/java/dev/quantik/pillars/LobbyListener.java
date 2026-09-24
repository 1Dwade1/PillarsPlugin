package dev.quantik.pillars;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LobbyListener implements Listener {
    private final LobbyScoreboard lobbyScoreboard;

    public LobbyListener(LobbyScoreboard lobbyScoreboard) {
        this.lobbyScoreboard = lobbyScoreboard;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        lobbyScoreboard.applyToPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lobbyScoreboard.clear(event.getPlayer());
    }
}
