package ru.deelter.waterphysics.engine;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.scheduler.BukkitRunnable;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.cache.PlayerChunkCache;
import ru.deelter.waterphysics.config.PluginConfig;
import ru.deelter.waterphysics.util.BlockKey;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static ru.deelter.waterphysics.cache.BlockStateCache.*;

/**
 * Core water flow engine.  Runs on the MAIN thread every N ticks.
 * <p>
 * Mass-conserving model.  Each water cell holds an integer number of
 * <b>units</b> in the range 1..8:
 * <ul>
 *   <li>8 units  = source block        (Minecraft level 0)</li>
 *   <li>1..7     = flowing block        (Minecraft level 8 - units)</li>
 *   <li>0        = air / empty</li>
 * </ul>
 * Every operation only ever <i>moves</i> units between cells — it never
 * creates or destroys them — so a poured volume always equals the drained
 * volume (no duplication, no leftover source).
 * <p>
 * Flow priority per tick:
 * <ol>
 *   <li>Gravity   — push as many units as fit straight down.</li>
 *   <li>Sideways  — donate one unit to each lower horizontal neighbour while
 *       the gradient is &gt;= 2 (flattens connected bodies; the {@code >= 2}
 *       guard prevents 1-unit oscillation and leaves single-layer puddles
 *       stable).</li>
 * </ol>
 * Re-queueing of touched cells lets the body converge over successive ticks.
 */
public final class FlowEngine extends BukkitRunnable {

	// Horizontal neighbour offsets: N, E, S, W
	private static final int[] DX = {0, 1, 0, -1};
	private static final int[] DZ = {-1, 0, 1, 0};

	// All 6 face offsets (for waterlogging side-effect check)
	private static final int[] NX6 = {0, 1, 0, -1, 0, 0};
	private static final int[] NY6 = {0, 0, 0, 0, 1, -1};
	private static final int[] NZ6 = {-1, 0, 1, 0, 0, 0};

	// Max cells visited when hunting for a downhill drain across a flat plane.
	// Bounds cost regardless of body shape; large enough to reach a distant exit.
	private static final int MAX_FLOW_NODES = 1024;

	private final PluginConfig config;
	private final BlockStateCache cache;
	private final PlayerChunkCache proximity;
	private final WaterQueue queue;

	// Proximity update counter
	private int tickCount;

	// ---- Cached config primitives (read once, avoid getter call overhead) --
	private final int batchSize;
	private final boolean playerProximityCheck;
	private final boolean convertLava;
	private final boolean convertLavaSource;
	private final boolean lavaPhysics;
	private final boolean waterlogEnabled;
	private final int waterlogMaxLevel;
	private final boolean biomeExclusionEnabled;
	private final boolean removePuddles;
	private final int removePuddleMaxUnits;
	private final boolean soundsEnabled;
	private final int soundRateLimitTicks;
	private final float soundVolume;
	private final float soundPitch;
	private final boolean effectsEnabled;
	private final int effectsRateLimitTicks;
	private final int effectsCount;

	// Biome exclusion cache: keyed by 4x4x4 section position, value = is excluded.
	// Biomes never change at runtime → safe to cache forever.
	private final Map<Long, Boolean> biomeExcludedCache = new HashMap<>();

	// Sound rate-limit: chunk key → last soundTick when sound played.
	private final Map<Long, Integer> lastSoundTick = new HashMap<>();
	// Particle rate-limit: chunk key → last soundTick when effect played.
	private final Map<Long, Integer> lastEffectTick = new HashMap<>();
	private int soundTick;

	// Reused BFS scratch for findDrainDir — avoids per-call allocation.
	private final ArrayDeque<int[]> bfsQueue = new ArrayDeque<>();
	private final HashSet<Long> bfsSeen = new HashSet<>(64);

	// ---- Per-world height bounds cache -------------------------------------
	private final Map<UUID, int[]> worldBoundsCache = new HashMap<>(); // [0]=min, [1]=max

	// ---- Active fluid context for the block currently being processed ------
	// Set at the top of processBlock; every flow/mutation helper reads these so
	// the same finite model drives water OR lava without duplicated code.
	// Safe as instance fields: processing is single-threaded and synchronous.
	private byte curType = TYPE_WATER;
	private Material curMat = Material.WATER;
	private boolean curIsWater = true;

