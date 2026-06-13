package ru.deelter.waterphysics.engine;

import org.bukkit.World;
import ru.deelter.waterphysics.util.BlockKey;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deduplicating block queue for water flow processing.
 *
 * Uses {@code ArrayDeque} (faster than LinkedList/ConcurrentLinkedQueue for
 * single-threaded poll/offer) + per-world {@code HashSet<Long>} as a dedup
 * guard.  The same block can never appear in the queue more than once —
 * this was a major source of wasted CPU in the original plugin.
 *
 * All methods must be called from the main thread.
 */
public final class WaterQueue {

    public record Entry(World world, int x, int y, int z) {
        public long key() { return BlockKey.of(x, y, z); }
    }

    private final ArrayDeque<Entry>         deque     = new ArrayDeque<>(1024);
    private final Map<UUID, Set<Long>>      dedupSets = new HashMap<>();

    /**
     * Enqueue a block for flow processing.
     * @return {@code true} if the block was added, {@code false} if already queued.
     */
    public boolean enqueue(World world, int x, int y, int z) {
        long key = BlockKey.of(x, y, z);
        UUID wid = world.getUID();
        Set<Long> dedup = dedupSets.computeIfAbsent(wid, k -> new HashSet<>());
        if (dedup.add(key)) {
            deque.addLast(new Entry(world, x, y, z));
            return true;
        }
        return false;
    }

    public Entry poll() {
        Entry entry = deque.pollFirst();
        if (entry != null) {
            Set<Long> dedup = dedupSets.get(entry.world().getUID());
            if (dedup != null) dedup.remove(entry.key());
        }
        return entry;
    }

    public boolean isEmpty()  { return deque.isEmpty(); }
    public int     size()     { return deque.size(); }

    public void clear() {
        deque.clear();
        dedupSets.values().forEach(Set::clear);
    }
}
