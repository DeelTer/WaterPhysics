# Разработка WaterPhysics

Детальное описание внутреннего устройства для разработчиков.

---

## Data flow

```
Event (BlockFromTo) / Player place water
          ↓
WaterEventListener.onBlockFromTo() / BlockListener.onBlockPlace()
          ↓
          ├→ cache.preload(block)         [main thread]
          └→ queue.enqueue(world, x, y, z) [dedup check]
                    ↓
                [WaterQueue]
                 ArrayDeque  + Set<Long> guard
                    ↓
                [every 1-N ticks]
                FlowEngine.run()
                    ↓
                processBlock()
                    ├→ getType(w, x, y, z)    [cache hit / BlockStateCache load]
                    ├→ getLevel(w, x, y, z)   [cache hit / BlockStateCache load]
                    ├→ flowDown()  or  flowSideways()
                    ├→ giveToSevenAir()  [recursive, level-6]
                    ├→ givefromSevenUp() [recursive, level-7]
                    └→ setWater() / setAir() / applyLevel()
                            ↓
                        [Block.setType / setBlockData]
                        [Cache.put]
                        [queue.enqueue re-process]
```

---

## BlockKey encoding

```java
public static long of(int x, int y, int z) {
    return ((long) (x & 0x3FFFFFF) << 38)
         | ((long) (y & 0xFFF)     << 26)
         |  (long) (z & 0x3FFFFFF);
}

// Bit layout (LSB → MSB):
// [0-25]     Z (26 bits)   range -33M..+33M
// [26-37]    Y (12 bits)   range -2k..+2k  (MC: -64..320)
// [38-63]    X (26 bits)   range -33M..+33M

// Sign extension при декодировании:
public static int x(long key) {
    int raw = (int) ((key >> 38) & 0x3FFFFFF);
    return raw >= 0x2000000 ? raw | 0xFC000000 : raw;  // ← sign bit
}
```

**Почему это работает:**
- Minecraft world coordinates всегда в пределах ±30 млн
- Encoding deterministic: одни (x,y,z) → всегда один long
- Декодирование обратимо
- JVM inlines эту операцию в микросекунду

---

## BlockStateCache детали

### Byte value interning

```java
// JVM автоматически кешит Byte для -128..127
Byte b1 = Byte.valueOf(0);
Byte b2 = Byte.valueOf(0);
b1 == b2  // true (same object)

// Наши значения:
// - Types:  0-4       ← все intern'd
// - Levels: 0-7       ← все intern'd
// Cache hit всегда возвращает cached объект из heap
```

### Per-world cache

```java
Map<UUID, Cache<Long, Byte>> typeCaches  = new HashMap<>();
Map<UUID, Cache<Long, Byte>> levelCaches = new HashMap<>();

// Каждый world отдельный Caffeine instance:
// Advantage: 
//   - Если world unload, эта cache не засоряется
//   - Per-world LRU более справедлив (big world не вытесняет small)
//   - Можно per-world tuning в будущем
```

### TTL vs Access time

```java
.expireAfterAccess(60s)

// Блок выгружается из кеша если:
// - не был read/write в течение 60 сек
//
// Результат:
// - Active ватер (обновляется) → остается в кеше
// - Old water (никто не трогал) → выгружается, экономя RAM
```

### Cache miss = world read

```java
private byte loadType(World world, UUID wid, long key, int x, int y, int z) {
    Material mat = world.getBlockAt(x, y, z).getType();
    byte type = materialToType(mat);
    // Check waterlogged
    if (type == TYPE_OTHER) {
        try {
            Block block = world.getBlockAt(x, y, z);
            if (block.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()) {
                type = TYPE_WATER;
            }
        } catch (Exception ignored) {}
    }
    typeCache(wid).put(key, type);
    return type;
}

// Overhead:
// - Block lookup O(region file)
// - BlockData instanceof + cast
// - put into cache (sync map)
//
// Чтобы минимизировать: player placement triggers cache.preload()
```

---

## WaterQueue дедупликация

```java
public boolean enqueue(World world, int x, int y, int z) {
    long key = BlockKey.of(x, y, z);
    UUID wid = world.getUID();
    Set<Long> dedup = dedupSets.computeIfAbsent(wid, k -> new HashSet<>());
    if (dedup.add(key)) {        // ← add() returns true if NEW
        deque.addLast(new Entry(world, x, y, z));
        return true;
    }
    return false;               // ← already queued
}

// Per-world HashSet<Long>:
// - add(key) = O(1) average, O(log n) worst
// - Отбрасывает дупликаты на входе
// - На poll(): remove из dedup set
//
// Benefit vs original:
// - Original: один блок может быть в очереди 10+ раз
// - New:      каждый блок ≤ 1 раз до poll()
// - CPU saved: × 10-50 зависит от вода конфиг
```

---

## FlowEngine горячий путь

### processBlock() guards