	public FlowEngine(PluginConfig config, BlockStateCache cache,
	                  PlayerChunkCache proximity, WaterQueue queue) {
		this.config = config;
		this.cache = cache;
		this.proximity = proximity;
		this.queue = queue;
		this.batchSize = config.getBatchSize();
		this.playerProximityCheck = config.isPlayerProximityCheck();
		this.convertLava = config.isConvertLava();
		this.convertLavaSource = config.isConvertLavaSource();
		this.lavaPhysics = config.isLavaPhysics();
		this.waterlogEnabled = config.isWaterlogEnabled();
		this.waterlogMaxLevel = config.getWaterlogMaxLevel();
		this.biomeExclusionEnabled = !config.getExcludedBiomes().isEmpty();
		this.removePuddles = config.isRemovePuddles();
		this.removePuddleMaxUnits = config.getRemovePuddleMaxUnits();
		this.soundsEnabled = config.isSoundsEnabled();
		this.soundRateLimitTicks = config.getSoundRateLimitTicks();
		this.soundVolume = config.getSoundVolume();
		this.soundPitch = config.getSoundPitch();
		this.effectsEnabled = config.isEffectsEnabled();
		this.effectsRateLimitTicks = config.getEffectsRateLimitTicks();
		this.effectsCount = config.getEffectsCount();
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
		if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
		if (!config.isWorldEnabled(world.getName())) return;
		if (playerProximityCheck
				&& !proximity.isActive(world.getUID(), x, z)) return;
		if (biomeExclusionEnabled) {
			long bk = BlockKey.of(x >> 2, y >> 2, z >> 2);
			if (biomeExcludedCache.computeIfAbsent(bk, k -> config.isBiomeExcluded(world.getBiome(x, y, z)))) return;
		}
		// Pick the fluid for this block: water always, lava only if enabled.
		byte selfType = getType(world, x, y, z);
		if (selfType == TYPE_WATER) {
			curType = TYPE_WATER;
			curMat = Material.WATER;
			curIsWater = true;
		} else if (selfType == TYPE_LAVA && lavaPhysics) {
			curType = TYPE_LAVA;
			curMat = Material.LAVA;
			curIsWater = false;
		} else {
			return;
		}

		int u = unitsAt(world, x, y, z);
		if (u <= 0) return;
		final int u0 = u;

		// ----- 1. Gravity: fall to the bottom in one tick -----------------
		int minY = minY(world);
		int downY = y - 1;
		if (downY >= minY) {
			byte dt = getType(world, x, downY, z);
			boolean canFall = dt == TYPE_AIR || dt == TYPE_PLANT
					|| (dt == curType && unitsAt(world, x, downY, z) < 8);
			if (canFall) {
				setAir(world, x, y, z);             // empty origin
				addWaterFalling(world, x, y, z, u); // pour the whole column to the floor
				return;
			}
		}

		// ----- 2. Sideways -------------------------------------------------
		// (a) Drain straight into any directly downhill neighbour; handle lava
		//     and waterlogging along the way.
		for (int i = 0; i < 4 && u > 0; i++) {
			int nx = x + DX[i];
			int nz = z + DZ[i];
			byte nt = getType(world, nx, y, nz);

			// Water touching lava solidifies it (cobble/obsidian). Only when the
			// active fluid is water — lava must not solidify lava neighbours.
			if (curIsWater && nt == TYPE_LAVA && convertLava) {
				boolean isSource = getLevel(world, nx, y, nz) == 0;
				Material result = (isSource && convertLavaSource) ? Material.OBSIDIAN : Material.COBBLESTONE;
				setSolid(world, nx, y, nz, result);
				continue;
			}

			boolean passable = (nt == TYPE_AIR || nt == TYPE_PLANT || nt == curType);
			if (!passable) {
				if (waterlogEnabled && curIsWater && (nt == TYPE_OTHER || nt == TYPE_WATERLOGGED)) {
					updateWaterlog(world, nx, y, nz, 8 - u);
				}
				continue;
			}

			int nu = (nt == curType) ? unitsAt(world, nx, y, nz) : 0;
			if (nu < 8 && canDescend(world, nx, y, nz)) {
				int move = Math.min(u, 8 - nu);
				addWaterFalling(world, nx, y, nz, move);
				u -= move;
			}
		}

		// (b) Still holding water and no adjacent drop: scan the flat plane for
		//     the nearest cell water could fall from and crawl one unit toward
		//     it.  Guarantees a body fully empties whenever ANY reachable lower
		//     spot exists, instead of stranding films on a plateau.
		if (u > 1) {
			int dir = findDrainDir(world, x, y, z);
			if (dir >= 0) {
				int nx = x + DX[dir];
				int nz = z + DZ[dir];
				int nu = unitsAt(world, nx, y, nz);
				if (nu < u) {
					addWaterFalling(world, nx, y, nz, 1);
					u--;
				}
			} else {
				// Truly enclosed basin → equalise to a flat puddle (stable at diff < 2).
				for (int i = 0; i < 4 && u > 1; i++) {
					int nx = x + DX[i];
					int nz = z + DZ[i];
					byte nt = getType(world, nx, y, nz);
					if (nt != TYPE_AIR && nt != TYPE_PLANT && nt != curType) continue;
					int nu = (nt == curType) ? unitsAt(world, nx, y, nz) : 0;
					if (u - nu >= 2) {
						addWaterFalling(world, nx, y, nz, 1);
						u--;
					}
				}
			}
		}

		// Evaporate a stranded thin film: a small flowing block that cannot
		// move anywhere (no fuller neighbour to merge from, no adjacent drop,
		// no reachable drain) would otherwise hang forever as a puddle.
		if (removePuddles && u > 0 && u <= removePuddleMaxUnits && isStrandedPuddle(world, x, y, z, u)) {
			u = 0;
		}

		// Write back our remaining units.
		place(world, x, y, z, u);

		// Wake the block above so it can fall into any space we just freed.
		if (u != u0) {
			int upY = y + 1;
			if (upY <= maxY(world) && getType(world, x, upY, z) == curType) {
				queue.enqueue(world, x, upY, z);
			}
		}
	}

