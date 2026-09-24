package dev.quantik.pillars;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class PlayerStatsPlaceholder extends PlaceholderExpansion {
    private final DatabaseManager dbManager;

    public PlayerStatsPlaceholder(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public String getIdentifier() {
        return "playerstats";
    }

    @Override
    public String getAuthor() {
        return "1_QUANTIK_1";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        switch (params) {
            case "wins":
                return String.valueOf(dbManager.getWins(player.getName()));
            case "losses":
                return String.valueOf(dbManager.getLosses(player.getName()));
            case "kills":
                return String.valueOf(dbManager.getKills(player.getName()));
            case "win_loss_ratio":
                return String.format("%.2f", dbManager.getWinLossRatio(player.getName()));
            default:
                return null;
        }
    }
}

