package ru.deelter.waterphysics.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import ru.deelter.waterphysics.WaterPhysics;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.config.PluginConfig;
import ru.deelter.waterphysics.engine.WaterQueue;

/**
 * Re-queues water blocks in a chunk when it loads.
 *
 * Without this: chunk unloads mid-drain → queued blocks dropped → chunk reloads
 * → water frozen forever at wrong levels.
 *
 * Uses ChunkSnapshot for fast array-based type scanning (no Block allocations
 * per block), then preloads only the water blocks found.
 * Deferred 1 tick so the chunk finishes loading before we access it.
 */
@RequiredArgsConstructor
public final class ChunkListener implements Listener {

    private final PluginConfig    config;
    private final BlockStateCache cache;
    private final WaterQueue      queue;
    private final WaterPhysics    plugin;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!config.isChunkRescanOnLoad() || config.isInteractionOnly()) return;

        World world = event.getWorld();
        if (!config.isWorldEnabled(world.getName())) return;
        if (event.isNewChunk()) return;

        Chunk chunk = event.getChunk();

        // Defer 1 tick — chunk must finish loading before block access
        plugin.getServer().getScheduler().runTask(plugin, () -> scanChunk(world, chunk));
    }

    private void scanChunk(World world, Chunk chunk) {
        if (!chunk.isLoaded()) return;

        int baseX  = chunk.getX() << 4;
        int baseZ  = chunk.getZ() << 4;
        int minY   = world.getMinHeight();
        int maxY   = world.getMaxHeight();
        int limit  = config.getChunkScanMaxBlocks();
        int queued = 0;

        // ChunkSnapshot: fast array reads, no Block allocations per iteration.
        // Paper accepts world Y coordinates (not chunk-relative) for getBlockType().
        ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);

        outer:
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = minY; y < maxY; y++) {
                    if (snap.getBlockType(lx, y, lz) != Material.WATER) continue;

                    // Preload block into cache to avoid cold misses in engine
                    Block block = chunk.getBlock(lx, y, lz);
                    cache.preload(block);
                    queue.enqueue(world, baseX + lx, y, baseZ + lz);

                    if (++queued >= limit) break outer;
                }
            }
        }
    }
}
