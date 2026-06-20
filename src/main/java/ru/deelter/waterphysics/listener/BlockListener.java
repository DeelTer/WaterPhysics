package ru.deelter.waterphysics.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    /**
     * Explosions destroy many blocks at once (TNT, creeper, bed/anchor) via
     * the explode events — not BlockBreakEvent. Each removed block opens a hole
     * water must flow into, so invalidate + wake neighbours for every one.
     * The blocks are still present at MONITOR time; the engine re-reads them
     * next tick once vanilla has cleared them.
     */
    private void handleExplosion(List<Block> blocks) {
        for (Block block : blocks) {
            invalidateAndQueueNeighbours(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePiston(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePiston(event.getBlocks(), event.getDirection());
    }

    /**
     * A piston shifts blocks one step along {@code dir}, freeing their old
     * positions. Invalidate + wake fluid neighbours at both the old cell and the
     * cell each block moves into, so water flows into the gap. Blocks are still
     * at their pre-move positions at MONITOR time; the engine re-reads them next
     * tick once the move has completed.
     */
    private void handlePiston(List<Block> blocks, BlockFace dir) {
        for (Block block : blocks) {
            invalidateAndQueueNeighbours(block);
            invalidateAndQueueNeighbours(block.getRelative(dir));
        }
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
            if (ntype == BlockStateCache.TYPE_WATER || ntype == BlockStateCache.TYPE_LAVA) {
                queue.enqueue(changed.getWorld(), nx, ny, nz);
            }
        }
    }
}
