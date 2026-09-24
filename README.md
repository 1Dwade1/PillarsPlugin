# Pillars

Плагин для Minecraft мини-игры "Столбы" — батл-рояль режим, где игроки сражаются на столбах, пока мировая граница сжимается.

## Как это работает

Игроки присоединяются к арене через NPC или команду `/pillars join`. После набора минимального количества участников запускается обратный отсчёт. Игра начинается: каждый игрок телепортируется на отдельный столб из бедрока, расположенный по кругу вокруг центра арены. Каждые N секунд игрокам выдаётся случайный блок для строительства. Мировая граница постепенно сжимается к центру, вынуждая игроков сражаться на уменьшающейся территории. Последний выживший получает награду в виде очков и записывается в базу данных статистики. После победы арена автоматически очищается и становится доступной для следующей игры.

Плагин создаёт для каждой арены отдельный плоский мир (`arena_<название>`), управляет состоянием игры через систему задач, отслеживает статистику через SQLite (асинхронно + кэш), интегрируется с Citizens (NPC), HolographicDisplays (топ-10 игроков, личная статистика) и PlaceholderAPI (scoreboard переменные).

## Требования

- Minecraft 1.16+
- Spigot/Paper сервер
- **Обязательные зависимости:**
  - [Citizens](https://github.com/CitizensDev/Citizens2)
  - [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)
  - [HolographicDisplays](https://github.com/filoghost/HolographicDisplays)
  - [PlayerPoints](https://github.com/Rosewood-Development/PlayerPoints)

## Установка

1. Скачайте `Pillars.jar` из [Releases](https://github.com/1Dwade1/PillarsPlugin/releases) или соберите сами
2. Установите все обязательные зависимости: Citizens, PlaceholderAPI, HolographicDisplays, PlayerPoints
3. Поместите JAR в папку `plugins/`
4. Перезапустите сервер
5. Настройте `plugins/Pillars/config.yml`
6. Создайте арены командами (см. [Команды](#команды))

## Конфигурация

### Пример config.yml

```yaml
scoreboard:
  title: "&#e0b11eP&#e59419i&#ea7614l&#f0590fl&#f53b0aa&#fa1e05r&#ff0000s"
  display-condition: "%player-version-id%>=765;%bedrock%=false"
  lines:
    - "&fНик: &#1aa3fb&l%player_name%"
    - "&fТитул: %javascript_suffix%"
    - "&fГруппа: %javascript_group%"
    - "&f"
    - "Всего убийств: &6%playerstats_kills%"
    - "Всего побед: &6%playerstats_wins%"
    - ""
    - "&e"
    - "&fРубинов: &6%playerpoints_points_formatted% ♮"

npc:
  selector:
    skin: "1_QUANTIK_1"
  fastgame:
    skin: "1_QUANTIK_1"

gameplay:
  min-players: 3                # Минимум игроков для старта
  countdown-duration: 30        # Обратный отсчёт, секунды
  pillar-height: 20             # Высота столбов
  item-drop-interval: 10        # Интервал выдачи предметов, секунды
  winner-return-delay: 5        # Задержка возврата победителя, секунды
  arena-clear-max-height: 150   # Максимальная высота очистки (не используется после оптимизации)
```

### Настройка арены

Для каждой арены в `config.yml` создаётся секция:

```yaml
arenas:
  arena1:
    spawnLocation:
      world: "arena_arena1"
      x: 0.5
      y: 100.0
      z: 0.5
      yaw: 0.0
      pitch: 0.0
    enabled: true
    pillars_radius: 10          # Радиус круга столбов
    worldborder_size: 50        # Начальный размер границы
    worldborder_duration: 600   # Время сжатия границы, секунды
    playerLimit: 8              # Максимум игроков
```

## Команды

Все команды начинаются с `/pillars`:

### Для игроков

| Команда | Описание | Права |
|---------|----------|-------|
| `/pillars join` | Присоединиться к случайной арене | `pillars.commands.pillars.join` (default: true) |
| `/pillars quit` | Покинуть арену | `pillars.commands.pillars.quit` (default: true) |

### Для администраторов

| Команда | Описание | Права |
|---------|----------|-------|
| `/pillars create <название>` | Создать арену | `pillars.commands.pillars.create` (default: op) |
| `/pillars delete <название>` | Удалить арену* | `pillars.commands.pillars.delete` (default: op) |
| `/pillars setenabled <название> <true\|false>` | Включить/выключить арену | `pillars.commands.pillars.setenabled` (default: op) |
| `/pillars setlobby` | Установить точку лобби | `pillars.commands.pillars.setlobby` (default: op) |
| `/pillars setselecternpc` | Создать NPC выбора арены | `pillars.commands.pillars.setnpc` (default: op) |
| `/pillars setfastgamenpc` | Создать NPC быстрой игры | `pillars.commands.pillars.setnpc` (default: op) |
| `/pillars settoppos` | Установить позицию голограммы топ-10 | `pillars.commands.pillars.settoppos` (default: op) |
| `/pillars setstatspos` | Установить позицию голограммы статистики | `pillars.commands.pillars.setstatspos` (default: op) |
| `/pillars reload` | Перезагрузить плагин | `pillars.commands.pillars.reload` (default: op) |
| `/pillars info` | Информация о плагине | (нет ограничений) |

*Примечание: удаление арены блокируется, если на ней находятся игроки или идёт игра.

## Интеграция с плагинами

### Citizens

Создайте двух NPC командами `/pillars setselecternpc` (выбор арены) и `/pillars setfastgamenpc` (быстрая игра). Скины настраиваются в `config.yml`.

### PlaceholderAPI

Плагин регистрирует плейсхолдеры:

- `%playerstats_wins%` — побед
- `%playerstats_losses%` — поражений
- `%playerstats_kills%` — убийств
- `%playerstats_winlossratio%` — W/L ratio
- `%playerstats_killdeathkratio%` — K/D ratio

### HolographicDisplays

Автоматически создаются голограммы:
- **Топ-10 игроков** — обновляется после каждой игры (позиция: `/pillars settoppos`)
- **Личная статистика** — индивидуальная для каждого игрока (позиция: `/pillars setstatspos`)

### PlayerPoints

Победитель получает 5 очков, проигравшие — по 1 очку.

## База данных

Статистика хранится в SQLite (`player_stats.db`):

- `player_name` — никнейм игрока (PRIMARY KEY)
- `wins` — количество побед
- `losses` — количество поражений
- `kills` — количество убийств

Все запросы к БД выполняются асинхронно через `ExecutorService`. Результаты кэшируются в `ConcurrentHashMap` для быстрого доступа из scoreboard/PlaceholderAPI.

## Структура проекта

```
src/main/java/dev/quantik/pillars/
├── Pillars.java              – главный класс, команды, listeners
├── Arena.java                – логика арены, worldborder, pillars, reset
├── DatabaseManager.java      – асинхронная работа с SQLite + кэш
├── LobbyScoreboard.java      – scoreboard в лобби с поддержкой PlaceholderAPI
├── ArenaScoreboard.java      – scoreboard во время игры
├── TopPlayersHologram.java   – голограмма топ-10 игроков
├── PlayerStatsPlaceholder.java – PlaceholderAPI expansion
├── PlayerStatsUpdater.java   – обновление голограмм при изменении статистики
├── DatabaseUpdateListener.java – интерфейс listener'ов БД
├── LobbyListener.java        – события в лобби
└── Const.java                – константы (забаненные блоки)
```

## Сборка

Требуется Maven 3.6+:

```bash
mvn clean package
```

Результат: `target/Pillars-<version>.jar`

## Поддержка

- Issues: [GitHub Issues](https://github.com/1Dwade1/PillarsPlugin/issues)
