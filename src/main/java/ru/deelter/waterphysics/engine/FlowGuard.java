package ru.deelter.waterphysics.engine;

import org.bukkit.World;

/**
 * A veto another plugin can install to keep water physics out of ground it owns.
 *
 * <p>Minigames are the motivating case: an arena that is not currently running is a <i>saved map</i>,
 * not a live world. Letting water settle there rewrites the map between matches — a leftover pool
 * finds a gap left by the previous game and drains into it, and the change is then baked into the
 * arena everyone plays next. Flow is also simply wasted work on a map nobody is standing in.</p>
 *
 * <p>Guards are consulted at {@link WaterQueue#enqueue} — the single door every flow update comes
 * through — so a vetoed block never enters the queue at all.</p>
 *
 * <p>Called on the main thread, once per enqueue attempt: keep it cheap.</p>
 */
@FunctionalInterface
public interface FlowGuard {

	/**
	 * @return {@code true} to stop this block from ever being queued for flow.
	 */
	boolean isSuppressed(World world, int x, int y, int z);
}
