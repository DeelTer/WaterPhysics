package ru.deelter.waterphysics.cache;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import ru.deelter.waterphysics.config.PluginConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caches which chunk coordinates (per world) have at least one online player
 * within the configured chunk radius.  Updated every 20 ticks on main thread.
 *
 * Replaces the original per-block sqrt() distance check against all players.
 * isActive() = O(1) HashSet lookup.
 */
@RequiredArgsConstructor
public final class PlayerChunkCache {

    private final PluginConfig config;

    private final Map<UUID, Set<Long>> activeChunks = new HashMap<>();

    public void update(Collection<? extends Player> players) {
        activeChunks.clear();
        if (!config.isPlayerProximityCheck()) return;

        int radius = config.getPlayerProximityChunks();
        for (Player player : players) {
            UUID wid  = player.getWorld().getUID();
            int  pcx  = player.getLocation().getBlockX() >> 4;
            int  pcz  = player.getLocation().getBlockZ() >> 4;
            Set<Long> chunks = activeChunks.computeIfAbsent(wid, k -> new HashSet<>());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    chunks.add(chunkKey(pcx + dx, pcz + dz));
                }
            }
        }
    }

    public boolean isActive(UUID worldId, int blockX, int blockZ) {
        if (!config.isPlayerProximityCheck()) return true;
        Set<Long> chunks = activeChunks.get(worldId);
        return chunks != null && chunks.contains(chunkKey(blockX >> 4, blockZ >> 4));
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
