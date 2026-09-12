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
	/** While false (physics disabled) every enqueue is dropped so nothing piles up for a stopped engine. */
	private boolean accepting = true;
	/** Vetoes installed by other plugins — see {@link FlowGuard}. Usually empty. */
	private final List<FlowGuard> guards = new ArrayList<>(1);

	/** Install a veto. Cheap to have none: the common path is an {@code isEmpty()} check. */
	public void addGuard(FlowGuard guard) {
		if (guard != null && !guards.contains(guard)) guards.add(guard);
	}

	public void removeGuard(FlowGuard guard) {
		guards.remove(guard);
	}

	public void setAccepting(boolean accepting) {
		this.accepting = accepting;
		if (!accepting) clear();
	}

	private boolean suppressed(World world, int x, int y, int z) {
		if (guards.isEmpty()) return false;
		for (int i = 0; i < guards.size(); i++) {
			if (guards.get(i).isSuppressed(world, x, y, z)) return true;
		}
		return false;
	}

	public WaterQueue(PlayerChunkCache proximity) {
		this.proximity = proximity;
	}

	/**
	 * Enqueue a block for flow processing.
	 *
	 * @return {@code true} if the block was added, {@code false} if already queued.
	 */
	public boolean enqueue(World world, int x, int y, int z) {
		// Checked before the dedup set so a vetoed block leaves no trace to clean up later.
		if (!accepting || suppressed(world, x, y, z)) return false;
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
