package ru.deelter.waterphysics.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import ru.deelter.waterphysics.WaterPhysics;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.config.PluginConfig;
import ru.deelter.waterphysics.engine.WaterQueue;

/**
 * When a player places a water bucket, re-evaluate all water blocks
 * within scan-radius so the body can spread or overflow if it has an opening.
 *
 * The scan also includes y+1 above each water block — water adjacent to air
 * at a higher Y can act as an overflow point.
 *
 * Deferred 1 tick so the placed water block is in the world before scanning.
 */
@RequiredArgsConstructor
public final class BucketListener implements Listener {

    private final PluginConfig    config;
    private final BlockStateCache cache;
    private final WaterQueue      queue;
    private final WaterPhysics    plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!config.isBucketPhysicsEnabled()) return;
        if (event.getBucket() != Material.WATER_BUCKET) return;

        Block target = event.getBlock();
        World world  = target.getWorld();
        if (!config.isWorldEnabled(world.getName())) return;

        int bx     = target.getX();
        int by     = target.getY();
        int bz     = target.getZ();
        int radius = config.getBucketScanRadius();

        // Defer 1 tick: the placed water block isn't in the world yet at event time
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Queue the placed block itself
            cache.preload(target);
            queue.enqueue(world, bx, by, bz);

            // Re-evaluate all water blocks in radius at y-1..y+1
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = bx + dx;
                        int ny = by + dy;
                        int nz = bz + dz;
                        if (cache.getType(world, nx, ny, nz) == BlockStateCache.TYPE_WATER) {
                            queue.enqueue(world, nx, ny, nz);
                        }
                    }
                }
            }
        });
    }
}
