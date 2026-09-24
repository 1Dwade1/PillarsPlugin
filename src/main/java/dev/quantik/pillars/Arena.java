package dev.quantik.pillars;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

import static dev.quantik.pillars.Pillars.arenasConfig;
import static dev.quantik.pillars.Pillars.lobbyLocation;

public class Arena {
    private final String name;
    private Location spawnLocation;
    private boolean enabled;
    private boolean gameRunning;
    public int max_players;
    public Set<Player> players = new HashSet<>();
    private final List<Location> placedBlocks = new ArrayList<>();
    public ArenaScoreboard scoreboard;
    public long startTime;
    public final World world;
    private BukkitRunnable scoreboardTask;
    private BukkitRunnable itemTask;
    private BukkitRunnable borderTask;
    private BukkitRunnable countdownTask;
    private volatile boolean countdownStarted = false;

    public Arena(String name) {
        this.name = name;
        this.enabled = false;
        this.gameRunning = false;
        this.world = createWorldForArena(name);
    }

    public static void setupWorldBorder(Arena arena, Pillars plugin) {
        World world = arena.world;
        WorldBorder border = world.getWorldBorder();

        double initialSize = arenasConfig.getDouble("arenas."+arena.getName()+".worldborder_size");
        border.setSize(initialSize);

        border.setCenter(0, 0);

        double finalSize = 1.0;
        long durationInSeconds = arenasConfig.getLong("arenas."+arena.getName()+".worldborder_duration");

        border.setSize(finalSize, durationInSeconds);

        BukkitRunnable borderTask = new BukkitRunnable() {
            @Override
            public void run() {
                if(!arena.isGameRunning()){
                    this.cancel();
                }
                for (Player player : world.getPlayers()) {
                    if (!border.isInside(player.getLocation())) {
                        player.damage(2.0);
                    }
                }
            }
        };
        borderTask.runTaskTimer(plugin, 20L, 20L);
        arena.setBorderTask(borderTask);
    }


    private World createWorldForArena(String arenaName) {
        String worldName = "arena_" + arenaName;
        World existingWorld = Bukkit.getWorld(worldName);

        if (existingWorld != null) {
            Bukkit.getLogger().warning("Мир с именем " + worldName + " уже существует. Используется существующий мир.");
            return existingWorld;
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generator(new ChunkGenerator() {
            @Override
            public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
                return createChunkData(world);
            }
        });
        creator.generateStructures(false);

        World newWorld = creator.createWorld();
        if (newWorld != null) {
            newWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            newWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            newWorld.setTime(6000);
            newWorld.setGameRuleValue("doMobSpawning", "true");
            newWorld.setSpawnFlags(true, true);
            Bukkit.getLogger().info("Мир для арены " + arenaName + " успешно создан.");
            this.spawnLocation = new Location(newWorld, 0, 100, 0, 0, 0);
        } else {
            throw new IllegalStateException("Не удалось создать мир для арены: " + arenaName);
        }
        return newWorld;
    }
    public void deleteWorld() {
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            File worldFolder = world.getWorldFolder();
            deleteDirectory(worldFolder);
            Bukkit.getLogger().info("Мир для арены " + name + " удален.");
        }
    }

    private boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return directory.delete();
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public String getName() {
        return name;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public void setGameRunning(boolean gameRunning) {
        this.gameRunning = gameRunning;
    }

    public Set<Player> getPlayers() {
        return players;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean isCountdownStarted() {
        return countdownStarted;
    }

    public void setCountdownStarted(boolean started) {
        this.countdownStarted = started;
    }

    public void setScoreboardTask(BukkitRunnable task) {
        this.scoreboardTask = task;
    }

    public void setItemTask(BukkitRunnable task) {
        this.itemTask = task;
    }

    public void setBorderTask(BukkitRunnable task) {
        this.borderTask = task;
    }

    public void setCountdownTask(BukkitRunnable task) {
        this.countdownTask = task;
    }

    public void cancelAllTasks() {
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
        if (itemTask != null) {
            itemTask.cancel();
            itemTask = null;
        }
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }


    public static void createPillars(Arena arena) {
        if (arena.world == null) {
            Bukkit.getLogger().severe("Мир не найден!");
            return;
        }

        Set<Player> players = arena.getPlayers();
        int playerCount = players.size();
        int minPlayers = arenasConfig.getInt("gameplay.min-players", 3);
        if (playerCount < minPlayers) {
            Bukkit.getLogger().warning("Недостаточно игроков для начала игры.");
            return;
        }

        int radius = arenasConfig.getInt("arenas."+arena.getName()+".pillars_radius");
        int baseX = arena.spawnLocation.getBlockX();
        int baseZ = arena.spawnLocation.getBlockZ();
        int baseY = arena.spawnLocation.getBlockY();

        double angleStep = 360.0 / playerCount;

        int index = 0;
        for (Player player : players) {
            double angle = Math.toRadians(index * angleStep);

            int offsetX = (int) (radius * Math.cos(angle));
            int offsetZ = (int) (radius * Math.sin(angle));

            Location pillarLocation = new Location(arena.world, baseX + offsetX, baseY, baseZ + offsetZ);

            int pillarHeight = arenasConfig.getInt("gameplay.pillar-height", 20);
            for (int y = baseY; y < baseY + pillarHeight; y++) {
                arena.world.getBlockAt(pillarLocation.getBlockX(), y, pillarLocation.getBlockZ()).setType(Material.BEDROCK);
            }

            Location spawnLocation = pillarLocation.clone().add(0.5, pillarHeight + 1, 0.5);
            player.teleport(spawnLocation);

            index++;
        }
    }

    public void playerEliminated(Player player) {
        players.remove(player);
        scoreboard.updatePlayersLeft(players.size());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearPlayers() {
        players.clear();
    }

    public Player getLastPlayerStanding() {
        return players.size() == 1 ? players.iterator().next() : null;
    }

    public void broadcast(String message) {
        for (Player player : players) {
            player.sendMessage(message);
        }
    }
    public List<Location> getPlacedBlocks() {
        return placedBlocks;
    }

    public static void resetArena(Arena arena) {
        if (arena.world == null) {
            Bukkit.getLogger().severe("Мир не найден!");
            return;
        }

        arena.cancelAllTasks();
        arena.setCountdownStarted(false);

        for (Player player : arena.getPlayers()) {
            player.getInventory().clear();
            player.teleport(lobbyLocation);
            player.sendMessage("Игра окончена. Вы были возвращены в лобби.");
            arena.scoreboard.clear(player);
        }

        arena.clearPlayers();

        World arenaWorld = Bukkit.getWorld("arena_"+arena.getName());
        if (arenaWorld != null) {
            arenaWorld.getEntities().forEach(entity -> {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            });
        }

        List<Location> blocksToRemove = new ArrayList<>(arena.getPlacedBlocks());
        arena.getPlacedBlocks().clear();

        new BukkitRunnable() {
            int index = 0;
            
            @Override
            public void run() {
                if (index >= blocksToRemove.size()) {
                    this.cancel();
                    arena.setGameRunning(false);
                    return;
                }
                
                int batchSize = 100;
                int endIndex = Math.min(index + batchSize, blocksToRemove.size());
                
                for (int i = index; i < endIndex; i++) {
                    Location location = blocksToRemove.get(i);
                    if (location != null && location.getWorld() != null) {
                        location.getBlock().setType(Material.AIR);
                    }
                }
                
                index = endIndex;
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("Pillars"), 0L, 1L);
    }

}

