# WaterPhysics

High-performance realistic water physics plugin for Paper 1.21+.

Water behaves like a finite, mass-conserving fluid: what you pour in is what
pours out. No duplication, no vanishing source blocks, no stranded films.

---

## How the flow model works

Each water cell holds an integer number of **units** (1–8):

| Units | Meaning              | Minecraft level |
|-------|----------------------|-----------------|
| 8     | source (full block)  | 0               |
| 1–7   | flowing block        | `8 - units`     |
| 0     | air / empty          | —               |

Every operation only ever **moves** units between cells — it never creates or
destroys them. Total volume is conserved (except deliberate puddle evaporation,
see below).

Each processed water cell, per tick:

1. **Gravity** — push as many units as fit straight down.
2. **Adjacent drain** — pour into any neighbour with a drop below it
   (a lower floor), cascading down the column as a waterfall — never leaving
   a film hanging in mid-air.
3. **Drain seeking** — if sitting on a flat plane, breadth-first search
   (up to `MAX_FLOW_NODES = 1024` cells) for the nearest spot water could
   fall from, and crawl one unit toward it. Guarantees a body fully empties
   whenever *any* reachable lower spot exists.
4. **Equalize** — in a fully enclosed basin, level out with neighbours,
   stopping at a 1-unit gradient so flat puddles stay stable (no oscillation).
5. **Puddle removal** — a tiny film that cannot move anywhere is evaporated
   (configurable).

When a cell's level changes, **all six face-neighbour water blocks are
re-queued**, so fuller neighbours re-evaluate and flow in. This makes the
whole body drain progressively, ring by ring, over successive ticks — instead
of moving once and freezing.

---

## Changelog

### v1.3.0 — Mass-conserving rewrite
- ✅ **Rewritten flow engine** on a conserving unit model — pour volume equals
  drain volume, no duplication, no leftover sources
- ✅ **Neighbour wake on level change** — water no longer "hangs" after the
  first ring; the whole body drains gradually
- ✅ **Drain-seeking BFS** — water on a flat plane finds and flows toward the
  nearest reachable drop, fully emptying toward an outlet
- ✅ **Waterfall cascade** (`addWaterFalling`) — sideways flow over a ledge
  falls down the column; no floating one-layer films left in mid-air
- ✅ **Puddle evaporation** (`flow.remove-puddles`) — stranded thin films with
  nowhere to flow are removed
- ✅ **Explosion support** — `EntityExplodeEvent` / `BlockExplodeEvent` (TNT,
  creeper, bed/anchor) trigger water flow into the crater
- ✅ **Per-world whitelist fixed** — disabled worlds now keep 100% vanilla
  water (`BlockPhysicsEvent` no longer suppressed outside managed worlds)

### v1.2.0 — Interaction-only mode + optimizations
- ✅ **Interaction-only mode** (`flow.interaction-only: true`) — water flows
  ONLY on player interaction (place/break). Static water (oceans, rivers) is
  not processed, saving CPU
- ✅ **Biome exclusion** (`optimization.excluded-biomes`) — disable physics in
  specific biomes. Per-4×4×4 section cache for fast lookups
- ✅ **Bucket mechanics** (`bucket.enabled`) — placing a water bucket re-queues
  blocks in radius so water can spread from the new point
- ✅ **`Block.breakNaturally()`** for replaceable blocks (moss carpet, carpets,
  grass) when flooded — drops items instead of vanishing
- ✅ **Sounds** (`sounds.enabled`) — `BLOCK_WATER_AMBIENT` with per-chunk rate
  limit, random pitch ±0.2
- ✅ **Lombok** for `@Getter`/`@RequiredArgsConstructor`
- ✅ **Shadow 9.0.0** for Java 25 support (class version 69)

### v1.1.0 — Waterlogged + chunk continuity
- ✅ **ChunkListener** — water blocks re-queued on chunk load. Critical for the
  "hole in the ocean + chunk unloaded" scenario
