package ru.deelter.waterphysics.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import ru.deelter.waterphysics.WaterPhysics;
import ru.deelter.waterphysics.config.PluginConfig;

/**
 * bStats integration. Reports a handful of useful config toggles so we can see
 * how the plugin is actually configured across servers.
 * <p>
 * Kept out of {@link WaterPhysics} so the metrics setup lives in one place and
 * can be disabled/extended without touching the plugin bootstrap.
 */
public final class PluginMetrics {

	// https://bstats.org/what-is-my-plugin-id
	private static final int PLUGIN_ID = 32110;

	private PluginMetrics() {
	}

	public static Metrics start(WaterPhysics plugin, PluginConfig config) {
		Metrics metrics = new Metrics(plugin, PLUGIN_ID);

		metrics.addCustomChart(new SimplePie("flow_mode",
				() -> config.isInteractionOnly() ? "interaction-only" : "continuous"));

		metrics.addCustomChart(new SimplePie("worlds_mode",
				() -> config.isAllWorldsEnabled() ? "all-worlds" : "whitelist"));

		metrics.addCustomChart(new SimplePie("player_proximity_check",
				() -> onOff(config.isPlayerProximityCheck())));

		metrics.addCustomChart(new SimplePie("waterlogging",
				() -> onOff(config.isWaterlogEnabled())));

		metrics.addCustomChart(new SimplePie("lava_physics",
				() -> onOff(config.isLavaPhysics())));

		metrics.addCustomChart(new SimplePie("remove_puddles",
				() -> onOff(config.isRemovePuddles())));

		metrics.addCustomChart(new SimplePie("effects",
				() -> onOff(config.isEffectsEnabled())));

		metrics.addCustomChart(new SimplePie("sounds",
				() -> onOff(config.isSoundsEnabled())));

		metrics.addCustomChart(new SimplePie("tick_interval",
				() -> String.valueOf(config.getTickInterval())));

		metrics.addCustomChart(new SimplePie("batch_size",
				() -> String.valueOf(config.getBatchSize())));

		return metrics;
	}

	private static String onOff(boolean value) {
		return value ? "enabled" : "disabled";
	}
}
