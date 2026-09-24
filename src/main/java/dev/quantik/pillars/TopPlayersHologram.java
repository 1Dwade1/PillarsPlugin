package dev.quantik.pillars;

import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI;
import me.filoghost.holographicdisplays.api.hologram.Hologram;
import me.filoghost.holographicdisplays.api.hologram.line.TextHologramLine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TopPlayersHologram {
    private final JavaPlugin plugin;
    private final DatabaseManager dbManager;
    private Hologram hologram;

    public TopPlayersHologram(JavaPlugin plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    public void createTop10Hologram(Location location) {
        if (hologram != null) {
            hologram.delete();
        }

        HolographicDisplaysAPI api = HolographicDisplaysAPI.get(plugin);
        hologram = api.createHologram(location);

        hologram.getLines().appendText("§6§lТоп-10 игроков:");

        List<String> topPlayers = dbManager.getTopPlayers();

        int rank = 1;
        for (String playerLine : topPlayers) {
            hologram.getLines().appendText("§e" + rank + ". " + playerLine);
            rank++;
        }
        Pillars.top10hologram = hologram;
    }
}
