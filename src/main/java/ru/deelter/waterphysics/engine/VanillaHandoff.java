package ru.deelter.waterphysics.engine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.util.BlockKey;

import java.util.ArrayDeque;
import java.util.UUID;

/**
 * Hands fluid the engine touched back to vanilla when physics is switched off.
 * <p>
 * Engine writes use {@code applyPhysics=false}, so the cells it left behind (flowing levels with no
 * feeding source, half-drained films) have no scheduled vanilla fluid tick and would stay frozen
 * forever. Every fluid cell still in the cache gets one {@link Block#fluidTick()}; from there vanilla
 * schedules its own follow-up ticks and spreads/drains exactly as without the plugin.
 * <p>
 * Spread over ticks ({@code batchSize} cells per tick) so a large cache does not stall the server.
 */
public final class VanillaHandoff extends BukkitRunnable {

	private record Cell(UUID world, long key) {
	}

	private final ArrayDeque<Cell> cells = new ArrayDeque<>();
	private final int batchSize;

	public VanillaHandoff(BlockStateCache cache, int batchSize) {
		this.batchSize = batchSize;
		cache.forEachCached(BlockStateCache.TYPE_WATER, (wid, key) -> cells.add(new Cell(wid, key)));
		cache.forEachCached(BlockStateCache.TYPE_LAVA, (wid, key) -> cells.add(new Cell(wid, key)));
	}

	public boolean isEmpty() {
		return cells.isEmpty();
	}

	@Override
	public void run() {
		for (int i = 0; i < batchSize; i++) {
			Cell cell = cells.poll();
			if (cell == null) {
				cancel();
				return;
			}
			World world = Bukkit.getWorld(cell.world());
			if (world == null) continue;
			int x = BlockKey.x(cell.key()), y = BlockKey.y(cell.key()), z = BlockKey.z(cell.key());
			if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
			Block block = world.getBlockAt(x, y, z);
			Material type = block.getType();
			if (type == Material.WATER || type == Material.LAVA) block.fluidTick();
		}
	}
}