	// =========================================================================
	//  Unit <-> level conversion + conserving placement
	// =========================================================================

	/** Fluid "units": 8 = source (full), 1..7 = flowing, 0 = empty/air. Uses the active fluid. */
	private int unitsAt(World world, int x, int y, int z) {
		if (getType(world, x, y, z) != curType) return 0;
		int lvl = getLevel(world, x, y, z);
		if (lvl == 0) return 8;   // source
		if (lvl >= 8) return 0;   // falling / invalid → treat as empty
		return 8 - lvl;
	}

	/**
	 * True if water arriving at (x,y,z) could fall further — i.e. the cell
	 * directly below has room (air/plant, or non-full water). Marks a cell
	 * whose floor sits lower than the donor's, so we drain into it fully
	 * instead of leaving a 1-unit film perched on a ledge.
	 */
	private boolean canDescend(World world, int x, int y, int z) {
		int by = y - 1;
		if (by < minY(world)) return false;
		byte bt = getType(world, x, by, z);
		if (bt == TYPE_AIR || bt == TYPE_PLANT) return true;
		return bt == curType && unitsAt(world, x, by, z) < 8;
	}

	/**
	 * Breadth-first search across the flat plane at height {@code y} for the
	 * nearest cell from which water could fall ({@link #canDescend}). Returns
	 * the index (0-3 in DX/DZ) of the first step toward it, or -1 if no drop is
	 * reachable within {@link #MAX_FLOW_NODES} cells.
	 * <p>
	 * Traverses only passable, non-descending cells (water sitting on a solid
	 * floor, or air over a solid floor) — a descending cell is the goal, not a
	 * transit node. Diagonal-free 4-neighbour expansion, matching flow dirs.
	 */
	private int findDrainDir(World world, int x, int y, int z) {
		bfsQueue.clear();
		bfsSeen.clear();
		bfsSeen.add(BlockKey.of(x, 0, z));

		for (int i = 0; i < 4; i++) {
			int nx = x + DX[i];
			int nz = z + DZ[i];
			if (!bfsSeen.add(BlockKey.of(nx, 0, nz))) continue;
			if (!isFlowPassable(world, nx, y, nz)) continue;
			if (canDescend(world, nx, y, nz)) return i;
			bfsQueue.add(new int[]{nx, nz, i});
		}

		while (!bfsQueue.isEmpty() && bfsSeen.size() < MAX_FLOW_NODES) {
			int[] c = bfsQueue.poll();
			for (int i = 0; i < 4; i++) {
				int nx = c[0] + DX[i];
				int nz = c[1] + DZ[i];
				if (!bfsSeen.add(BlockKey.of(nx, 0, nz))) continue;
				if (!isFlowPassable(world, nx, y, nz)) continue;
				if (canDescend(world, nx, y, nz)) return c[2];
				bfsQueue.add(new int[]{nx, nz, c[2]});
			}
		}
		return -1;
	}

