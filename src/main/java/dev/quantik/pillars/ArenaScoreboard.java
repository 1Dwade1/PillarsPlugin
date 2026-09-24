package dev.quantik.pillars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class ArenaScoreboard {
    private final ScoreboardManager manager;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Map<String, String> entries;

    public ArenaScoreboard(String title) {
        manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("ScoreboardManager не найден!");
        }

        scoreboard = manager.getNewScoreboard();
        objective = scoreboard.registerNewObjective("arenaStats", "dummy", ChatColor.AQUA + title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        entries = new LinkedHashMap<>();
    }

    public void updateTime(String time) {
        setScore("Время игры:", time);
    }

    public void updatePlayersLeft(int playersLeft) {
        setScore("Игроков осталось:", String.valueOf(playersLeft));
    }

    private void setScore(String title, String value) {
        if (entries.containsKey(title)) {
            scoreboard.resetScores(entries.get(title));
        }

        String entry = ChatColor.GREEN + title + ChatColor.WHITE + " " + value;

        entries.put(title, entry);

        updateEntries();
    }

    private void updateEntries() {
        int position = entries.size();
        for (String entry : entries.values()) {
            Score score = objective.getScore(entry);
            score.setScore(position--);
        }
    }

    public void applyToPlayer(Player player) {
        player.setScoreboard(scoreboard);
    }

    public void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
