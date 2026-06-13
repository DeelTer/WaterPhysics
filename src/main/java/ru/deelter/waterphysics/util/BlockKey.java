package ru.deelter.waterphysics.util;

import org.bukkit.block.Block;

/**
 * Encodes 3D block coordinates into a single {@code long} for use as a
 * zero-allocation map key.  Using Block objects as keys requires expensive
 * hashCode/equals (world UUID + coords).  Long keys are primitive — no boxing
 * overhead in Caffeine's internal long→long store.
 *
 * Bit layout (64 bits):
 *   bits 63-38 (26 bits) = X   range ±33 554 431  (MC limit ±30 000 000 ✓)
 *   bits 37-26 (12 bits) = Y   range ±2 048        (MC range -64..320 ✓)
 *   bits 25-0  (26 bits) = Z   range ±33 554 431
 */
public final class BlockKey {

    private BlockKey() {}

    public static long of(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
             | ((long) (y & 0xFFF)     << 26)
             |  (long) (z & 0x3FFFFFF);
    }

    public static long of(Block block) {
        return of(block.getX(), block.getY(), block.getZ());
    }

    public static int x(long key) {
        int raw = (int) ((key >> 38) & 0x3FFFFFF);
        return raw >= 0x2000000 ? raw | 0xFC000000 : raw;
    }

    public static int y(long key) {
        int raw = (int) ((key >> 26) & 0xFFF);
        return raw >= 0x800 ? raw | 0xFFFFF000 : raw;
    }

    public static int z(long key) {
        int raw = (int) (key & 0x3FFFFFF);
        return raw >= 0x2000000 ? raw | 0xFC000000 : raw;
    }
}
