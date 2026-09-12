package ru.deelter.waterphysics.engine;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.deelter.waterphysics.config.PluginConfig;
import org.bukkit.util.Vector;

/**
 * Keeps boats afloat while the engine rewrites water levels underneath them.
 * <p>
 * The engine sets fluid levels with physics disabled, so a surface can jump above a boat in a single
 * write. Vanilla then classifies the boat as {@code UNDER_WATER} and sinks it (ejecting the rider).
 * After each engine pass we look at boats near players in the touched worlds and, if the water surface in
 * its column is above the boat's buoyancy line, add an upward push so vanilla buoyancy takes over.
 * The push is added to the current velocity rather than replacing it, so horizontal motion survives.
 */
final class BoatLifter {

	/** Vanilla fluid height for a full (source / falling) cell: 8 of 9 sub-levels. */
	private static final double FULL_FLUID_AMOUNT = 8.0;
	private static final double FLUID_HEIGHT_STEPS = 9.0;
	/** Levelled levels at or above this are "falling" water, which renders as a full cell. */
	private static final int FALLING_LEVEL = 8;
	/** Fraction of boat height that may sit below the surface — a floating boat is partly submerged. */
	private static final double FLOAT_DRAFT = 0.5;

	private final double liftVelocity;
	private final double checkRadius;

	BoatLifter(PluginConfig config) {
		this.liftVelocity = config.getBoatLiftVelocity();
		this.checkRadius = config.getBoatCheckRadius();
	}

	/**
	 * Only boats within {@code checkRadius} of a player are checked: nobody sees a boat sink elsewhere,
	 * and the nearby lookup uses the entity section index instead of scanning every entity in the world.
	 * A boat near two players may be visited twice — harmless, the second visit sees it already rising.
	 */
	void lift(World world) {
		for (Player player : world.getPlayers()) {
			for (Boat boat : player.getLocation().getNearbyEntitiesByType(Boat.class, checkRadius)) {
				liftBoat(world, boat);
			}
		}
	}

	private void liftBoat(World world, Boat boat) {
		Location loc = boat.getLocation();
		double surface = surfaceAbove(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
		if (Double.isNaN(surface)) return;
		if (surface - loc.getY() <= boat.getHeight() * FLOAT_DRAFT) return;

		Vector v = boat.getVelocity();
		// Already rising fast enough — stacking pushes every run would launch the boat.
		if (v.getY() >= liftVelocity) return;
		boat.setVelocity(v.add(new Vector(0, liftVelocity, 0)));
	}

	/**
	 * Absolute Y of the water surface in the column starting at the boat's block, or NaN when the
	 * boat's block holds no water (the boat rests on or above the surface).
	 */
	private static double surfaceAbove(World world, int x, int y, int z) {
		int maxY = world.getMaxHeight() - 1;
		double amount = fluidAmount(world.getBlockAt(x, y, z));
		if (amount <= 0) return Double.NaN;
		while (y < maxY) {
			double above = fluidAmount(world.getBlockAt(x, y + 1, z));
			if (above <= 0) break;
			amount = above;
			y++;
		}
		return y + amount / FLUID_HEIGHT_STEPS;
	}

	/** Vanilla fluid amount (0..8) of a cell: 8 for source/falling/waterlogged, 8 - level for flowing. */
	private static double fluidAmount(Block block) {
		BlockData data = block.getBlockData();
		if (block.getType() == Material.WATER && data instanceof Levelled ld) {
			int level = ld.getLevel();
			return (level == 0 || level >= FALLING_LEVEL) ? FULL_FLUID_AMOUNT : FULL_FLUID_AMOUNT - level;
		}
		if (data instanceof Waterlogged wl && wl.isWaterlogged()) return FULL_FLUID_AMOUNT;
		return 0;
	}
}
