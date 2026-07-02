package ru.deelter.waterphysics;

import lombok.Getter;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.deelter.waterphysics.cache.BlockStateCache;
import ru.deelter.waterphysics.cache.PlayerChunkCache;
import ru.deelter.waterphysics.command.WaterCommand;
import ru.deelter.waterphysics.config.PluginConfig;
import org.bstats.bukkit.Metrics;
import ru.deelter.waterphysics.engine.FlowEngine;
import ru.deelter.waterphysics.engine.WaterQueue;
import ru.deelter.waterphysics.metrics.PluginMetrics;
import ru.deelter.waterphysics.listener.BlockListener;
import ru.deelter.waterphysics.listener.BucketListener;
import ru.deelter.waterphysics.listener.ChunkListener;
import ru.deelter.waterphysics.listener.WaterEventListener;

public final class WaterPhysics extends JavaPlugin {

    private PluginConfig     config;
    private BlockStateCache  cache;
    private PlayerChunkCache proximity;
    private WaterQueue        queue;
    private FlowEngine        engine;
    private BukkitTask        engineTask;
    private Metrics           metrics;
    @Getter
    private boolean           physicsEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        physicsEnabled = config.isEnabled();

        cache     = new BlockStateCache(config);
        proximity = new PlayerChunkCache(config);
        queue     = new WaterQueue(proximity);

        // Only start engine if enabled in config
        if (physicsEnabled) startEngine();

        registerListeners();

        metrics = PluginMetrics.start(this, config);

        WaterCommand cmd = new WaterCommand(this, queue);
        var cmdObj = getCommand("waterphysics");
        if (cmdObj != null) {
            cmdObj.setExecutor(cmd);
            cmdObj.setTabCompleter(cmd);
        }

        getLogger().info("WaterPhysics enabled. Physics: " + (physicsEnabled ? "ON" : "OFF")
            + ", mode=" + (config.isInteractionOnly() ? "INTERACTION" : "CONTINUOUS")
            + ", batch=" + config.getBatchSize()
            + ", interval=" + config.getTickInterval() + "t"
            + ", waterlog=" + config.isWaterlogEnabled() + ".");
    }

    @Override
    public void onDisable() {
        if (metrics != null) metrics.shutdown();
        if (engineTask != null) engineTask.cancel();

        // SHUTDOWN SAFETY: flush remaining queue so no water blocks are left
        // in a dangling state after a hard server restart.
        if (queue != null && cache != null) {
            int drained = 0;
            while (!queue.isEmpty() && drained++ < 10_000) {
                WaterQueue.Entry entry = queue.poll();
                if (entry == null) break;
                flushBlockFromCache(entry.world(), entry.x(), entry.y(), entry.z());
            }
            cache.clearAll();
            queue.clear();
        }

        getLogger().info("WaterPhysics disabled.");
    }

    // ---- Public API for other plugins ---------------------------------------

    /**
     * Purge all cached block state + pending flow entries inside a region of one world. Call this
     * AFTER any bulk block rewrite your plugin does without firing Bukkit block events (arena
     * snapshot restores, WorldEdit-style operations, etc.) — otherwise this engine's cache and queue
     * keep the pre-rewrite state and can write stale water back onto the region afterward.
     * <p>
     * Other plugins should soft-depend on WaterPhysics and call this via
     * {@code JavaPlugin.getPlugin(WaterPhysics.class).purgeRegion(...)}.
     */
    public void purgeRegion(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        cache.purgeRegion(world.getUID(), minX, minY, minZ, maxX, maxY, maxZ);
        queue.purgeRegion(world, minX, minY, minZ, maxX, maxY, maxZ);
    }

	public void setPhysicsEnabled(boolean enabled) {
        this.physicsEnabled = enabled;
        if (!enabled) queue.clear();
        restartEngine();
    }

    public void reload() {
        reloadConfig();
        loadConfig();
        cache.clearAll();
        queue.clear();
        restartEngine();
        // Unregister old listeners before re-registering to prevent duplicate events
        org.bukkit.event.HandlerList.unregisterAll(this);
        registerListeners();
    }

    // ---- Internal -----------------------------------------------------------

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WaterEventListener(config, cache, queue), this);
        getServer().getPluginManager().registerEvents(new BlockListener(cache, queue), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(config, cache, queue, this), this);
        getServer().getPluginManager().registerEvents(new BucketListener(config, cache, queue, this), this);
    }

    private void loadConfig() {
        config = new PluginConfig(getConfig());
    }

    private void startEngine() {
        engine     = new FlowEngine(config, cache, proximity, queue);
        engineTask = engine.runTaskTimer(this, 20L, config.getTickInterval());
    }

    private void restartEngine() {
        if (engineTask != null) { engineTask.cancel(); engineTask = null; }
        if (physicsEnabled) startEngine();
    }

    /**
     * Best-effort: apply any cached level change to the world block.
     * Called per-entry during shutdown drain to prevent dangling water.
     */
    private void flushBlockFromCache(World world, int x, int y, int z) {
        try {
            byte type = cache.getType(world, x, y, z);
            if (type != BlockStateCache.TYPE_WATER) return;
            int   level = cache.getLevel(world, x, y, z);
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == org.bukkit.Material.WATER
                    && block.getBlockData() instanceof Levelled ld
                    && ld.getLevel() != level) {
                ld.setLevel(level);
                block.setBlockData(ld, false);
            }
        } catch (Exception ignored) {
            // Best-effort on crash/shutdown — do not rethrow
        }
    }
}
