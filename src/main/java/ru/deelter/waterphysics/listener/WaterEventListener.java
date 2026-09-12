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
import ru.deelter.waterphysics.WaterPhysics;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.config.PluginConfig;
import ru.deelter.waterphysics.engine.WaterQueue;

@RequiredArgsConstructor
public final class WaterEventListener implements Listener {

	private final PluginConfig config;
	private final BlockStateCache cache;
	private final WaterQueue queue;
	private final WaterPhysics plugin;

	/** Physics off or world unmanaged → vanilla owns the fluid, never cancel its events. */
	private boolean managed(Block block) {
		return plugin.isPhysicsEnabled() && config.isWorldEnabled(block.getWorld().getName());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockFromTo(BlockFromToEvent event) {
		Block from = event.getBlock();
		Material type = from.getType();

		boolean isWater = type == Material.WATER;
		boolean isLava = type == Material.LAVA;
		if (!isWater && !isLava) return;
		if (!managed(from)) return;

		// Leave lava fully vanilla unless lava physics is enabled — otherwise
		// cancelling its flow would freeze it (the engine wouldn't process it).
		if (isLava && !config.isLavaPhysics()) return;

		event.setCancelled(true);

		// In interaction-only mode the fluid flows only when disturbed by a
		// player action. We still cancel to prevent vanilla physics, but don't queue.
		if (!config.isInteractionOnly()) {
			cache.preload(from);
			cache.preload(event.getToBlock());
			queue.enqueue(from.getWorld(), from.getX(), from.getY(), from.getZ());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		// Only suppress vanilla physics in worlds we manage — elsewhere water
		// must behave normally (vanilla).
		if (!managed(event.getBlock())) return;
		Material mat = event.getBlock().getType();
		if (mat == Material.WATER || isSeaPlant(mat)
				|| (mat == Material.LAVA && config.isLavaPhysics())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockGrow(BlockGrowEvent event) {
		if (!config.isPreventSeaPlantGrowth() || !managed(event.getBlock())) return;
		if (isSeaPlant(event.getNewState().getType())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockSpread(BlockSpreadEvent event) {
		if (!config.isPreventSeaPlantGrowth() || !managed(event.getBlock())) return;
		if (isSeaPlant(event.getNewState().getType())) {
			event.setCancelled(true);
		}
	}

	private static boolean isSeaPlant(Material mat) {
		return BlockStateCache.SEA_PLANTS.contains(mat);
	}
}
