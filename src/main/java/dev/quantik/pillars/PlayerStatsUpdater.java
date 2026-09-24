package dev.quantik.pillars;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerStatsUpdater implements DatabaseUpdateListener {
    private final Pillars plugin;

    public PlayerStatsUpdater(Pillars plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onDatabaseUpdate(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player != null && player.isOnline()) {
            plugin.updatePersonalStats(player);
        }
    }
}