- ✅ **Waterlogged support** — stairs, slabs, fences and any waterloggable block
  auto waterlog/unwaterlog based on nearby water level
  (`waterlogged.max-level`)
- ✅ **TYPE_WATERLOGGED** in BlockStateCache — distinguishes water blocks from
  waterlogged solid blocks
- ✅ **World height bounds cache** — `getMinHeight()`/`getMaxHeight()` cached
  once per world

---

## Performance design

Rewritten from the original `RealisticWater` for low overhead:

- ✅ **Main thread only** — no async, Paper-safe Block access, zero locking
- ✅ **Caffeine LRU cache** with `long` keys (no `Block` boxing)
- ✅ **Queue deduplication** via per-world `HashSet<Long>` — each block queued
  at most once before processing
- ✅ **PlayerChunkCache** — O(1) proximity check instead of per-block `sqrt()`
- ✅ **Static offset arrays**, reused BFS scratch sets (no per-call allocation)
- ✅ **Config values cached as primitives** — zero YAML lookups while running
- ✅ **Per-world height bounds cached** — no repeated world dispatch per block

---

## Project structure

```
WaterPhysics/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
│
└── src/main/
    ├── resources/
    │   ├── plugin.yml
    │   └── config.yml
    │
    └── java/ru/deelter/waterphysics/
        ├── WaterPhysics.java            ← entry point (JavaPlugin)
        │
        ├── config/
        │   └── PluginConfig.java        ← all config values as primitives
        │
        ├── cache/
        │   ├── BlockStateCache.java     ← Caffeine cache (type + level per block)
        │   └── PlayerChunkCache.java    ← active-chunk cache, O(1) proximity
        │
        ├── engine/
        │   ├── WaterQueue.java          ← ArrayDeque + HashSet<Long> dedup
        │   └── FlowEngine.java          ← main flow loop (main thread)
        │
        ├── listener/
        │   ├── WaterEventListener.java  ← cancels vanilla BlockFromTo/Physics
        │   ├── BlockListener.java       ← break/place/explosion → re-queue
        │   ├── ChunkListener.java       ← rescan water on chunk load
        │   └── BucketListener.java      ← bucket placement re-queue
        │
        ├── command/
        │   └── WaterCommand.java        ← /wp reload|enable|disable|stop|status
        │
        └── util/
            └── BlockKey.java            ← encode (x,y,z) → long for cache keys
```

---

## Build

```bash
# Build the shaded JAR (bundles Caffeine)
./gradlew shadowJar

# Output: build/libs/WaterPhysics-1.0.0.jar
# Copy into your server's plugins/ folder
```

Requires Java 21+ on the build machine.

---

## Configuration (`config.yml`)

All values are read **once** at startup and stored as primitive fields. The
YAML is not read again while running — apply changes with `/wp reload`.

### Core

```yaml
enabled: true                              # Global toggle

worlds:                                    # Whitelist of worlds with physics.
  - "*"                                    # "*" (or empty) = all worlds.
                                           # Otherwise list names: ["world", ...]
                                           # Worlds NOT listed stay 100% vanilla.

flow:
  interaction-only: true                   # Water flows ONLY on player break/place.
                                           # Static water is not processed (saves CPU).

  batch-size: 512                          # Blocks processed per tick (256–1024).

  tick-interval: 3                         # Ticks between engine runs (1 = every tick).

  remove-puddles: true                     # Evaporate stranded thin films with
                                           # nowhere to flow.

  puddle-max-units: 1                      # Max units a film may have to count as a
                                           # removable puddle. 1 = thinnest layer only.
```

### Optimization

