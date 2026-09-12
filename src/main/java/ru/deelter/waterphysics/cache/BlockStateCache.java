package ru.deelter.waterphysics.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import ru.deelter.waterphysics.config.PluginConfig;
import ru.deelter.waterphysics.util.BlockKey;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.concurrent.TimeUnit;

/**
 * Two Caffeine caches per world: block type (byte) and water level (byte),
 * keyed by encoded {@code long} block coordinates.
 * <p>
 * JVM interns Byte for -128..127 — all type constants (0-5) and levels (0-7)
 * reuse cached objects → zero allocation on every cache hit.
 */
@RequiredArgsConstructor
public final class BlockStateCache {

	public static final byte TYPE_WATER = 0;
	public static final byte TYPE_AIR = 1;
	public static final byte TYPE_LAVA = 2;
	public static final byte TYPE_PLANT = 3;
	public static final byte TYPE_WATERLOGGED = 4;
	public static final byte TYPE_OTHER = 5;

	/** Underwater vegetation water may wash away (and must never be allowed to grow). */
	public static final Set<Material> SEA_PLANTS = EnumSet.of(
			Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT,
			Material.BUBBLE_COLUMN);

	/**
	 * Invisible/admin blocks physics must never destroy or waterlog. In Paper 26.x some of these
	 * report isSolid()=false and implement Waterlogged, so they need an explicit exclusion.
	 */
	public static final Set<Material> PROTECTED_BLOCKS = EnumSet.of(
			Material.BARRIER, Material.STRUCTURE_VOID, Material.STRUCTURE_BLOCK,
			Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
			Material.JIGSAW);

	private final PluginConfig config;

	private final Map<UUID, Cache<Long, Byte>> typeCaches = new HashMap<>();
	private final Map<UUID, Cache<Long, Byte>> levelCaches = new HashMap<>();

	// ---- Public API -------------------------------------------------------

	public byte getType(World world, int x, int y, int z) {
		long key = BlockKey.of(x, y, z);
		UUID wid = world.getUID();
		Byte hit = typeCache(wid).getIfPresent(key);
		return hit != null ? hit : loadType(world, wid, key, x, y, z);
	}

	public byte getLevel(World world, int x, int y, int z) {
		long key = BlockKey.of(x, y, z);
		UUID wid = world.getUID();
		Byte hit = levelCache(wid).getIfPresent(key);
		return hit != null ? hit : loadLevel(world, wid, key, x, y, z);
	}

	public void putType(UUID worldId, long key, byte type) {
		typeCache(worldId).put(key, type);
	}

	public void putLevel(UUID worldId, long key, byte level) {
		levelCache(worldId).put(key, level);
	}

	public void invalidate(UUID worldId, long key) {
		typeCache(worldId).invalidate(key);
		levelCache(worldId).invalidate(key);
	}

	public void preload(Block block) {
		long key = BlockKey.of(block.getX(), block.getY(), block.getZ());
		UUID wid = block.getWorld().getUID();
		byte type = computeType(block);
		typeCache(wid).put(key, type);
		if (type == TYPE_WATER && block.getBlockData() instanceof Levelled ld) {
			levelCache(wid).put(key, (byte) ld.getLevel());
		}
	}

	/** Visit every cached cell of the given type across all worlds (world id, encoded block key). */
	public void forEachCached(byte type, BiConsumer<UUID, Long> action) {
		typeCaches.forEach((wid, c) -> c.asMap().forEach((key, t) -> {
			if (t == type) action.accept(wid, key);
		}));
	}

	public void clearAll() {
		typeCaches.values().forEach(Cache::invalidateAll);
		levelCaches.values().forEach(Cache::invalidateAll);
	}

	/**
	 * Drop every cached type/level entry inside the given block-coordinate box for one world.
	 * See {@link ru.deelter.waterphysics.engine.WaterQueue#purgeRegion} — call both together after a
	 * silent bulk block rewrite (arena snapshot restore, WorldEdit, etc.) so this engine re-reads the
	 * real world state instead of handing out pre-rewrite cached values for those cells.
	 */
	public void purgeRegion(UUID worldId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		purgeRegion(typeCaches.get(worldId), minX, minY, minZ, maxX, maxY, maxZ);
		purgeRegion(levelCaches.get(worldId), minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static void purgeRegion(Cache<Long, Byte> cache, int minX, int minY, int minZ,
	                                int maxX, int maxY, int maxZ) {
		if (cache == null) return;
		cache.asMap().keySet().removeIf(key -> {
			int x = BlockKey.x(key), y = BlockKey.y(key), z = BlockKey.z(key);
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		});
	}

	// ---- Type helpers -----------------------------------------------------

	public static byte computeType(Block block) {
		Material mat = block.getType();
		byte basic = materialToType(mat);
		if (basic != TYPE_OTHER || PROTECTED_BLOCKS.contains(mat)) return basic;
		if (block.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()) return TYPE_WATERLOGGED;
		// Passable blocks (grass, flowers, ferns, rails…) and non-solid thin blocks
		// (carpets, moss carpet, snow layers, lily pads…) are treated like plants —
		// water flows through and removes them. Matches vanilla water behaviour.
		if (block.isPassable() || !mat.isSolid()) return TYPE_PLANT;
		return TYPE_OTHER;
	}

	public static byte materialToType(Material mat) {
		if (SEA_PLANTS.contains(mat)) return TYPE_PLANT;
		return switch (mat) {
			case WATER -> TYPE_WATER;
			case AIR, CAVE_AIR, VOID_AIR -> TYPE_AIR;
			case LAVA -> TYPE_LAVA;
			default -> TYPE_OTHER;
		};
	}

	public static boolean isPassable(byte type) {
		return type == TYPE_AIR || type == TYPE_PLANT || type == TYPE_LAVA;
	}

	// ---- Cache creation ---------------------------------------------------

	private Cache<Long, Byte> typeCache(UUID worldId) {
		return typeCaches.computeIfAbsent(worldId, k -> buildCache());
	}

	private Cache<Long, Byte> levelCache(UUID worldId) {
		return levelCaches.computeIfAbsent(worldId, k -> buildCache());
	}

	private Cache<Long, Byte> buildCache() {
		return Caffeine.newBuilder()
				.executor(Runnable::run)
				.maximumSize(config.getCacheMaxSize())
				.expireAfterAccess(config.getCacheTtlSeconds(), TimeUnit.SECONDS)
				.build();
	}

	// ---- World reads (cache miss) -----------------------------------------

	private byte loadType(World world, UUID wid, long key, int x, int y, int z) {
		byte type = computeType(world.getBlockAt(x, y, z));
		typeCache(wid).put(key, type);
		return type;
	}

	private byte loadLevel(World world, UUID wid, long key, int x, int y, int z) {
		byte level = 0;
		Block block = world.getBlockAt(x, y, z);
		if (block.getType() == Material.WATER && block.getBlockData() instanceof Levelled ld) {
			level = (byte) ld.getLevel();
		}
		levelCache(wid).put(key, level);
		return level;
	}
}
