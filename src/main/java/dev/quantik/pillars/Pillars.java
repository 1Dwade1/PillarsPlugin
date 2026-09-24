package dev.quantik.pillars;

import me.clip.placeholderapi.PlaceholderAPI;
import me.filoghost.holographicdisplays.api.HolographicDisplaysAPI;
import me.filoghost.holographicdisplays.api.hologram.Hologram;
import me.filoghost.holographicdisplays.api.hologram.VisibilitySettings;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static dev.quantik.pillars.Arena.*;

public class Pillars extends JavaPlugin implements Listener {
    private PlayerPointsAPI playerPointsAPI;
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Map<Player, Hologram> playerHolograms = new HashMap<>();
    public static Pillars plugin;
    public static Location lobbyLocation;
    private final Map<String, Arena> arenas = new HashMap<>();
    private File arenasFile;
    public static FileConfiguration arenasConfig;
    private NPC selecter_npc;
    public static DatabaseManager database;
    private LobbyScoreboard lobbyScoreboard;
    public static Hologram top10hologram;
    private NPC fastgame_npc;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createArenasFile();
        getLogger().info("Pillars включен!");
        database = new DatabaseManager();
        plugin = this;
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Worlds: "+Bukkit.getWorlds());
    }
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("Pillars")) {
            loadArenasFromConfig();
            PlayerStatsUpdater statsUpdater = new PlayerStatsUpdater(this);
            database.addListener(statsUpdater);
            new PlayerStatsPlaceholder(database).register();
            lobbyScoreboard = new LobbyScoreboard(database, YamlConfiguration.loadConfiguration(new File(getDataFolder(),"config.yml")));
            getServer().getPluginManager().registerEvents(new LobbyListener(lobbyScoreboard), this);
            TopPlayersHologram topPlayersHologram = new TopPlayersHologram(this, database);
            Location top10hologramLocation = deserializeLocation(
                    arenasConfig.getConfigurationSection("holograms.top10").getValues(false)
            );
            topPlayersHologram.createTop10Hologram(top10hologramLocation);
        }
        if (event.getPlugin().getName().equalsIgnoreCase("PlayerPoints")){
            playerPointsAPI = PlayerPoints.getInstance().getAPI();
            getLogger().info("PlayerPoints API успешно подключен!");
        }
    }
    @EventHandler
    public void onCitizensLoad(CitizensEnableEvent event) {
        getLogger().info("Citizens успешно загружен. Теперь можно работать с NPC.");
        int i=0;
        for(NPC npc : CitizensAPI.getNPCRegistry()){
            i++;
            if(npc.getName().equals("Выбрать арену")){
                this.selecter_npc = npc;
            }
            if(npc.getName().equals("Быстрая игра")){
                this.fastgame_npc = npc;
            }
        }
        if(i==0){
            getLogger().warning("NPC registry is empty. No NPCs found.");
        }

    }
    public void addPointsToPlayer(Player player, int points) {
        UUID playerUUID = player.getUniqueId();
        boolean success = playerPointsAPI.give(playerUUID, points);
        if (!success) {
            getLogger().warning("Не удалось выдать поинты игроку "+player.getName());
        }
    }
    public PlayerPointsAPI getPlayerPointsAPI() {
        return playerPointsAPI;
    }

    private void openJoinMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 9, "Присоединиться к игре");
        int i=0;
        for(Arena arena : arenas.values()){
            ItemStack compass = new ItemStack(Material.COMPASS);
            ItemMeta meta = compass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("Арена "+arena.getName());
                List<String> lore = new ArrayList<>();
                lore.add("Игроков: " + arena.getPlayers().size() + "/"+arenasConfig.getInt("arenas."+arena.getName()+".playerLimit"));
                lore.add("Игра идет: " + (arena.isGameRunning() ? "Да" : "Нет"));
                meta.setLore(lore);
                compass.setItemMeta(meta);
            }
            menu.setItem(arenas.size()-1-i, compass);
            i++;
        }
        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Присоединиться к игре")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.COMPASS) {
                Player player = (Player) event.getWhoClicked();
                for (Arena arena : arenas.values()) {
                    if (arena.getPlayers().contains(player)) {
                        leaveArena(player,  false);
                        return;
                    }
                }
                Arena arena = arenas.values().stream().toList().get(arenas.size()-1-event.getSlot());
                player.closeInventory();
                if(!arena.isEnabled()){
                    player.sendMessage(ChatColor.RED+"Эта арена сейчас выключена");
                } else if(arena.isGameRunning()){
                    player.sendMessage(ChatColor.RED+"Игра уже идёт на этой арене");
                } else if(arena.getPlayers().size()==arenasConfig.getInt("arenas."+arena.getName()+".playerLimit")){
                    player.sendMessage(ChatColor.RED+"На этой арене уже достигнуто максимальное кол-во игроков");
                }
                if (arena.isEnabled() && !arena.isGameRunning() && arena.getPlayers().size()<arenasConfig.getInt("arenas."+arena.getName()+".playerLimit")) {
                    arena.addPlayer(player);
                    arena.broadcast(player.getName()+ " присоединился к арене ("+arena.getPlayers().size()+"/"+arenasConfig.getInt("arenas."+arena.getName()+".playerLimit")+")");
                    player.sendMessage("Вы присоединись к арене: " + arena.getName()+" ("+arena.getPlayers().size()+"/"+arenasConfig.getInt("arenas."+arena.getName()+".playerLimit")+")");

                    int minPlayers = getConfig().getInt("gameplay.min-players", 3);
                    if (arena.getPlayers().size() >= minPlayers && !arena.isCountdownStarted()) {
                        arena.setCountdownStarted(true);
                        startCountdown(arena);
                    }
                    return;
                }
            }
        }
    }
    @EventHandler
    public void onPlayerInteractNPC(PlayerInteractEntityEvent event) {
        if (!event.getHand().equals(EquipmentSlot.HAND)) {
            return;
        }
        if (selecter_npc != null && selecter_npc.getEntity() != null && event.getRightClicked().equals(selecter_npc.getEntity())) {
            openJoinMenu(event.getPlayer());
        }
        if(fastgame_npc != null && fastgame_npc.getEntity() != null && event.getRightClicked().equals(fastgame_npc.getEntity())){
            joinRandomArena(event.getPlayer());
        }
    }
    private void createLobbyNPC(Player player) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        selecter_npc = registry.createNPC(EntityType.PLAYER, "Выбрать арену");
        selecter_npc.spawn(player.getLocation());
        String skinName = arenasConfig.getString("npc.selector.skin", "1_QUANTIK_1");
        selecter_npc.getOrAddTrait(SkinTrait.class).setSkinName(skinName);
        saveConfig();
        getLogger().info("NPC создан с ID: " + selecter_npc.getId());
    }
    private void createFastGameNPC(Player player) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        fastgame_npc = registry.createNPC(EntityType.PLAYER, "Быстрая игра");
        fastgame_npc.spawn(player.getLocation());
        String skinName = arenasConfig.getString("npc.fastgame.skin", "1_QUANTIK_1");
        fastgame_npc.getOrAddTrait(SkinTrait.class).setSkinName(skinName);
        saveConfig();
        getLogger().info("NPC создан с ID: " + fastgame_npc.getId());
    }

    private void createArenasFile() {
        arenasFile = new File(getDataFolder(), "config.yml");
        if (!arenasFile.exists()) {
            saveResource("config.yml", false);
        }
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);
    }

    public void saveArenaToConfig(Arena arena) {
        String path = "arenas." + arena.getName();
        arenasConfig.set(path + ".spawnLocation", serializeLocation(arena.getSpawnLocation()));
        arenasConfig.set(path + ".enabled", arena.isEnabled());
    }
    public void saveLobbyLocation(Location lobbyLocation) {
        arenasConfig.set("lobby", serializeLocation(lobbyLocation));
    }
    private Map<String, Object> serializeLocation(Location location) {
        if (location == null) return null;

        Map<String, Object> map = new HashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }
    public void loadArenasFromConfig() {
        if (arenasConfig.contains("lobby")) {
            Location lobbyLocation = deserializeLocation(arenasConfig.getConfigurationSection("lobby").getValues(false));
            this.lobbyLocation = lobbyLocation;
        }

        if (arenasConfig.contains("arenas")) {
            for (String arenaName : arenasConfig.getConfigurationSection("arenas").getKeys(false)) {
                String path = "arenas." + arenaName;
                Location spawnLocation = deserializeLocation(arenasConfig.getConfigurationSection(path + ".spawnLocation").getValues(false));
                boolean enabled = arenasConfig.getBoolean(path + ".enabled");
                Arena arena = new Arena(arenaName);
                arena.setSpawnLocation(spawnLocation);
                arena.setEnabled(enabled);
                arenas.put(arenaName, arena);
            }
        }
    }

    private Location deserializeLocation(Map<String, Object> map) {
        if (map == null) return null;
        if(Bukkit.getWorld((String) map.get("world"))==null){
            Bukkit.createWorld(new WorldCreator((String) map.get("world"))).setSpawnFlags(true, true);
        }
        World world = Bukkit.getWorld(map.get("world").toString());
        double x = (double) map.get("x");
        double y = (double) map.get("y");
        double z = (double) map.get("z");
        float yaw = ((Double) map.get("yaw")).floatValue();
        float pitch = ((Double) map.get("pitch")).floatValue();

        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public void onDisable() {
        getLogger().info("Сохранение лобби и арен в конфиг...");
        for (Arena arena : arenas.values()) {
            saveArenaToConfig(arena);
        }
        if (lobbyLocation != null) {
            saveLobbyLocation(lobbyLocation);
        }
        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить конфигурацию при отключении!");
            e.printStackTrace();
        }
        for(Hologram hologram : playerHolograms.values()){
            hologram.delete();
        }
        if (top10hologram != null) {
            top10hologram.delete();
        }
        if (database != null) {
            database.close();
        }
        getLogger().info("Pillars отключён!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pillars")) {
            return handlePillarsCommand(sender, args);
        }
        return false;
    }

    private boolean handlePillarsCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Использование: /pillars <join/quit>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join":
                if (sender instanceof Player player) {
                    joinRandomArena(player);
                } else {
                    sender.sendMessage("Только игроки могут присоединиться к арене.");
                }
                return true;
            case "create":
                if (args.length > 1) {
                    if(sender.hasPermission("Pillars.commands.pillars.create")){
                        String arenaName = args[1];
                        Arena arena = new Arena(arenaName);
                        arenas.put(arenaName, arena);
                        arenasConfig.set("arenas."+arenaName+".pillars_radius", 10);
                        arenasConfig.set("arenas."+arenaName+".worldborder_size", 50);
                        arenasConfig.set("arenas."+arenaName+".worldborder_duration", 600);
                        arenasConfig.set("arenas."+arenaName+".playerLimit", 8);
                        sender.sendMessage("Арена " + arenaName + " создана!");
                    }
                } else {
                    sender.sendMessage("Использование: /pillars create <название арены>");
                }
                return true;
            case "settoppos":
                if (sender.hasPermission("Pillars.commands.pillars.settoppos")) {
                    if (sender instanceof Player player) {
                        Location location = player.getLocation().add(0, 2, 0);
                        arenasConfig.set("holograms.top10", serializeLocation(location));
                        try {
                            arenasConfig.save(arenasFile);
                            sender.sendMessage("Позиция для топ-10 успешно сохранена");
                        } catch (IOException e) {
                            sender.sendMessage("Ошибка сохранения позиции в конфиг.");
                            e.printStackTrace();
                        }
                    } else {
                        sender.sendMessage("Только игроки могут использовать эту команду.");
                    }
                }
                return true;
            case "setstatspos":
                if (sender.hasPermission("Pillars.commands.pillars.setstatspos")) {
                    if (sender instanceof Player player) {
                        Location location = player.getLocation().add(0, 2, 0);
                        arenasConfig.set("holograms.personalStats", serializeLocation(location));
                        try {
                            arenasConfig.save(arenasFile);
                            sender.sendMessage("Позиция для статистики успешно сохранена!");
                        } catch (IOException e) {
                            sender.sendMessage("Ошибка сохранения позиции в конфиг.");
                            e.printStackTrace();
                        }
                    } else {
                        sender.sendMessage("Только игроки могут использовать эту команду.");
                    }
                }
                return true;
            case "setenabled":
                if (args.length > 2) {
                    if(sender.hasPermission("Pillars.commands.pillars.setenabled")){
                        String arenaName = args[1];
                        boolean enabled = Boolean.parseBoolean(args[2]);
                        Arena arena = arenas.get(arenaName);
                        if (arena != null) {
                            arena.setEnabled(enabled);
                            sender.sendMessage("Арена " + arenaName + " теперь " + (enabled ? "открыта" : "закрыта") + "!");
                        } else {
                            sender.sendMessage("Арена " + arenaName + " не существует.");
                        }
                    }
                } else {
                    sender.sendMessage("Использование: /pillars setenabled <название арены> <true/false>");
                }
                return true;
            case "info":
                sender.sendMessage("Made by QUANTIK");
                return true;
            case "quit":
                if (sender instanceof Player player) {
                    leaveArena(player, true);
                    lobbyScoreboard.applyToPlayer(player);
                } else {
                    sender.sendMessage("Только игроки могут покинуть арену.");
                }
                return true;
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage("Использование: /pillars delete <название арены>");
                    return true;
                }
                if(sender.hasPermission("Pillars.commands.pillars.delete")){
                    String arenaName = args[1];
                    if (!arenas.containsKey(arenaName)) {
                        sender.sendMessage("Арена " + arenaName + " не существует.");
                        return true;
                    }
                    
                    Arena arenaToDelete = arenas.get(arenaName);
                    
                    if (arenaToDelete.isGameRunning() || !arenaToDelete.getPlayers().isEmpty()) {
                        sender.sendMessage("§cНельзя удалить арену, пока на ней находятся игроки или идёт игра!");
                        sender.sendMessage("§cИгроков на арене: " + arenaToDelete.getPlayers().size());
                        sender.sendMessage("§cИгра активна: " + (arenaToDelete.isGameRunning() ? "Да" : "Нет"));
                        return true;
                    }
                    
                    arenaToDelete.deleteWorld();

                    arenas.remove(arenaName);

                    arenasConfig.set("arenas." + arenaName, null);
                    try {
                        arenasConfig.save(arenasFile);
                    } catch (IOException e) {
                        sender.sendMessage("Ошибка сохранения файла с аренами.");
                        e.printStackTrace();
                        return true;
                    }
                    sender.sendMessage("Арена " + arenaName + " успешно удалена!");
                }
                return true;
            case "setlobby":
                if(sender.hasPermission("Pillars.commands.pillars.setlobby")){
                    if (args.length > 0 && args[0].equalsIgnoreCase("setlobby")) {
                        if (sender instanceof Player player) {
                            lobbyLocation = player.getLocation();
                            saveLobbyLocation(lobbyLocation);
                            sender.sendMessage("Местоположение лобби задано!");
                        } else {
                            sender.sendMessage("Только игроки могут задать местоположение лобби.");
                        }
                        return true;
                    }
                    sender.sendMessage("Использование: /pillars <setlobby>");
                }
                return true;
            case "setselecternpc":
                if(sender.hasPermission("Pillars.commands.pillars.setnpc")){
                    if (sender instanceof Player player) {
                        createLobbyNPC(player);
                        sender.sendMessage("NPC создан");
                    }
                }
                return true;
            case "setfastgamenpc":
                if(sender.hasPermission("Pillars.commands.pillars.setnpc")){
                    if (sender instanceof Player player) {
                        createFastGameNPC(player);
                        sender.sendMessage("NPC создан");
                    }
                }
                return true;
            case "reload":
                if (sender.hasPermission("Pillars.commands.pillars.reload")) {
                    getLogger().info("Перезагрузка плагина...");

                    for (Arena arena : arenas.values()) {
                        saveArenaToConfig(arena);
                    }

                    if (lobbyLocation != null) {
                        saveLobbyLocation(lobbyLocation);
                    }

                    try {
                        arenasConfig.save(arenasFile);
                    } catch (IOException e) {
                        getLogger().severe("Не удалось сохранить конфигурацию!");
                        e.printStackTrace();
                        sender.sendMessage("§cОшибка сохранения конфигурации!");
                        return true;
                    }

                    for (Hologram hologram : playerHolograms.values()) {
                        hologram.delete();
                    }
                    playerHolograms.clear();
                    
                    if (top10hologram != null) {
                        top10hologram.delete();
                    }

                    arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);
                    loadArenasFromConfig();

                    lobbyScoreboard = new LobbyScoreboard(database, YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")));
                    TopPlayersHologram topPlayersHologram = new TopPlayersHologram(this, database);

                    Location top10hologramLocation = deserializeLocation(
                            arenasConfig.getConfigurationSection("holograms.top10").getValues(false)
                    );
                    topPlayersHologram.createTop10Hologram(top10hologramLocation);

                    int npcCount = 0;
                    for (NPC npc : CitizensAPI.getNPCRegistry()) {
                        npcCount++;
                        if (npc.getName().equals("Выбрать арену")) {
                            this.selecter_npc = npc;
                        }
                        if (npc.getName().equals("Быстрая игра")) {
                            this.fastgame_npc = npc;
                        }
                    }

                    if (npcCount == 0) {
                        getLogger().warning("NPC registry is empty. No NPCs found.");
                    }
                    
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (playerHolograms.get(online) == null) {
                            createPersonalStats(online);
                        }
                    }

                    sender.sendMessage("§aПлагин успешно перезагружен!");
                    getLogger().info("Перезагрузка завершена.");
                }
                return true;
            default:
                sender.sendMessage("Неизвестная команда.");
                return true;
        }
    }

    private void leaveArena(Player player, boolean command) {
        for (Arena arena : arenas.values()) {
            if (arena.getPlayers().contains(player)) {
                if(arena.isGameRunning()){
                    player.getInventory().clear();
                    arena.playerEliminated(player);
                    addPointsToPlayer(player, 1);
                    database.addLoss(player.getName());
                    player.setGameMode(GameMode.SURVIVAL);
                    player.teleport(lobbyLocation);
                }else{
                    arena.removePlayer(player);
                }
                arena.broadcast(player.getName()+" покинул арену");
                if(command){
                    player.sendMessage("Вы покинули арену.");
                }
                if (arena.getPlayers().size() == 1) {
                    if(arena.isGameRunning()){
                        endGame(arena);
                    }
                }
                return;
            }
        }
        if(command){
            for(Arena arena : arenas.values()){
                if(arena.world.getPlayers().contains(player)){
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setHealth(20);
                    player.getInventory().clear();
                    player.teleport(lobbyLocation);
                    return;
                }
            }
            player.sendMessage("Вы не находитесь на арене.");
        }
    }

    private void startCountdown(Arena arena) {
        BukkitRunnable countdownTask = new BukkitRunnable() {
            int countdown = getConfig().getInt("gameplay.countdown-duration", 30);

            @Override
            public void run() {
                int minPlayers = getConfig().getInt("gameplay.min-players", 3);
                if (arena.getPlayers().size() < minPlayers || !arena.isEnabled()) {
                    arena.broadcast("Отмена. Недостаточно игроков.");
                    arena.setCountdownStarted(false);
                    this.cancel();
                    return;
                }

                if (countdown <= 0) {
                    startGame(arena);
                    this.cancel();
                } else if (countdown <= 5) {
                    arena.broadcast("Игра начинается через " + countdown + " секунд.");
                    countdown--;
                } else {
                    if (countdown % 10 == 0) {
                        arena.broadcast("Игра начинается через " + countdown + " секунд.");
                    }
                    countdown--;
                }
            }
        };
        countdownTask.runTaskTimer(this, 0L, 20L);
        arena.setCountdownTask(countdownTask);
    }

    private void startGame(Arena arena) {
        int minPlayers = getConfig().getInt("gameplay.min-players", 3);
        if (!arena.isEnabled() || arena.getPlayers().size() < minPlayers) {
            getLogger().info("Игра " + arena.getName() + " не может быть начата.");
            arena.setCountdownStarted(false);
            return;
        }

        arena.setGameRunning(true);
        Bukkit.broadcastMessage("Игра начинается на арене " + arena.getName());
        arena.broadcast("Игроки:");
        for(Player player : arena.getPlayers()){
            arena.broadcast(player.getName());
        }

        createPillars(arena);
        setupWorldBorder(arena, this);

        arena.max_players = arena.getPlayers().size();
        arena.startTime = System.currentTimeMillis();
        arena.scoreboard = new ArenaScoreboard("Статистика арены");

        for (Player player : arena.getPlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().clear();
            arena.scoreboard.updatePlayersLeft(arena.getPlayers().size());
            arena.scoreboard.updateTime("0:00");
            arena.scoreboard.applyToPlayer(player);
        }

        BukkitRunnable scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!arena.isGameRunning()) {
                    this.cancel();
                    return;
                }
                long elapsedTime = (System.currentTimeMillis() - arena.startTime) / 1000;
                int minutes = (int) (elapsedTime / 60);
                int seconds = (int) (elapsedTime % 60);
                String time = minutes + ":" + (seconds < 10 ? "0" : "") + seconds;

                arena.scoreboard.updateTime(time);
                arena.scoreboard.updatePlayersLeft(arena.getPlayers().size());
            }
        };
        scoreboardTask.runTaskTimer(this, 0L, 20L);
        arena.setScoreboardTask(scoreboardTask);

        int itemDropInterval = getConfig().getInt("gameplay.item-drop-interval", 10);
        BukkitRunnable itemTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!arena.isGameRunning()) {
                    this.cancel();
                    return;
                }

                for (Player player : arena.getPlayers()) {
                    if (!player.isOnline()) continue;

                    Material randomMaterial = getRandomMaterial();
                    player.getInventory().addItem(new ItemStack(randomMaterial));
                }

                if (arena.getPlayers().size() == 1) {
                    endGame(arena);
                    this.cancel();
                }
            }
        };
        itemTask.runTaskTimer(this, 20L * itemDropInterval, 20L * itemDropInterval);
        arena.setItemTask(itemTask);
    }


    private void endGame(Arena arena) {
        arena.setGameRunning(false);
        arena.cancelAllTasks();
        Player winner = arena.getLastPlayerStanding();
        arena.world.getWorldBorder().setSize(100000);
        if (winner != null) {
            addPointsToPlayer(winner, 5);
            database.addWin(winner.getName());
            arena.scoreboard.clear(winner);
            top10hologram.delete();
            TopPlayersHologram topPlayersHologram = new TopPlayersHologram(this, database);
            Location top10hologramLocation = deserializeLocation(
                    arenasConfig.getConfigurationSection("holograms.top10").getValues(false)
            );
            topPlayersHologram.createTop10Hologram(top10hologramLocation);
            winner.setAllowFlight(true);
            winner.setMetadata("immortal", new FixedMetadataValue(this, true));
            Bukkit.broadcastMessage("Победитель арены " + arena.getName() + " - " + winner.getName());
            int returnDelay = getConfig().getInt("gameplay.winner-return-delay", 5);
            winner.sendMessage("Вы победили. Возвращение в лобби через " + returnDelay + " секунд");
            Plugin plugin = this;
            new BukkitRunnable() {
                @Override
                public void run() {
                    winner.teleport(lobbyLocation);
                    arena.world.getPlayers().forEach(player -> {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setHealth(20);
                        player.getInventory().clear();
                        player.teleport(lobbyLocation);
                    });
                    if (winner.isOnline()) {
                        winner.sendMessage("Игра завершена.");
                    }
                    winner.removeMetadata("immortal", plugin);
                    winner.setAllowFlight(false);
                    winner.getInventory().clear();
                    winner.setHealth(20.0);
                    resetArena(arena);
                    lobbyScoreboard.applyToPlayer(winner);
                }
            }.runTaskLater(this, 20L * returnDelay);
            new BukkitRunnable(){
                int fireworkCount = 0;
                @Override
                public void run() {
                    if (!winner.isOnline()) {
                        this.cancel();
                        return;
                    }
                    if(!arena.world.getPlayers().contains(winner)){
                        this.cancel();
                        return;
                    }
                    if(fireworkCount >= 4){
                        this.cancel();
                        return;
                    }
                    double y = winner.getLocation().getY();
                    Location location = winner.getLocation();
                    location.setY(y + 3);

                    Firework firework = winner.getWorld().spawn(location, Firework.class);

                    FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(Color.RED)
                            .withFade(Color.ORANGE)
                            .withTrail()
                            .withFlicker()
                            .build());
                    meta.setPower(1);
                    firework.setFireworkMeta(meta);

                    fireworkCount++;
                }
            }.runTaskTimer(this, 0, 20L);
        } else {
            Bukkit.broadcastMessage("Игра на арене " + arena.getName() + " закончилась без победителей.");
        }
    }


    private Material getRandomMaterial() {
        Material[] materials = Material.values();
        Material randomMaterial;
        do {
            randomMaterial = materials[new Random().nextInt(materials.length)];
        } while (!randomMaterial.isItem() || Const.BLOCK_BAN.contains(randomMaterial));
        return randomMaterial;
    }
    private void joinRandomArena(Player player) {
        Arena bestArena = null;
        int maxPlayers = -1;

        for (Arena arena : arenas.values()) {
            if (arena.getPlayers().contains(player)) {
                player.sendMessage("Вы уже присоединились к арене");
                return;
            }
            if (arena.isEnabled() && !arena.isGameRunning() && arena.getPlayers().size() < arenasConfig.getInt("arenas." + arena.getName() + ".playerLimit")) {
                if (arena.getPlayers().size() > maxPlayers) {
                    maxPlayers = arena.getPlayers().size();
                    bestArena = arena;
                }
            }
        }

        if (bestArena != null) {
            bestArena.addPlayer(player);
            bestArena.broadcast(player.getName() + " присоединился к арене ("+bestArena.getPlayers().size()+"/"+arenasConfig.getInt("arenas."+bestArena.getName()+".playerLimit")+")");
            player.sendMessage("Вы присоединились к арене: " + bestArena.getName()+" ("+bestArena.getPlayers().size()+"/"+arenasConfig.getInt("arenas."+bestArena.getName()+".playerLimit")+")");

            int minPlayers = getConfig().getInt("gameplay.min-players", 3);
            if (bestArena.getPlayers().size() >= minPlayers && !bestArena.isCountdownStarted()) {
                bestArena.setCountdownStarted(true);
                startCountdown(bestArena);
            }
        } else {
            player.sendMessage("Нет свободных арен.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = event.getEntity().getKiller();
        for (Arena arena : arenas.values()) {
            if (arena.getPlayers().contains(player)) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline()) {
                        player.spigot().respawn();

                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            if (arena.getPlayers().contains(player)) {
                                if (arena.isGameRunning()) {
                                    player.getInventory().clear();
                                    arena.playerEliminated(player);
                                    addPointsToPlayer(player, 1);
                                    database.addLoss(player.getName());
                                    player.teleport(arena.getSpawnLocation());
                                    player.setGameMode(GameMode.SPECTATOR);
                                } else {
                                    arena.removePlayer(player);
                                }

                                if (arena.getPlayers().size() == 1) {
                                    if (arena.isGameRunning()) {
                                        endGame(arena);
                                    }
                                }
                            }
                            lobbyScoreboard.applyToPlayer(player);
                        }, 2L);
                    }
                }, 1L);

                if(killer!=null){
                    if(arena.getPlayers().contains(killer)){
                        String killerName = killer.getName();
                        database.addKill(killerName);
                    }
                }else{
                    if (lastDamager.containsKey(player.getUniqueId())) {
                        UUID killerId = lastDamager.get(player.getUniqueId());
                        killer = Bukkit.getPlayer(killerId);
                        if (killer != null) {
                            database.addKill(killer.getName());
                        }
                    }
                    lastDamager.remove(player.getUniqueId());
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        for (Arena arena : arenas.values()) {
            if (arena.getPlayers().contains(player)) {
                arena.getPlacedBlocks().add(event.getBlockPlaced().getLocation());
                return;
            }
        }
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        lastDamager.remove(player.getUniqueId());
        lastDamager.values().removeIf(uuid -> uuid.equals(player.getUniqueId()));
        
        if (playerHolograms.containsKey(player)) {
            Hologram hologram = playerHolograms.get(player);
            if (hologram != null) {
                hologram.delete();
            }
            playerHolograms.remove(player);
        }
        
        if (player.hasMetadata("immortal")) {
            player.removeMetadata("immortal", this);
        }
        
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        
        leaveArena(player, false);
    }
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.hasMetadata("immortal")) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onPlayerByPlayerDamage(EntityDamageByEntityEvent event){
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player damager) {
            lastDamager.put(victim.getUniqueId(), damager.getUniqueId());
            
            Bukkit.getScheduler().runTaskLater(this, () -> {
                lastDamager.remove(victim.getUniqueId());
            }, 20L * 10);
        }
    }
    public void createPersonalStats(Player player) {
        HolographicDisplaysAPI api = HolographicDisplaysAPI.get(this);

        if (!arenasConfig.contains("holograms.personalStats")) {
            getLogger().warning("Конфигурация holograms.personalStats не найдена!");
            return;
        }

        Location hologramLocation = deserializeLocation(
                arenasConfig.getConfigurationSection("holograms.personalStats").getValues(false)
        );
        
        if (hologramLocation == null) {
            getLogger().warning("Не удалось десериализовать location для personalStats!");
            return;
        }
        
        Hologram playerHologram = api.createHologram(hologramLocation);

        VisibilitySettings visibilitySettings = playerHologram.getVisibilitySettings();
        visibilitySettings.setGlobalVisibility(VisibilitySettings.Visibility.HIDDEN);
        visibilitySettings.setIndividualVisibility(player, VisibilitySettings.Visibility.VISIBLE);

        playerHologram.getLines().appendText("§aСтатистика " + player.getName());

        String winsPlaceholder = PlaceholderAPI.setPlaceholders(player, "%playerstats_wins%");
        String lossesPlaceholder = PlaceholderAPI.setPlaceholders(player, "%playerstats_losses%");
        String killsPlaceholder = PlaceholderAPI.setPlaceholders(player, "%playerstats_kills%");
        int wins = Integer.parseInt(winsPlaceholder);
        int losses = Integer.parseInt(lossesPlaceholder);
        int kills = Integer.parseInt(killsPlaceholder);
        double winLossRatio = losses == 0 ? (wins > 0 ? wins : 0) : (double) wins / losses;
        double killDeathRatio = losses == 0 ? (kills > 0 ? kills : 0) : (double) kills / losses;

        playerHologram.getLines().appendText("§eПобеды: " + winsPlaceholder);
        playerHologram.getLines().appendText("§cПоражения: " + lossesPlaceholder);
        playerHologram.getLines().appendText("§6W/L: " + String.format("%.2f", winLossRatio));
        playerHologram.getLines().appendText("");
        playerHologram.getLines().appendText("§eУбийства: " + killsPlaceholder);
        playerHologram.getLines().appendText("§cСмерти: " + lossesPlaceholder);
        playerHologram.getLines().appendText("§6K/D: " + String.format("%.2f", killDeathRatio));

        playerHolograms.put(player, playerHologram);
    }
    public void updatePersonalStats(Player player){
        if(playerHolograms.containsKey(player)){
            playerHolograms.get(player).delete();
            playerHolograms.remove(player);
        }
        createPersonalStats(player);
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        if(playerHolograms.get(event.getPlayer())==null){
            createPersonalStats(event.getPlayer());
        }
    }
}

