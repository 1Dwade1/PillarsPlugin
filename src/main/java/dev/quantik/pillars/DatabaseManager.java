package dev.quantik.pillars;

import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {
    private static final String DATABASE_URL = "jdbc:sqlite:player_stats.db";
    private final List<DatabaseUpdateListener> listeners = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, PlayerStats> cache = new ConcurrentHashMap<>();

    public static class PlayerStats {
        public int wins;
        public int losses;
        public int kills;
        
        public PlayerStats(int wins, int losses, int kills) {
            this.wins = wins;
            this.losses = losses;
            this.kills = kills;
        }
    }

    public DatabaseManager() {
        try (Connection connection = connect()) {
            String createTable = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_name TEXT PRIMARY KEY,
                    wins INTEGER DEFAULT 0,
                    losses INTEGER DEFAULT 0,
                    kills INTEGER DEFAULT 0
                )
                """;
            connection.prepareStatement(createTable).execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    public void addListener(DatabaseUpdateListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners(String playerName) {
        Bukkit.getScheduler().runTask(Pillars.plugin, () -> {
            for (DatabaseUpdateListener listener : listeners) {
                listener.onDatabaseUpdate(playerName);
            }
        });
    }
    
    private void invalidateCache(String playerName) {
        cache.remove(playerName);
    }

    public void addWin(String playerName) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = connect()) {
                String query = """
                    INSERT INTO player_stats (player_name, wins, losses, kills)
                    VALUES (?, 1, 0, 0)
                    ON CONFLICT(player_name) DO UPDATE SET wins = wins + 1
                    """;
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerName);
                    statement.executeUpdate();
                }
                invalidateCache(playerName);
                notifyListeners(playerName);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, executor);
    }

    public void addLoss(String playerName) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = connect()) {
                String query = """
                    INSERT INTO player_stats (player_name, wins, losses, kills)
                    VALUES (?, 0, 1, 0)
                    ON CONFLICT(player_name) DO UPDATE SET losses = losses + 1
                    """;
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerName);
                    statement.executeUpdate();
                }
                invalidateCache(playerName);
                notifyListeners(playerName);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, executor);
    }

    public void addKill(String playerName) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = connect()) {
                String query = """
                    INSERT INTO player_stats (player_name, wins, losses, kills)
                    VALUES (?, 0, 0, 1)
                    ON CONFLICT(player_name) DO UPDATE SET kills = kills + 1
                    """;
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, playerName);
                    statement.executeUpdate();
                }
                invalidateCache(playerName);
                notifyListeners(playerName);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, executor);
    }
    
    private PlayerStats fetchStats(String playerName) {
        try (Connection connection = connect()) {
            String query = "SELECT wins, losses, kills FROM player_stats WHERE player_name = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, playerName);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    return new PlayerStats(
                        resultSet.getInt("wins"),
                        resultSet.getInt("losses"),
                        resultSet.getInt("kills")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new PlayerStats(0, 0, 0);
    }
    
    public int getKills(String playerName) {
        PlayerStats stats = cache.computeIfAbsent(playerName, this::fetchStats);
        return stats.kills;
    }

    public int getWins(String playerName) {
        PlayerStats stats = cache.computeIfAbsent(playerName, this::fetchStats);
        return stats.wins;
    }

    public int getLosses(String playerName) {
        PlayerStats stats = cache.computeIfAbsent(playerName, this::fetchStats);
        return stats.losses;
    }

    public double getWinLossRatio(String playerName) {
        int wins = getWins(playerName);
        int losses = getLosses(playerName);
        return losses == 0 ? (wins > 0 ? wins : 0) : (double) wins / losses;
    }
    public List<String> getTopPlayers() {
        List<String> topPlayers = new ArrayList<>();
        try (Connection connection = connect()) {
            String query = """
            SELECT player_name, wins
            FROM player_stats
            ORDER BY wins DESC
            LIMIT 10
        """;
            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String playerName = resultSet.getString("player_name");
                    int wins = resultSet.getInt("wins");
                    topPlayers.add(playerName + " - " + wins + " побед");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topPlayers;
    }

    public void close() {
        listeners.clear();
        executor.shutdown();
    }
}