	/**
	 * True if (x,y,z) holds {@code u} units that have nowhere to go this tick:
	 * no horizontal neighbour fuller than us (nothing will feed/merge), no
	 * adjacent cell to descend into, and no reachable drain. Such a film is
	 * a dead-end puddle and gets evaporated.
	 */
	private boolean isStrandedPuddle(World world, int x, int y, int z, int u) {
		// Can it fall straight down?
		if (canDescend(world, x, y, z)) return false;
		for (int i = 0; i < 4; i++) {
			int nx = x + DX[i];
			int nz = z + DZ[i];
			byte nt = getType(world, nx, y, nz);
			if (nt == curType) {
				if (unitsAt(world, nx, y, nz) > u) return false; // a fuller neighbour
			} else if (nt == TYPE_AIR || nt == TYPE_PLANT) {
				if (canDescend(world, nx, y, nz)) return false;  // adjacent drop
			}
		}
		return findDrainDir(world, x, y, z) < 0; // no far drain either
	}

	/** A cell the active fluid can occupy/flow through: air, plant, or non-full same-fluid. */
	private boolean isFlowPassable(World world, int x, int y, int z) {
		byte t = getType(world, x, y, z);
		if (t == TYPE_AIR || t == TYPE_PLANT) return true;
		return t == curType && unitsAt(world, x, y, z) < 8;
	}

	/**
	 * Add {@code units} of water to the column at (x,y,z), letting it fall to the
	 * bottom in a single step. Descends through the contiguous passable column
	 * (air / plant / non-full water), then fills from the floor upward — so a
	 * deep drop reaches the cave floor this tick and stacks from the bottom,
	 * instead of trickling down one block per tick or hanging in mid-air.
	 */
	private void addWaterFalling(World world, int x, int y, int z, int units) {
		if (units <= 0) return;

		// Find the lowest cell water can occupy in this column.
		int floor = y;
		int p = y - 1;
		while (p >= minY(world)) {
			byte t = getType(world, x, p, z);
			if (t == TYPE_AIR || t == TYPE_PLANT) { floor = p; p--; continue; }
			if (t == curType && unitsAt(world, x, p, z) < 8) { floor = p; p--; continue; }
			break; // solid floor or full fluid — cannot sink past
		}

		// Fill from the bottom up; overflow backs up toward y.
		for (int cy = floor; units > 0 && cy <= y; cy++) {
			int existing = unitsAt(world, x, cy, z);
			int add = Math.min(units, 8 - existing);
			if (add > 0) {
				place(world, x, cy, z, existing + add);
				units -= add;
			}
		}
	}

