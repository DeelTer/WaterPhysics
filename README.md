# WaterPhysics

Высокооптимизированный плагин реалистичной физики воды для Paper/Spigot 1.21+.

## Changelog

### v1.1.0 — Waterlogged + chunk continuity
- ✅ **ChunkListener** — при загрузке чанка все водные блоки ре-квеуются. Критично для сценария "дыра в океане + чанк выгрузился"
- ✅ **Waterlogged поддержка** — ступеньки, плиты, заборы и любые waterloggable блоки автоматически waterlog/unwaterlog по уровню воды рядом. Конфигурируемый порог (`waterlogged.max-level`)
- ✅ **TYPE_WATERLOGGED** в BlockStateCache — водяные блоки отличаются от waterlogged solid блоков. Нет двойного `world.getBlockAt()` при cache miss
- ✅ **Кеш высот мира** — `world.getMinHeight()` / `getMaxHeight()` вызывается один раз per-world, не per-block  
- ✅ **Исправлен bug** — `startEngine()` не вызывался при `enabled: false` в config
- ✅ **Разделены** `spreadLevel()`/`nextBlockdata()` — поведение flowSideways теперь читаемо  
- ✅ **`mergeLevels()`** вынесен в отдельный метод — используется в обоих местах flowDown
- ✅ **6-направленный waterlog check** — NX6/NY6/NZ6 arrays для проверки всех соседей

---

## История переписки

**Оригинальный плагин** (RealisticWater v1.9.11):
- Использовал Block объекты как ключи в HashMap → дорогие `equals()` / `hashCode()`
- Per-block вызовы `isNear()` с проверкой расстояния до каждого онлайн игрока → N × M sqrt() операций
- `ArrayList` + `Collections.shuffle()` каждый вызов `getSurroundingBlocks()`
- `arTinka()` использовал `.toString()` и string compare вместо enum switch
- Нет дедупликации очереди — один блок мог быть заквеучен 10+ раз за тик
- Async thread читал Block данные напрямую (unsafe в Paper)
- `HashSet<Block>` allocations в каждой рекурсивной функции
- Нет лимита на размер cache → memory leak при длительной игре

**Новая версия (WaterPhysics v1.0.0)**:
- ✅ Полная переписка на Gradle KTS
- ✅ Java 21, Paper 26.1.2
- ✅ Caffeine LRU cache с `long`-ключами (вместо Block)
- ✅ Главный поток (zero async overhead)
- ✅ Дедупликация очереди через `HashSet<Long>` per world
- ✅ PlayerChunkCache — O(1) проверка вместо sqrt() per block
- ✅ Static массивы offsets, reused HashSet в рекурсии
- ✅ Конфигурируемые параметры, кешированные в примитивы
- ✅ Graceful shutdown — drain очереди перед выключением

---

## Структура проекта

```
WaterPhysics/
├── build.gradle.kts                     ← сборка
├── settings.gradle.kts
├── .gitignore
├── README.md                            ← этот файл
│
└── src/main/
    ├── resources/
    │   ├── plugin.yml                   ← метаданные плагина
    │   └── config.yml                   ← конфиг (примеры значений)
    │
    └── java/ru/deelter/waterphysics/
        ├── WaterPhysics.java            ← точка входа (JavaPlugin)
        │
        ├── config/
        │   └── PluginConfig.java        ← все значения конфига как примитивы
        │                                   (обновляется только при reload)
        │
        ├── cache/
        │   ├── BlockStateCache.java     ← Caffeine кеш (type + level per block)
        │   │                              uses BlockKey для long-кодирования
        │   │                              per-world LRU с TTL и size limit
        │   │
        │   └── PlayerChunkCache.java    ← кеш активных chunk'ов
        │                                   обновляется каждые 20 тиков
        │                                   позволяет O(1) proximity check
        │
        ├── engine/
        │   ├── WaterQueue.java          ← ArrayDeque + HashSet<Long> дедуп
        │   │                              гарантирует: блок в очереди ≤1 раз
        │   │
        │   └── FlowEngine.java          ← главный цикл обработки воды
        │                                   BukkitRunnable (main thread)
        │                                   processBlock() → flowDown/flowSideways
        │                                   рекурсивные функции для level-6/7
        │
        ├── listener/
        │   ├── WaterEventListener.java  ← отменяет vanilla BlockFromTo
        │   │                              registers water в queue
        │   │                              prevent seagrass growth (config)
        │   │
        │   └── BlockListener.java       ← cache invalidation при break/place
        │                                   re-queue соседей воды
        │
        └── command/
            └── WaterCommand.java        ← /wp reload|enable|disable|stop|status
                                            TabCompletion поддержка

        ├── util/
        │   └── BlockKey.java            ← encode (x,y,z) → long
                                            для use as HashMap key
```

