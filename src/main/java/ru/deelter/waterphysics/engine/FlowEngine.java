package ru.deelter.waterphysics.engine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.scheduler.BukkitRunnable;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.cache.PlayerChunkCache;
import ru.deelter.waterphysics.config.PluginConfig;
import ru.deelter.waterphysics.util.BlockKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static ru.deelter.waterphysics.cache.BlockStateCache.*;

/**
 * Core water flow engine.  Runs on the MAIN thread every N ticks.
 *
 * Performance decisions:
 *  - Main thread → no ConcurrentHashMap overhead, no async/sync handoff
 *  - long-keyed Caffeine caches → zero Block object boxing
 *  - Dedup queue → each block queued at most once per cycle
 *  - Static DX/DZ arrays → no ArrayList+shuffle per call
 *  - Pre-reused HashSet for recursion visited tracking
 *  - Player proximity via pre-computed chunk set → no per-block sqrt()
 *  - Config values cached as primitives → zero YAML lookups
 *  - World height bounds cached per-world → no repeated getMinHeight()/getMaxHeight()
 */
public final class FlowEngine extends BukkitRunnable {

    // Horizontal neighbour offsets: N, E, S, W
    private static final int[] DX = { 0,  1,  0, -1};
    private static final int[] DZ = {-1,  0,  1,  0};

    // All 6 face offsets (for waterlogging side-effect check)
    private static final int[] NX6 = { 0,  1,  0, -1,  0,  0};
    private static final int[] NY6 = { 0,  0,  0,  0,  1, -1};
    private static final int[] NZ6 = {-1,  0,  1,  0,  0,  0};

    private final PluginConfig     config;
    private final BlockStateCache  cache;
    private final PlayerChunkCache proximity;
    private final WaterQueue       queue;

    // Reused visited sets — avoids allocation per recursive call
    private final HashSet<Long> visitedA = new HashSet<>(64);
    private final HashSet<Long> visitedB = new HashSet<>(64);

    // Stop flag for recursive spread functions
    private boolean stop;

    // Proximity update counter
    private int tickCount;

    // ---- Cached config primitives (read once, avoid getter call overhead) --
    private final int     batchSize;
    private final boolean playerProximityCheck;
    private final boolean convertLava;
    private final boolean convertLavaSource;
    private final boolean waterlogEnabled;
    private final int     waterlogMaxLevel;
    private final boolean biomeExclusionEnabled;
    private final boolean equalizeWaterLevels;
    private final boolean soundsEnabled;
    private final int     soundRateLimitTicks;
    private final float   soundVolume;
    private final float   soundPitch;

    // Biome exclusion cache: keyed by 4x4x4 section position, value = is excluded.
    // Biomes never change at runtime → safe to cache forever.
    private final Map<Long, Boolean> biomeExcludedCache = new HashMap<>();

    // Sound rate-limit: chunk key → last soundTick when sound played.
    private final Map<Long, Integer> lastSoundTick = new HashMap<>();
    private int soundTick;

    // ---- Per-world height bounds cache -------------------------------------
    // world.getMinHeight() / getMaxHeight() calls dispatch to the world object
    // every time.  Cache them in a HashMap so processBlock pays zero per call.
    private final Map<UUID, int[]> worldBoundsCache = new HashMap<>(); // [0]=min, [1]=max

    public FlowEngine(PluginConfig config, BlockStateCache cache,
                      PlayerChunkCache proximity, WaterQueue queue) {
        this.config              = config;
        this.cache               = cache;
        this.proximity           = proximity;
        this.queue               = queue;
        this.batchSize           = config.getBatchSize();
        this.playerProximityCheck = config.isPlayerProximityCheck();
        this.convertLava         = config.isConvertLava();
        this.convertLavaSource   = config.isConvertLavaSource();
        this.waterlogEnabled     = config.isWaterlogEnabled();
        this.waterlogMaxLevel    = config.getWaterlogMaxLevel();
        this.biomeExclusionEnabled  = !config.getExcludedBiomes().isEmpty();
        this.equalizeWaterLevels    = config.isEqualizeWaterLevels();
        this.soundsEnabled          = config.isSoundsEnabled();
        this.soundRateLimitTicks    = config.getSoundRateLimitTicks();
        this.soundVolume            = config.getSoundVolume();
        this.soundPitch             = config.getSoundPitch();
    }

