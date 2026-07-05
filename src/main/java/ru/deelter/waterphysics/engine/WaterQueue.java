package ru.deelter.waterphysics.engine;

import org.bukkit.World;
import ru.deelter.waterphysics.cache.PlayerChunkCache;
import ru.deelter.waterphysics.util.BlockKey;

import java.util.*;

/**
 * Deduplicating, two-tier block queue for water flow processing.
 * <p>
 * Blocks in chunks near a player ("near" tier) are processed before distant
 * ones ("far" tier), so the action visible to the player resolves first while
 * off-screen flow catches up with leftover budget.  Classification is O(1) via
 * {@link PlayerChunkCache#isActive} at enqueue time.
 * <p>
 * Uses {@code ArrayDeque} (faster than LinkedList/ConcurrentLinkedQueue for
 * single-threaded poll/offer) + a per-world {@code HashSet<Long>} dedup guard
 * shared across both tiers — the same block can never appear more than once.
 * <p>
 * All methods must be called from the main thread.
 */
public final class WaterQueue {

	public record Entry(World world, int x, int y, int z) {
		public long key() {
			return BlockKey.of(x, y, z);
		}
	}

	private final PlayerChunkCache proximity;

	private final ArrayDeque<Entry> near = new ArrayDeque<>(1024);
	private final ArrayDeque<Entry> far = new ArrayDeque<>(1024);
	private final Map<UUID, Set<Long>> dedupSets = new HashMap<>();

	public WaterQueue(PlayerChunkCache proximity) {
		this.proximity = proximity;
	}

	/**
	 * Enqueue a block for flow processing.
	 *
	 * @return {@code true} if the block was added, {@code false} if already queued.
	 */
	public boolean enqueue(World world, int x, int y, int z) {
		long key = BlockKey.of(x, y, z);
		UUID wid = world.getUID();
		Set<Long> dedup = dedupSets.computeIfAbsent(wid, k -> new HashSet<>());
		if (dedup.add(key)) {
			Entry entry = new Entry(world, x, y, z);
			if (proximity.isActive(wid, x, z)) {
				near.addLast(entry);
			} else {
				far.addLast(entry);
			}
			return true;
		}
		return false;
	}

	/**
	 * Poll the next block — near-player blocks first, then distant ones.
	 */
	public Entry poll() {
		Entry entry = near.pollFirst();
		if (entry == null) entry = far.pollFirst();
		if (entry != null) {
			Set<Long> dedup = dedupSets.get(entry.world().getUID());
			if (dedup != null) dedup.remove(entry.key());
		}
		return entry;
	}

	public boolean isEmpty() {
		return near.isEmpty() && far.isEmpty();
	}

	public int size() {
		return near.size() + far.size();
	}

	public void clear() {
		near.clear();
		far.clear();
		dedupSets.values().forEach(Set::clear);
	}

	/**
	 * Drop every queued entry inside the given block-coordinate box for one world. For use by other
	 * plugins that silently bulk-rewrite a region (arena snapshot restores, WorldEdit, etc.) without
	 * firing Bukkit block events — otherwise this engine keeps processing stale pending flow entries
	 * against the region after it's been overwritten, writing water onto whatever now occupies those
	 * coordinates. Call {@link ru.deelter.waterphysics.cache.BlockStateCache#purgeRegion} alongside
	 * this so the cache doesn't hand out stale state for the same cells afterward.
	 */
	public void purgeRegion(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		UUID wid = world.getUID();
		Set<Long> dedup = dedupSets.get(wid);
		java.util.function.Predicate<Entry> inRegion = e ->
				e.world().getUID().equals(wid)
						&& e.x() >= minX && e.x() <= maxX
						&& e.y() >= minY && e.y() <= maxY
						&& e.z() >= minZ && e.z() <= maxZ;
		near.removeIf(e -> {
			boolean hit = inRegion.test(e);
			if (hit && dedup != null) dedup.remove(e.key());
			return hit;
		});
		far.removeIf(e -> {
			boolean hit = inRegion.test(e);
			if (hit && dedup != null) dedup.remove(e.key());
			return hit;
		});
	}
}