	/**
	 * Place {@code units} of water at a cell.
	 * 0 → air, 8 → source (level 0), else flowing level (8 - units).
	 * Routes through setWater/applyLevel so cache, waterlog and re-queue
	 * side-effects stay consistent.
	 */
	private void place(World world, int x, int y, int z, int units) {
		if (units <= 0) {
			if (getType(world, x, y, z) == curType) setAir(world, x, y, z);
			return;
		}
		if (units > 8) units = 8;
		int level = (units >= 8) ? 0 : 8 - units;
		if (getType(world, x, y, z) == curType) {
			applyLevel(world, x, y, z, level);
		} else {
			setWater(world, x, y, z, level);
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
		byte ntype = cache.getType(world, nx, ny, nz);
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
		if (block.getType() != curMat) {
			// Drop items for replaceable blocks (carpets, moss, plants, etc.) before flooding
			if (!block.getType().isAir() && cache.getType(world, x, y, z) == TYPE_PLANT) {
				block.breakNaturally();
			}
			block.setType(curMat, false);
			if (curIsWater) {
				tryPlaySound(world, x, y, z);
				tryPlayEffect(world, x, y, z);
			}
		}
		if (block.getBlockData() instanceof Levelled ld) {
			ld.setLevel(level);
			block.setBlockData(ld, false);
		}

		cache.putType(wid, key, curType);
		cache.putLevel(wid, key, (byte) level);
		queue.enqueue(world, x, y, z);
		enqueueWaterNeighbors(world, x, y, z);

		if (waterlogEnabled && curIsWater) {
			// Update waterlogged state of solid neighbours
			for (int i = 0; i < 6; i++) {
				int nx = x + NX6[i];
				int ny = y + NY6[i];
				int nz = z + NZ6[i];
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

		// Wake all surrounding fluid (above falls in, sides flow in, below re-checks)
		enqueueWaterNeighbors(world, x, y, z);

		if (waterlogEnabled && curIsWater) {
			forceUnwaterlogNeighbors(world, x, y, z);
		}
	}

	private void applyLevel(World world, int x, int y, int z, int level) {
		if (level >= 8) {
			setAir(world, x, y, z);
			return;
		}
		if (y < minY(world) || y > maxY(world)) return;

		long key = BlockKey.of(x, y, z);
		UUID wid = world.getUID();

		byte oldLevel = cache.getLevel(world, x, y, z);
		if (cache.getType(world, x, y, z) == curType && oldLevel == (byte) level) {
			return; // Level unchanged — do not re-queue
		}

		Block block = world.getBlockAt(x, y, z);
		if (block.getType() == curMat && block.getBlockData() instanceof Levelled ld) {
			ld.setLevel(level);
			block.setBlockData(ld, false);
		} else {
			setWater(world, x, y, z, level);
			return;
		}

		cache.putType(wid, key, curType);
		cache.putLevel(wid, key, (byte) level);
		queue.enqueue(world, x, y, z);
		enqueueWaterNeighbors(world, x, y, z);

		if (waterlogEnabled && curIsWater) {
			for (int i = 0; i < 6; i++) {
				int nx = x + NX6[i];
				int ny = y + NY6[i];
				int nz = z + NZ6[i];
				if (ny >= minY(world) && ny <= maxY(world)) {
					updateWaterlog(world, nx, ny, nz, level);
				}
			}
		}
	}

	/**
	 * Re-queue every water block on the 6 faces of (x,y,z).  Called whenever a
	 * cell's water level changes so fuller neighbours re-evaluate and flow into
	 * the now-lower cell.  Without this, water past the first ring "hangs" —
	 * it is never re-processed and freezes mid-drain.
	 */
	private void enqueueWaterNeighbors(World world, int x, int y, int z) {
		for (int i = 0; i < 6; i++) {
			int nx = x + NX6[i];
			int ny = y + NY6[i];
			int nz = z + NZ6[i];
			if (ny < minY(world) || ny > maxY(world)) continue;
			if (cache.getType(world, nx, ny, nz) == curType) {
				queue.enqueue(world, nx, ny, nz);
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

	/**
	 * Spawn water particles when water reaches a new block — only near players
	 * (off-screen effects are wasted packets), rate-limited per chunk.
	 */
	private void tryPlayEffect(World world, int x, int y, int z) {
		if (!effectsEnabled) return;
		// "Near a player" = inside the proximity chunk set. When proximity is
		// off everything counts as near, so effects still play.
		if (playerProximityCheck && !proximity.isActive(world.getUID(), x, z)) return;

		long ck = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
		int last = lastEffectTick.getOrDefault(ck, -effectsRateLimitTicks - 1);
		if (soundTick - last < effectsRateLimitTicks) return;
		lastEffectTick.put(ck, soundTick);

		world.spawnParticle(
				Particle.FALLING_WATER,
				new Location(world, x + 0.5, y + 0.5, z + 0.5),
				effectsCount,
				0.3, 0.3, 0.3,
				0.0);
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

	private int minY(World world) {
		return bounds(world)[0];
	}

	private int maxY(World world) {
		return bounds(world)[1];
	}

	private int[] bounds(World world) {
		return worldBoundsCache.computeIfAbsent(world.getUID(),
				k -> new int[]{world.getMinHeight(), world.getMaxHeight() - 1});
	}
}
