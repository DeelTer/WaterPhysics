package ru.deelter.waterphysics.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.engine.WaterQueue;
import ru.deelter.waterphysics.util.BlockKey;

@RequiredArgsConstructor
public final class BlockListener implements Listener {

    private static final int[] DX = { 0,  1,  0, -1,  0,  0};
    private static final int[] DY = { 0,  0,  0,  0,  1, -1};
    private static final int[] DZ = {-1,  0,  1,  0,  0,  0};

    private final BlockStateCache cache;
    private final WaterQueue      queue;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        invalidateAndQueueNeighbours(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        cache.preload(block);
        if (block.getType() == Material.WATER) {
            queue.enqueue(block.getWorld(), block.getX(), block.getY(), block.getZ());
        }
        invalidateAndQueueNeighbours(block);
    }

    private void invalidateAndQueueNeighbours(Block changed) {
        long key = BlockKey.of(changed.getX(), changed.getY(), changed.getZ());
        cache.invalidate(changed.getWorld().getUID(), key);

        int x = changed.getX();
        int y = changed.getY();
        int z = changed.getZ();

        for (int i = 0; i < 6; i++) {
            int nx = x + DX[i];
            int ny = y + DY[i];
            int nz = z + DZ[i];
            byte ntype = cache.getType(changed.getWorld(), nx, ny, nz);
            if (ntype == BlockStateCache.TYPE_WATER) {
                queue.enqueue(changed.getWorld(), nx, ny, nz);
            }
        }
    }
}