    @Override
    public void run() {
        soundTick++;
        if (++tickCount >= 20) {
            tickCount = 0;
            proximity.update(Bukkit.getOnlinePlayers());
        }

        if (queue.isEmpty()) return;

        int processed = 0;
        while (processed++ < batchSize) {
            WaterQueue.Entry entry = queue.poll();
            if (entry == null) break;
            processBlock(entry.world(), entry.x(), entry.y(), entry.z());
        }
    }

    // =========================================================================
    //  Main block processing
    // =========================================================================

    private void processBlock(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4))              return;
        if (!config.isWorldEnabled(world.getName()))            return;
        if (playerProximityCheck
                && !proximity.isActive(world.getUID(), x, z))  return;
        if (biomeExclusionEnabled) {
            long bk = BlockKey.of(x >> 2, y >> 2, z >> 2);
            if (biomeExcludedCache.computeIfAbsent(bk, k -> config.isBiomeExcluded(world.getBiome(x, y, z)))) return;
        }
        if (getType(world, x, y, z) != TYPE_WATER)             return;

        int   level   = getLevel(world, x, y, z);
        int   minY    = minY(world);
        int   downY   = y - 1;
        byte  downType = (downY >= minY) ? getType(world, x, downY, z) : TYPE_OTHER;

        boolean fallsDown = downType == TYPE_AIR
                || downType == TYPE_PLANT
                || (downType == TYPE_WATER && getLevel(world, x, downY, z) != 0);

        if (fallsDown) {
            flowDown(world, x, y, z, level);
            return;
        }

        flowSideways(world, x, y, z, level);

        // Re-read after flowSideways may have changed this block
        if (getType(world, x, y, z) != TYPE_WATER) return;
        level = getLevel(world, x, y, z);

        // Level-6 → spread a level-7 surface block into adjacent air
        if (level == 6) {
            stop = false;
            visitedA.clear();
            giveToSevenAir(world, x, y, z, x, y, z, 0);
        }

        // Level-7 above a source → absorb into a nearby non-source
        if (level == 7 && downType == TYPE_WATER && getLevel(world, x, downY, z) == 0) {
            stop = false;
            visitedB.clear();
            givefromSevenUp(world, x, y, z, x, downY, z, 0);
        }

        // Re-queue block above if it has flowing water (propagate upward pressure)
        if (level > 0) {
            int upY = y + 1;
            if (upY <= maxY(world) && getType(world, x, upY, z) == TYPE_WATER) {
                queue.enqueue(world, x, upY, z);
            }
        }
    }

    // =========================================================================
    //  Flow downward
    // =========================================================================

    private void flowDown(World world, int x, int y, int z, int level) {
        // Wake horizontal neighbours for re-evaluation
        for (int i = 0; i < 4; i++) {
            queue.enqueue(world, x + DX[i], y, z + DZ[i]);
        }

        int  minY    = minY(world);
        int  downY   = y - 1;
        byte downType = (downY >= minY) ? getType(world, x, downY, z) : TYPE_OTHER;

        if (downType == TYPE_AIR || downType == TYPE_PLANT) {
            // Walk down through air/plants to find the landing block
            // Safe: x,z unchanged → same chunk column throughout the walk
            int landY = downY;
            while (landY > minY) {
                byte below = getType(world, x, landY - 1, z);
                if (below != TYPE_AIR && below != TYPE_PLANT) break;
                landY--;
            }

            byte landType = getType(world, x, landY, z);

            if (landType == TYPE_WATER) {
                int bottomLevel = getLevel(world, x, landY, z);
                if (bottomLevel != 0) {
                    int[] merged = mergeLevels(level, bottomLevel);
                    if (merged[0] < 8) setWater(world, x, landY + 1, z, merged[0]);
                    setAir(world, x, y, z);
                    applyLevel(world, x, landY, z, merged[1]);
                    return;
                }
            }

            // Solid below — place water at landY
            setWater(world, x, landY, z, level);
            setAir(world, x, y, z);

        } else if (downType == TYPE_WATER && getLevel(world, x, downY, z) != 0) {
            int[] merged = mergeLevels(level, getLevel(world, x, downY, z));
            applyLevel(world, x, y, z, merged[0]);
            applyLevel(world, x, downY, z, merged[1]);
        }
    }

    /**
     * Merge a falling block (from) into an existing water block (size).
     * Returns [newFromLevel, newSizeLevel].
     */
    private static int[] mergeLevels(int from, int size) {
        int needs = 8 - size;
        if (from == needs) {
            return new int[]{8, 0};
        }
        while (size != 0 && from < 8) { size--; from++; }
        return new int[]{from, size};
    }

    // =========================================================================
    //  Flow sideways
    // =========================================================================

    private void flowSideways(World world, int x, int y, int z, int originalLevel) {
        // blockdata mutates across the loop — intentional degradation per spread direction
        int blockdata = originalLevel;

        // Level equalization is only safe when this block has NO open (air/plant) neighbours.
        // If there is an open edge, the block should flow there normally; equalizing would
        // raise neighbour levels, those neighbours re-queue, find air, spread → water dupe.
        // Surrounded-by-water blocks can safely equalise: they cannot spread to air themselves.
        boolean canEqualize = false;
        if (equalizeWaterLevels && blockdata < 7) {
            canEqualize = true;
            for (int i = 0; i < 4; i++) {
                byte t = getType(world, x + DX[i], y, z + DZ[i]);
                if (t == TYPE_AIR || t == TYPE_PLANT) { canEqualize = false; break; }
            }
        }

        for (int i = 0; i < 4; i++) {
            if (blockdata >= 7) break;

            int  nx    = x + DX[i];
            int  nz    = z + DZ[i];
            byte ntype = getType(world, nx, y, nz);

            if (ntype == TYPE_AIR || ntype == TYPE_PLANT) {
                int spreadLevel = spreadLevel(blockdata);
                blockdata       = nextBlockdata(blockdata);
                setWater(world, nx, y, nz, spreadLevel);

            } else if (ntype == TYPE_WATER) {
                int nLevel = getLevel(world, nx, y, nz);
                // Raise less-full neighbour by 1 step — non-destructive, current level unchanged.
                // Guard: only when no open edge exists (canEqualize), so equalized neighbours
                // cannot then spread to air and create extra water.
                if (canEqualize && nLevel > blockdata + 1) {
                    applyLevel(world, nx, y, nz, nLevel - 1);
                }

            } else if (ntype == TYPE_LAVA && convertLava) {
                boolean isSource = getLevel(world, nx, y, nz) == 0;
                Material result  = (isSource && convertLavaSource) ? Material.OBSIDIAN : Material.COBBLESTONE;
                setSolid(world, nx, y, nz, result);
                blockdata++;

            } else if (waterlogEnabled && (ntype == TYPE_OTHER || ntype == TYPE_WATERLOGGED)) {
                // Waterlogging side-effect: update adjacent waterloggable solid blocks
                updateWaterlog(world, nx, y, nz, blockdata);
            }
        }

        if (blockdata != originalLevel) {
            applyLevel(world, x, y, z, blockdata); // applyLevel handles blockdata>=8 → setAir
        }
    }

    /** Spread level → level given to the destination AIR block. */
    private static int spreadLevel(int blockdata) {
        return switch (blockdata) {
            case 0 -> 4;
            case 1 -> 5;
            case 2 -> 5;
            case 3 -> 6;
            case 4 -> 6;
            case 5 -> 7;
            case 6 -> 7;
            default -> 7;
        };
    }

    /** How blockdata degrades after spreading in one direction. */
    private static int nextBlockdata(int blockdata) {
        return switch (blockdata) {
            case 0, 1 -> 4;
            case 2, 3 -> 5;
            case 4, 5 -> 6;
            case 6, 7 -> 8; // thin water consumes itself when it spreads
            default   -> 8;
        };
    }

    // =========================================================================
    //  Level-6 surface spread
    // =========================================================================

    private void giveToSevenAir(World world,
                                 int bx, int by, int bz,
                                 int fx, int fy, int fz,
                                 int depth) {
        if (depth > 15 || stop) return;
        if (getType(world, bx, by, bz) != TYPE_WATER) return;
        if (getLevel(world, bx, by, bz) != 6) return;

        for (int i = 0; i < 4; i++) {
            if (stop) return;
            int  nx    = fx + DX[i];
            int  nz    = fz + DZ[i];
            byte ntype = getType(world, nx, fy, nz);

            if (nx == bx && fy == by && nz == bz) continue;

            if (ntype == TYPE_WATER) {
                if (getLevel(world, nx, fy, nz) != 7) continue;
                long nkey = BlockKey.of(nx, fy, nz);
                if (!visitedA.add(nkey)) continue;
                giveToSevenAir(world, bx, by, bz, nx, fy, nz, depth + 1);

            } else if (ntype == TYPE_AIR || ntype == TYPE_PLANT) {
                int minY = minY(world);
                // Original block has air below → waterfall scenario
                if (minY(world) <= by - 1 && getType(world, bx, by - 1, bz) == TYPE_AIR) {
                    int ty = fy;
                    while (ty > minY) {
                        byte below = getType(world, nx, ty - 1, nz);
                        if (below != TYPE_AIR && below != TYPE_PLANT) break;
                        ty--;
                    }
                    byte landBelow = (ty > minY) ? getType(world, nx, ty - 1, nz) : TYPE_OTHER;
                    if (landBelow == TYPE_WATER) {
                        int ll = getLevel(world, nx, ty - 1, nz);
                        if (ll != 0) {
                            applyLevel(world, nx, ty - 1, nz, ll - 1);
                            stop = true;
                            return;
                        }
                    }
                    setWater(world, nx, ty, nz, 7);
                } else {
                    setWater(world, nx, fy, nz, 7);
                }
                applyLevel(world, bx, by, bz, 7);
                queue.enqueue(world, nx, fy, nz);
                stop = true;
                return;
            }
        }
    }

    // =========================================================================
    //  Level-7 above source → absorb
    // =========================================================================

    private void givefromSevenUp(World world,
                                  int bx, int by, int bz,
                                  int fx, int fy, int fz,
                                  int depth) {
        if (depth > 15 || stop) return;
        if (getType(world, bx, by, bz) != TYPE_WATER) return;

        for (int i = 0; i < 4; i++) {
            if (stop) return;
            int  nx    = fx + DX[i];
            int  nz    = fz + DZ[i];
            long nkey  = BlockKey.of(nx, fy, nz);

            if ((nx == bx && fy == by && nz == bz) || visitedB.contains(nkey)) continue;
            if (getType(world, nx, fy, nz) != TYPE_WATER) continue;

            visitedB.add(nkey);
            int nLevel = getLevel(world, nx, fy, nz);

            if (nLevel == 0) {
                givefromSevenUp(world, bx, by, bz, nx, fy, nz, depth + 1);
            } else {
                applyLevel(world, nx, fy, nz, nLevel - 1);
                setAir(world, bx, by, bz);
                stop = true;
                return;
            }
        }
    }

    // =========================================================================
    //  Waterlogging side-effect
    // =========================================================================

    /**
     * Update the waterlogged state of a solid block based on nearby water level.
     * Called during flowSideways when encountering TYPE_OTHER or TYPE_WATERLOGGED.
     * Only does a full Block read when the state actually needs to change.
     */
    private void updateWaterlog(World world, int nx, int ny, int nz, int nearbyLevel) {
        byte  ntype = cache.getType(world, nx, ny, nz);
        boolean shouldLog = nearbyLevel <= waterlogMaxLevel;

        if (shouldLog && ntype == TYPE_OTHER) {
            // Only do world read if block might be waterloggable
            Block nb = world.getBlockAt(nx, ny, nz);
            BlockData bd = nb.getBlockData();
            if (bd instanceof Waterlogged wl && !wl.isWaterlogged()) {
                wl.setWaterlogged(true);
                nb.setBlockData(wl, false);
                cache.putType(world.getUID(), BlockKey.of(nx, ny, nz), TYPE_WATERLOGGED);
            }
        } else if (!shouldLog && ntype == TYPE_WATERLOGGED) {
            Block nb = world.getBlockAt(nx, ny, nz);
            BlockData bd = nb.getBlockData();
            if (bd instanceof Waterlogged wl && wl.isWaterlogged()) {
                wl.setWaterlogged(false);
                nb.setBlockData(wl, false);
                cache.putType(world.getUID(), BlockKey.of(nx, ny, nz), TYPE_OTHER);
            }
        }
    }

    /**
     * Force-unwaterlog all 6 adjacent blocks when a water block disappears.
     * Called from setAir().
     */
    private void forceUnwaterlogNeighbors(World world, int x, int y, int z) {
        UUID wid = world.getUID();
        for (int i = 0; i < 6; i++) {
            int nx = x + NX6[i];
            int ny = y + NY6[i];
            int nz = z + NZ6[i];
            if (ny < minY(world) || ny > maxY(world)) continue;
            if (cache.getType(world, nx, ny, nz) != TYPE_WATERLOGGED) continue;

            Block nb = world.getBlockAt(nx, ny, nz);
            BlockData bd = nb.getBlockData();
            if (bd instanceof Waterlogged wl && wl.isWaterlogged()) {
                wl.setWaterlogged(false);
                nb.setBlockData(wl, false);
                cache.putType(wid, BlockKey.of(nx, ny, nz), TYPE_OTHER);
            }
        }
    }

    // =========================================================================
    //  Block mutation helpers
    // =========================================================================

    private void setWater(World world, int x, int y, int z, int level) {
        if (y < minY(world) || y > maxY(world)) return;

        long key = BlockKey.of(x, y, z);
        UUID wid = world.getUID();

        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.WATER) {
            // Drop items for replaceable blocks (carpets, moss, plants, etc.) before flooding
            if (!block.getType().isAir() && cache.getType(world, x, y, z) == TYPE_PLANT) {
                block.breakNaturally();
            }
            block.setType(Material.WATER, false);
            tryPlaySound(world, x, y, z);
        }
        if (block.getBlockData() instanceof Levelled ld) {
            ld.setLevel(level);
            block.setBlockData(ld, false);
        }

        cache.putType(wid, key, TYPE_WATER);
        cache.putLevel(wid, key, (byte) level);
        queue.enqueue(world, x, y, z);

        if (waterlogEnabled) {
            // Update waterlogged state of solid neighbours
            for (int i = 0; i < 6; i++) {
                int nx = x + NX6[i]; int ny = y + NY6[i]; int nz = z + NZ6[i];
                if (ny >= minY(world) && ny <= maxY(world)) {
                    updateWaterlog(world, nx, ny, nz, level);
                }
            }
        }
    }

    private void setAir(World world, int x, int y, int z) {
        long key = BlockKey.of(x, y, z);
        UUID wid = world.getUID();

        world.getBlockAt(x, y, z).setType(Material.AIR, false);
        cache.putType(wid, key, TYPE_AIR);
        cache.putLevel(wid, key, (byte) 0);

        // Water above must fall into this newly emptied space — critical for ocean drain cascade
        int upY = y + 1;
        if (upY <= maxY(world) && cache.getType(world, x, upY, z) == TYPE_WATER) {
            queue.enqueue(world, x, upY, z);
        }

        // Adjacent water at same level should re-evaluate (may now flow sideways/down)
        for (int i = 0; i < 4; i++) {
            int nx = x + DX[i];
            int nz = z + DZ[i];
            if (cache.getType(world, nx, y, nz) == TYPE_WATER) {
                queue.enqueue(world, nx, y, nz);
            }
        }

        if (waterlogEnabled) {
            forceUnwaterlogNeighbors(world, x, y, z);
        }
    }

    private void applyLevel(World world, int x, int y, int z, int level) {
        if (level >= 8) { setAir(world, x, y, z); return; }
        if (y < minY(world) || y > maxY(world)) return;

        long key = BlockKey.of(x, y, z);
        UUID wid = world.getUID();

        byte oldLevel = cache.getLevel(world, x, y, z);
        if (oldLevel == (byte) level) return; // Level unchanged — do not re-queue

        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == Material.WATER && block.getBlockData() instanceof Levelled ld) {
            ld.setLevel(level);
            block.setBlockData(ld, false);
        } else {
            setWater(world, x, y, z, level);
            return;
        }

        cache.putType(wid, key, TYPE_WATER);
        cache.putLevel(wid, key, (byte) level);
        queue.enqueue(world, x, y, z);

        if (waterlogEnabled) {
            for (int i = 0; i < 6; i++) {
                int nx = x + NX6[i]; int ny = y + NY6[i]; int nz = z + NZ6[i];
                if (ny >= minY(world) && ny <= maxY(world)) {
                    updateWaterlog(world, nx, ny, nz, level);
                }
            }
        }
    }

    private void setSolid(World world, int x, int y, int z, Material material) {
        long key = BlockKey.of(x, y, z);
        UUID wid = world.getUID();
        world.getBlockAt(x, y, z).setType(material, false);
        cache.putType(wid, key, TYPE_OTHER);
        cache.putLevel(wid, key, (byte) 0);
    }

    // =========================================================================
    //  Sound
    // =========================================================================

    private void tryPlaySound(World world, int x, int y, int z) {
        if (!soundsEnabled) return;
        long ck = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        int last = lastSoundTick.getOrDefault(ck, -soundRateLimitTicks - 1);
        if (soundTick - last < soundRateLimitTicks) return;
        lastSoundTick.put(ck, soundTick);
        float pitch = soundPitch - 0.2f + (float) (Math.random() * 0.4);
        world.playSound(
                new Location(world, x + 0.5, y + 0.5, z + 0.5),
                Sound.BLOCK_WATER_AMBIENT,
                SoundCategory.BLOCKS,
                soundVolume,
                pitch);
    }

    // =========================================================================
    //  Accessors + world bounds cache
    // =========================================================================

    private byte getType(World world, int x, int y, int z) {
        int[] b = bounds(world);
        if (y < b[0] || y > b[1]) return TYPE_OTHER;
        return cache.getType(world, x, y, z);
    }

    private int getLevel(World world, int x, int y, int z) {
        return cache.getLevel(world, x, y, z) & 0xFF;
    }

    private int minY(World world) { return bounds(world)[0]; }
    private int maxY(World world) { return bounds(world)[1]; }

    private int[] bounds(World world) {
        return worldBoundsCache.computeIfAbsent(world.getUID(),
                k -> new int[]{world.getMinHeight(), world.getMaxHeight() - 1});
    }
}