```yaml
optimization:
  player-proximity-check: true             # Process only chunks near players.
  player-proximity-chunks: 4               # Radius in chunks (4 ≈ 9×9 chunks).
  cache-ttl-seconds: 60                    # Cache entry validity without access.
  cache-max-size: 100000                   # Max cached block states (LRU).
  chunk-rescan-on-load: true               # Re-queue water on chunk load.
  chunk-scan-max-blocks: 2000              # Max water blocks re-queued per rescan.
  excluded-biomes:                         # Biomes with physics fully disabled.
    - ocean
    - deep_ocean
    - river
```

### Mechanics

```yaml
waterlogged:
  enabled: true                            # Auto waterlog/unwaterlog solid neighbours.
  max-level: 3                             # Water level (0=source..7=thin) that
                                           # triggers waterlogging.

sea-plants:
  remove-on-flow: true                     # Replace seagrass/kelp with water (drops items).
  prevent-growth: true                     # Stop sea plants growing/spreading.

bucket:
  enabled: true                            # Re-queue water on bucket placement.
  scan-radius: 8                           # Radius in blocks around placement.

lava:
  convert-to-cobblestone: true             # Flowing lava → cobblestone on contact.
  convert-source-to-obsidian: true         # Lava source → obsidian on contact.

sounds:
  enabled: true                            # Play BLOCK_WATER_AMBIENT on flow.
  rate-limit-ticks: 10                     # Min ticks between sounds per chunk.
  volume: 0.35                             # 0.0–1.0
  pitch: 1.0                               # Base pitch (±0.2 random)
```

---

## Commands

```
/wp                      # Help
/wp reload               # Reload config.yml
/wp enable               # Enable physics
/wp disable              # Disable physics
/wp stop                 # Clear the queue
/wp status               # Status + queue size

Aliases: /waterphysics, /water
Permission: waterphysics.admin (op by default)
```

---

## Event listeners

**WaterEventListener** — intercepts vanilla water physics
- Cancels `BlockFromToEvent` and `BlockPhysicsEvent` in managed worlds
- Leaves non-whitelisted worlds fully vanilla
- Prevents sea-plant growth/spread (config)

**BlockListener** — syncs cache on player build/break and explosions
- Break/place: invalidate + re-queue water neighbours
- `EntityExplodeEvent` / `BlockExplodeEvent`: re-queue neighbours of every
  destroyed block so water flows into the crater

**ChunkListener** — re-queues water on chunk load
- Scans the chunk for WATER blocks (guarded by `chunk-scan-max-blocks`)
- No effect in interaction-only mode

**BucketListener** — updates water on bucket placement
- `PlayerBucketEmptyEvent`, deferred 1 tick (placed water not in world yet)
- Re-queues blocks within `scan-radius`

---

## FAQ

**Q: Why main thread instead of async?**
A: Paper does not guarantee thread-safe Block access. Main thread = simple,
safe, fast path with no locking or context-switch cost.

**Q: Why `long` keys instead of `Block` objects?**
A: `Block.equals()`/`hashCode()` are expensive; a `long` key is a single
comparison with zero boxing in Caffeine.

**Q: Water still hangs in mid-air after old saves.**
A: The fix prevents *creation* of floating water. Pre-existing floats only fall
when re-processed — disturb a neighbour, or set `interaction-only: false` +
`chunk-rescan-on-load: true`, reload, and revisit the area.

**Q: Plugin lags.**
A: Lower `batch-size`, raise `tick-interval`, or reduce
`player-proximity-chunks`.

**Q: Memory grows.**
A: Lower `cache-max-size` (e.g. 50k) or `cache-ttl-seconds` (e.g. 30).

---

## Known limitations

- **No hydrostatic pressure / up-flow.** Communicating vessels separated by a
  solid wall (two pools at equal surface with no path over the top within the
  drain-search range) will not equalize through the wall. Same-plane bodies and
  drops/ledges work fully.

---

## Requirements

- Java 21+ (Java 25 supported via Shadow 9.0.0)
- Paper 1.21.3+
- Gradle 8.0+ (build only)

---

## License

Unspecified (currently: no usage restrictions).
```
