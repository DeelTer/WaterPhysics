package ru.deelter.waterphysics.config;

import lombok.Getter;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * All config values are read ONCE and stored as primitives.
 * Zero YAML lookups during water flow processing — direct field reads.
 */
@Getter
public final class PluginConfig {

	// Global
	private final boolean enabled;

	// Worlds
	private final Set<String> enabledWorlds;
	private final boolean allWorldsEnabled;

	// Biome exclusion
	private final Set<Biome> excludedBiomes;

	// Flow
	private final boolean interactionOnly;
	private final boolean equalizeWaterLevels;
	private final int batchSize;
	private final int tickInterval;

	// Puddle removal (evaporate stranded thin films with nowhere to flow)
	private final boolean removePuddles;
	private final int removePuddleMaxUnits;

	// Optimization
	private final boolean playerProximityCheck;
	private final int playerProximityChunks;
	private final long cacheTtlSeconds;
	private final long cacheMaxSize;
	private final boolean chunkRescanOnLoad;
	private final int chunkScanMaxBlocks;

	// Waterlogged
	private final boolean waterlogEnabled;
	private final int waterlogMaxLevel;

	// Sea plants
	private final boolean removeSeaPlants;
	private final boolean preventSeaPlantGrowth;

	// Bucket
	private final boolean bucketPhysicsEnabled;
	private final int bucketScanRadius;

	// Lava
	private final boolean convertLava;
	private final boolean convertLavaSource;
	private final boolean lavaPhysics;

	// Sounds
	private final boolean soundsEnabled;
	private final int soundRateLimitTicks;
	private final float soundVolume;
	private final float soundPitch;

	// Visual effects
	private final boolean effectsEnabled;
	private final int effectsRateLimitTicks;
	private final int effectsCount;

	public PluginConfig(FileConfiguration cfg) {
		this.enabled = cfg.getBoolean("enabled", true);

		List<String> worlds = cfg.getStringList("worlds");
		this.allWorldsEnabled = worlds.isEmpty() || worlds.contains("*");
		this.enabledWorlds = allWorldsEnabled ? Collections.emptySet() : new HashSet<>(worlds);

		List<String> biomeKeys = cfg.getStringList("optimization.excluded-biomes");
		Set<Biome> biomes = new HashSet<>();
		for (String key : biomeKeys) {
			Biome b = Registry.BIOME.get(NamespacedKey.minecraft(key.toLowerCase()));
			if (b != null) biomes.add(b);
		}
		this.excludedBiomes = Collections.unmodifiableSet(biomes);

		this.interactionOnly = cfg.getBoolean("flow.interaction-only", true);
		this.equalizeWaterLevels = cfg.getBoolean("flow.equalize-water-levels", true);
		this.batchSize = Math.max(1, cfg.getInt("flow.batch-size", 512));
		this.tickInterval = Math.max(1, cfg.getInt("flow.tick-interval", 1));

		this.removePuddles = cfg.getBoolean("flow.remove-puddles", true);
		this.removePuddleMaxUnits = Math.max(1, Math.min(7, cfg.getInt("flow.puddle-max-units", 1)));

		this.playerProximityCheck = cfg.getBoolean("optimization.player-proximity-check", true);
		this.playerProximityChunks = Math.max(1, cfg.getInt("optimization.player-proximity-chunks", 4));
		this.cacheTtlSeconds = Math.max(10L, cfg.getLong("optimization.cache-ttl-seconds", 60));
		this.cacheMaxSize = Math.max(1000L, cfg.getLong("optimization.cache-max-size", 100_000));
		this.chunkRescanOnLoad = cfg.getBoolean("optimization.chunk-rescan-on-load", true);
		this.chunkScanMaxBlocks = Math.max(100, cfg.getInt("optimization.chunk-scan-max-blocks", 2000));

		this.waterlogEnabled = cfg.getBoolean("waterlogged.enabled", true);
		this.waterlogMaxLevel = Math.min(7, Math.max(0, cfg.getInt("waterlogged.max-level", 3)));

		this.removeSeaPlants = cfg.getBoolean("sea-plants.remove-on-flow", true);
		this.preventSeaPlantGrowth = cfg.getBoolean("sea-plants.prevent-growth", true);

		this.bucketPhysicsEnabled = cfg.getBoolean("bucket.enabled", true);
		this.bucketScanRadius = Math.max(1, Math.min(16, cfg.getInt("bucket.scan-radius", 8)));

		this.convertLava = cfg.getBoolean("lava.convert-to-cobblestone", true);
		this.convertLavaSource = cfg.getBoolean("lava.convert-source-to-obsidian", true);
		this.lavaPhysics = cfg.getBoolean("lava.apply-physics", false);

		this.soundsEnabled = cfg.getBoolean("sounds.enabled", true);
		this.soundRateLimitTicks = Math.max(1, cfg.getInt("sounds.rate-limit-ticks", 10));
		this.soundVolume = (float) Math.max(0.0, Math.min(1.0, cfg.getDouble("sounds.volume", 0.35)));
		this.soundPitch = (float) Math.max(0.1, Math.min(2.0, cfg.getDouble("sounds.pitch", 1.0)));

		this.effectsEnabled = cfg.getBoolean("effects.enabled", true);
		this.effectsRateLimitTicks = Math.max(1, cfg.getInt("effects.rate-limit-ticks", 4));
		this.effectsCount = Math.max(1, Math.min(50, cfg.getInt("effects.count", 6)));
	}

	public boolean isWorldEnabled(String worldName) {
		return allWorldsEnabled || enabledWorlds.contains(worldName);
	}

	public Set<Biome> getExcludedBiomes() {
		return excludedBiomes;
	}

	public boolean isBiomeExcluded(Biome biome) {
		return !excludedBiomes.isEmpty() && excludedBiomes.contains(biome);
	}
}