---

## Сборка

```bash
# Build JAR с shade Caffeine
./gradlew shadowJar

# Выходной файл:
# build/libs/WaterPhysics-1.0.0.jar

# Копировать в plugins/
cp build/libs/WaterPhysics-1.0.0.jar /path/to/server/plugins/
```

Java 21+ требуется на машине сборки.

---

## Конфиг (`config.yml`)

Все значения читаются **один раз** при старте и сохраняются как примитивные поля в `PluginConfig`. При работе плагина YAML не читается — прямые field-доступы.

### Основные параметры

```yaml
enabled: true                              # Глобальный флаг

worlds:
  - "*"                                    # "*" = все миры
                                           # Или список: ["world", "world_nether"]

flow:
  batch-size: 256                          # Сколько блоков обработать за тик
                                           # Выше = быстрее вода, но больше CPU
                                           # Рекомендация: 128-512

  tick-interval: 1                         # Тики между запусками двигателя
                                           # 1 = каждый тик, 2 = через раз
```

### Оптимизация

```yaml
optimization:
  player-proximity-check: true             # Обработка только рядом с игроками
                                           # true = экономия CPU, false = везде

  player-proximity-chunks: 4               # Radius в chunk'ах (4 = 9x9)

  cache-ttl-seconds: 60                    # Кеш-запись невалидна через N сек
                                           # Выше = меньше world reads, больше RAM

  cache-max-size: 100000                   # Max block'ов в памяти (LRU)
                                           # 100k ≈ 3-4 MB, автоматический evict
```

### Механика

```yaml
sea-plants:
  remove-on-flow: true                     # Заменять seagrass/kelp на воду

  prevent-growth: true                     # Запретить рост растений

lava:
  convert-to-cobblestone: true             # Лава → булыжник при контакте
  convert-source-to-obsidian: true         # Лава-источник → обсидиан
```

---

## Команды

```
/wp                      # Справка
/wp reload               # Перезагрузить config.yml
/wp enable               # Включить физику
/wp disable              # Отключить физику
/wp stop                 # Очистить очередь
/wp status               # Статус + size очереди

Алиасы: /waterphysics, /water
Требуется: waterphysics.admin (опер по дефолту)
```

---

## Архитектура

### BlockKey кодирование

```java
long key = BlockKey.of(x, y, z);

// Бит-раскладка (64 bits):
// [X: 26 bits] [Y: 12 bits] [Z: 26 bits]
//
// Почему:
// - x,z: ±33M (достаточно для MC ±30M)
// - y: ±2k (MC: -64..320)
// - Result: primitive long ключ для HashMap/LRU
// - Ноль boxing, ноль heap allocation на lookup
```

### BlockStateCache (Caffeine)

```
Per-world:
  - typeCache<Long, Byte>   → материал (WATER/AIR/LAVA/PLANT/OTHER)
  - levelCache<Long, Byte>  → уровень воды (0-7)

Значения кешированы как Byte:
  - JVM intern's Byte(-128..127) → всегда те же объекты
  - cache.put(key, (byte)0) == cache.getIfPresent(key)
  - Ноль allocation на hit

TTL + LRU:
  - expireAfterAccess(60s)  → inactive блоки выгружаются
  - maximumSize(100k)       → bounded memory, LRU eviction
```

### WaterQueue дедуп

```java
// Оригинал: ConcurrentLinkedQueue → блок может быть N раз

// Новое:
ArrayDeque<Entry>              + Map<UUID, Set<Long>>
   ↓                                         ↓
 FIFO очередь                    per-world дедуп-гвард

boolean enqueue(world, x, y, z) {
    long key = BlockKey.of(x, y, z);
    return dedup.add(key);  // true if NEW, false if DUP
}
```

### FlowEngine (Main thread)