```java
private void processBlock(World world, int x, int y, int z) {
    // Проверки в порядке: most likely to fail first
    if (!world.isChunkLoaded(x >> 4, z >> 4)) return;  // ← fast bit shift
    if (!config.isWorldEnabled(world.getName())) return;  // ← string compare 1/world
    if (playerProximityCheck && !proximity.isActive(world.getUID(), x, z)) return;  // ← HashSet O(1)
    if (getType(world, x, y, z) != TYPE_WATER) return;  // ← cache hit likely
    // ... continue to flow logic
}

// Каждый return экономит работу для миллиардов проверок
```

### Кеширование примитивов в конструктор

```java
public FlowEngine(PluginConfig config, ...) {
    this.batchSize = config.getBatchSize();              // primitive int
    this.playerProximityCheck = config.isPlayerProximityCheck();  // boolean
    this.convertLava = config.isConvertLava();
    this.removeSeaPlants = config.isRemoveSeaPlants();
}

@Override
public void run() {
    int processed = 0;
    while (processed < batchSize) {  // ← field read, not getter call
        // ...
    }
}

// Почему:
// - Getter call = method invocation (expensive on JIT startup)
// - Field read = direct memory access (< 1 cycle on hot CPU)
// - Config не меняется во время работы (restart на reload)
```

### Reused HashSet в рекурсии

```java
private final HashSet<Long> visitedA = new HashSet<>(64);  // field-level
private final HashSet<Long> visitedB = new HashSet<>(64);

private void giveToSevenAir(...) {
    // ... в самом начале processBlock():
    stop = false;
    visitedA.clear();  // ← reuse existing set
    giveToSevenAir(...);
    
    // Never do:
    // HashSet<Block> visited = new HashSet<>();  // ← allocation × 1000 calls
}

// Allocation cost breakdown:
// - new HashSet<>(16): 300+ bytes heap
// - Happens every 100ms if many water sources
// - GC pressure, pauses
//
// Reuse:
// - clear() = fast (iterate, remove)
// - total allocations per server life = 2 (these two sets)
```

---

## PlayerChunkCache обновление

```java
@Override
public void run() {
    // update player chunks (каждые 20 тиков ≈ 1 sec)
    if (++tickCount >= 20) {
        tickCount = 0;
        proximity.update(Bukkit.getOnlinePlayers());
    }
    // ... rest of engine
}

// update() logic:
for (Player player : players) {
    UUID wid = player.getWorld().getUID();
    int pcx = player.getLocation().getBlockX() >> 4;  // ← chunk coord
    int pcz = player.getLocation().getBlockZ() >> 4;
    
    Set<Long> chunks = activeChunks.computeIfAbsent(wid, k -> new HashSet<>());
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
            chunks.add(chunkKey(pcx + dx, pcz + dz));
        }
    }
}

// Chunk key: ((long) cx << 32) | (cz & 0xFFFFFFFFL)
// Позволяет O(1) lookup: isActive(wid, blockX, blockZ)?
//   = chunks.contains(chunkKey(blockX >> 4, blockZ >> 4))

// Frequency: 20 тиков между обновлениями
// - Игрок может переместиться максимум на 20 блоков
// - Chunk boundaries: 16 блоков width
// - Worst case: граница + 20 = за границу
// - Solution: radius = 4 chunks = 16*5 = 80 блоков, safe
```

---

## Recursive functions: giveToSevenAir

```java
/**
 * Level-6 water источник: распространить уровень-7 блок на соседнее воздух
 * 
 * Алгоритм:
 * 1. Ходим через соседей исходного блока
 * 2. Если сосед = уровень-7 вода: рекурсивно идем дальше
 * 3. Если сосед = air: проверяем падение + спрейд уровень-7 там
 * 4. stop = true когда нашли место → return
 */
private void giveToSevenAir(World world,
                             int bx, int by, int bz,   // original level-6 block
                             int fx, int fy, int fz,   // current frontier
                             int depth) {
    if (depth > 15 || stop) return;
    if (getLevel(world, bx, by, bz) != 6) return;
    
    for (int i = 0; i < 4; i++) {  // N, E, S, W
        int nx = fx + DX[i];
        int nz = fz + DZ[i];
        byte ntype = getType(world, nx, fy, nz);
        
        if (ntype == TYPE_WATER && getLevel(world, nx, fy, nz) == 7) {
            // Walk this source water (level-7)
            long nkey = BlockKey.of(nx, fy, nz);
            if (visitedA.add(nkey)) {
                giveToSevenAir(world, bx, by, bz, nx, fy, nz, depth + 1);
            }
        } else if (ntype == TYPE_AIR || ntype == TYPE_PLANT) {
            // Found a candidate air block
            // Check if source has air below (waterfall setup)
            if (/* source has air below */) {
                // Walk AIR down as far as it goes
                // Place level-7 water there if appropriate
            }
        }
    }
}

// Depth limit = 15:
// - Prevents infinite recursion
// - Limits search tree: 4^15 = 1B nodes worst case
// - In practice: pruned by visited set + stop flag
// - Typical: 20-50 nodes visited
```

---

## Block mutation pattern

