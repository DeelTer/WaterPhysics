package ru.deelter.waterphysics.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.config.PluginConfig;
import ru.deelter.waterphysics.engine.WaterQueue;

@RequiredArgsConstructor
public final class WaterEventListener implements Listener {

	private final PluginConfig config;
	private final BlockStateCache cache;
	private final WaterQueue queue;

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockFromTo(BlockFromToEvent event) {
		Block from = event.getBlock();
		Material type = from.getType();

		if (type != Material.WATER && type != Material.LAVA) return;
		if (!config.isWorldEnabled(from.getWorld().getName())) return;

		event.setCancelled(true);

		// In interaction-only mode water flows only when disturbed by a player action.
		// We still cancel to prevent vanilla physics, but don't queue.
		if (type == Material.WATER && !config.isInteractionOnly()) {
			cache.preload(from);
			cache.preload(event.getToBlock());
			queue.enqueue(from.getWorld(), from.getX(), from.getY(), from.getZ());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		Material mat = event.getBlock().getType();
		if (mat == Material.WATER || isSeaPlant(mat)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockGrow(BlockGrowEvent event) {
		if (!config.isPreventSeaPlantGrowth()) return;
		if (isSeaPlant(event.getNewState().getType())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockSpread(BlockSpreadEvent event) {
		if (!config.isPreventSeaPlantGrowth()) return;
		if (isSeaPlant(event.getNewState().getType())) {
			event.setCancelled(true);
		}
	}

	private static boolean isSeaPlant(Material mat) {
		return mat == Material.SEAGRASS
				|| mat == Material.TALL_SEAGRASS
				|| mat == Material.KELP
				|| mat == Material.KELP_PLANT
				|| mat == Material.BUBBLE_COLUMN;
	}
}