```
run() каждые N тиков:
  1. update player chunks (каждые 20 тиков)
  2. poll batch-size blocks из queue
  3. for each block:
     - проверить: loaded, enabled, near player, is water
     - if down = AIR/PLANT → flowDown()
     - else → flowSideways()
     - if level == 6 → giveToSevenAir()  (рекурсия)
     - if level == 7 above source → givefromSevenUp() (рекурсия)

Нет async!
- Все world reads синхронны (на main thread)
- Cache synchronized единственно читается
- Paper-safe: нет конкурентного доступа к Block'ам
```

### Event listeners

**WaterEventListener** → перехватывает vanilla BlockFromTo
- Отменяет водопад
- Кеширует блоки
- Кушает в очередь

**BlockListener** → синхронизирует кеш
- На break: invalidate + re-queue соседей
- На place: preload + re-queue соседей

---

## Модификация логики

### Добавить конфиг опцию

1. Добавить в `config.yml`:
   ```yaml
   my-feature:
     my-param: true
   ```

2. Добавить field + getter в `PluginConfig`:
   ```java
   private final boolean myParam;
   
   public PluginConfig(FileConfiguration cfg) {
       this.myParam = cfg.getBoolean("my-feature.my-param", true);
   }
   
   public boolean isMyParam() { return myParam; }
   ```

3. Использовать в `FlowEngine`:
   ```java
   private final boolean myParam;  // cache locally
   
   public FlowEngine(...) {
       this.myParam = config.isMyParam();
   }
   ```

### Добавить новую механику (например, grass spread)

1. Создать `GrassSpreadEngine extends BukkitRunnable`
2. Создать listener, ловящий нужные события → queue'ить блоки
3. В `WaterPhysics.onEnable()`:
   ```java
   grassEngine = new GrassSpreadEngine(config, cache, queue);
   grassTask = grassEngine.runTaskTimer(this, 20L, 20L);
   ```

---

## Безопасность при перезагрузке

При `/stop` или crash сервера:

```java
// WaterPhysics.onDisable()
while (!queue.isEmpty() && drained++ < 10_000) {
    WaterQueue.Entry entry = queue.poll();
    flushBlockFromCache(entry.world(), ...);  // apply cache → world
}
```

**Результат:** не остается висячей воды в неправильных состояниях.

---

## Производительность

### На что обратить внимание

| Метрика | Почему | Tunning |
|---|---|---|
| Cache hit rate | все мелкие значения из Caffeine | `cache-ttl-seconds` выше = меньше miss, но RAM |
| Queue size | дедуп гарантирует N блоков, не 10N | batch-size выше = меньше queue length |
| Chunk updates | 20 тиков между обновлением proximity | хардкод; может быть параметр если нужен |
| Player distance | ~64 chunks по config | `player-proximity-chunks` tuneable |

### Профилирование

Добавить в `FlowEngine.run()`:
```java
long start = System.nanoTime();
// ... обработка
long elapsed = System.nanoTime() - start;
if (elapsed > 5_000_000) {  // >5ms
    getLogger().warning("Slow tick: " + (elapsed / 1_000_000) + "ms");
}
```

---

## Часто задаваемые вопросы разработчику

**Q: Почему main thread, а не async?**
A: Paper не гарантирует thread-safe доступ к Block данным. Async требует ConcurrentHashMap, синхронизацию, затраты на context switch. Main thread = простой, безопасный, fast path.

**Q: Почему long ключи вместо Block объектов?**
A: Block.equals() вычисляет хеш всего мира + координаты. Long.equals() = one instruction. Ноль boxing в Caffeine.

**Q: Как добавить свою recursive функцию вроде giveToSevenAir()?**
A: Pre-allocate `HashSet<Long> visited` в поле, clear() перед первым вызовом, передавать в рекурсию. Никаких new HashSet() в цикле.

**Q: Плагин зависает. Как debug?**
A: Добавить System.nanoTime() в начало processBlock(). Логировать медленные блоки. Снизить batch-size в конфиге.

**Q: Память растет. Что делать?**
A: `cache-max-size` уменьшить (например, до 50k). Или `cache-ttl-seconds` понизить (например, до 30).

---

## Требования

- Java 21+
- Paper/Spigot 1.21.0+
- Gradle 8.0+ (для сборки)

---

## Версионирование

Семантический версинг:
- `1.0.0` — релиз, полная стабильность
- `1.x.0` — новые фичи (механика, конфиги)
- `1.0.x` — баги, оптимизация
- `2.0.0` — breaking API change

---

## Лицензия

Указать при публикации (сейчас: нет ограничений на использование).