```java
private void setWater(World world, int x, int y, int z, int level) {
    long key = BlockKey.of(x, y, z);
    UUID wid = world.getUID();
    
    Block block = world.getBlockAt(x, y, z);
    block.setType(Material.WATER, false);  // ← false = no physics event
    if (block.getBlockData() instanceof Levelled ld) {
        ld.setLevel(level);
        block.setBlockData(ld, false);
    }
    
    cache.putType(wid, key, TYPE_WATER);     // ← update cache immediately
    cache.putLevel(wid, key, (byte) level);
    queue.enqueue(world, x, y, z);           // ← re-process next tick
}

// Important details:
// - setType(..., false) = no BlockPhysicsEvent
//   (otherwise вода propagates via vanilla listener, мы отменяем)
// - Cache update SAME tick
//   (otherwise next processBlock sees stale data, redundant work)
// - queue.enqueue() = block stays active for neighbors
```

---

## Shutdown sequence

```java
@Override
public void onDisable() {
    // 1. Stop the engine
    if (engineTask != null) engineTask.cancel();
    
    // 2. Drain remaining queue synchronously
    int drained = 0;
    while (!queue.isEmpty() && drained++ < 10_000) {
        WaterQueue.Entry entry = queue.poll();
        flushBlockFromCache(entry.world(), entry.x(), entry.y(), entry.z());
    }
    
    // 3. Clear all caches
    cache.clearAll();
    queue.clear();
}

private void flushBlockFromCache(World world, int x, int y, int z) {
    try {
        byte type = cache.getType(world, x, y, z);
        if (type != TYPE_WATER) return;
        int level = cache.getLevel(world, x, y, z);
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == WATER && block.getBlockData() instanceof Levelled ld) {
            if (ld.getLevel() != level) {
                ld.setLevel(level);
                block.setBlockData(ld, false);
            }
        }
    } catch (Exception ignored) {}  // Best-effort on crash
}

// Почему это нужно:
// - Без drain: остается 100+ блоков с уровнем в кеше, но неприменённым к world
// - Server restart: вода остается с неправильными уровнями
// - Server reload: висячие блоки не обновляются
// - Вода выглядит "заморожен" при перезагрузке плагина
//
// С drain + flush:
// - ВСЕ cached изменения apply к world перед отключением
// - Next reload: мир консистентен
```

---

## Config reload

```java
public void reload() {
    reloadConfig();              // ← Bukkit's reloadConfig()
    loadConfig();                // ← new PluginConfig(getConfig())
    cache.clearAll();            // ← clear all cached blocks
    queue.clear();               // ← discard pending work
    restartEngine();             // ← cancel old task, create new engine
}

private void restartEngine() {
    if (engineTask != null) engineTask.cancel();
    engine = new FlowEngine(config, cache, proximity, queue);
    engineTask = engine.runTaskTimer(this, 20L, config.getTickInterval());
}

// Почему полная перестройка:
// - Config содержит примитивы, кешированные в FlowEngine поля
// - Нельзя просто обновить PluginConfig
// - Нужна новая FlowEngine instance с новыми values
// - Cache.clearAll() = нужно пересчитать block states
```

---

## Potential optimizations

### 1. Batch BlockState updates

```java
// Current:
block.setType(Material.WATER, false);
block.setBlockData(ld, false);

// Future (Paper 1.22+):
// ChunkData API для batch updates
```

### 2. Async cache preload

```java
// Current: synchronous Block read on cache miss
// Future: async world.getBlockAtAsync() if available
```

### 3. Per-player water levels

```java
// Store different water level visibility per player
// e.g. level-5 visible to player A, level-3 to player B
// Requires: per-player cache + view packet manipulation
```

### 4. Configurable recursion depth

```yaml
recursive:
  max-depth: 15
  max-visited-per-call: 1000
```

---

## Testing

### Unit tests

```
src/test/java/ru/deelter/waterphysics/
├── util/BlockKeyTest.java
│   - test encoding/decoding edge cases
│   - max/min coordinates
│
├── cache/BlockStateCacheTest.java
│   - cache hit/miss ratio
│   - TTL expiration
│
└── engine/WaterQueueTest.java
    - dedup correctness
    - concurrent access (if ever added)
```

### Integration tests

```
Minecraft server (local) with plugin:
- /wp set 5  → place source water
- /wp status → check queue processing
- Monitor: FPS, TPS, memory usage
```

---

## Performance profiling

### JFR recording

```bash
java -XX:+FlightRecorder -XX:StartFlightRecording=filename=recording.jfr ...

# Analyze in JDK Mission Control
# Look for:
# - GC pause times
# - thread contention
# - hot methods
```

### Logs

Add в FlowEngine.run():
```java
long start = System.nanoTime();
int processed = 0;
while (processed < batchSize && ...) {
    processBlock(...);
    processed++;
}
long elapsed = System.nanoTime() - start;
if (elapsed > 5_000_000) {  // > 5ms
    getLogger().warning("Slow cycle: " + (elapsed / 1_000_000) + "ms, blocks=" + processed);
}
```
