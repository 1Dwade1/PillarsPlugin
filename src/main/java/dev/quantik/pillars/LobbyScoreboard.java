package dev.quantik.pillars;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyScoreboard {
    private final DatabaseManager database;
    private final ScoreboardManager manager;
    private final FileConfiguration config;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");

    public LobbyScoreboard(DatabaseManager database, FileConfiguration config) {
        this.database = database;
        this.config = config;
        manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("ScoreboardManager не найден!");
        }
    }

    public void applyToPlayer(Player player) {
        String displayCondition = config.getString("scoreboard.display-condition");
        if (displayCondition != null && !displayCondition.isEmpty()) {
            if (!evaluateCondition(displayCondition, player)) {
                return;
            }
        }

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "playerStats",
                "dummy",
                translateColors(config.getString("scoreboard.title"))
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = config.getStringList("scoreboard.lines");
        int position = lines.size();

        for (String line : lines) {
            String formattedLine = PlaceholderAPI.setPlaceholders(player, line);
            String translatedLine = translateColors(formattedLine);
            objective.getScore(translatedLine).setScore(position);
            position--;
        }

        player.setScoreboard(scoreboard);
    }

    private boolean evaluateCondition(String condition, Player player) {
        String resolved = PlaceholderAPI.setPlaceholders(player, condition);
        
        String[] conditions = resolved.split(";");
        for (String cond : conditions) {
            cond = cond.trim();
            
            if (cond.contains(">=")) {
                String[] parts = cond.split(">=", 2);
                if (parts.length < 2) return false;
                try {
                    int left = Integer.parseInt(parts[0].trim());
                    int right = Integer.parseInt(parts[1].trim());
                    if (left < right) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (cond.contains("<=")) {
                String[] parts = cond.split("<=", 2);
                if (parts.length < 2) return false;
                try {
                    int left = Integer.parseInt(parts[0].trim());
                    int right = Integer.parseInt(parts[1].trim());
                    if (left > right) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (cond.contains("=")) {
                String[] parts = cond.split("=", 2);
                if (parts.length == 2) {
                    String left = parts[0].trim();
                    String right = parts[1].trim();
                    if (!left.equals(right)) return false;
                }
            }
        }
        
        return true;
    }

    public void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public static String translateColors(String text) {
        if (Objects.equals(text, "")) return "";

        // Обработка HEX-кодов
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String replacement = ChatColor.of("#" + hexColor).toString();
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);

        // Заменяем стандартные коды (например, &e -> §e)
        return buffer.toString().replace("&", "§");
    }
}
