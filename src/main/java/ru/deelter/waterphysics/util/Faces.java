package ru.deelter.waterphysics.util;

/**
 * Shared block-face offset tables. Index order: N, E, S, W, UP, DOWN — the first four entries are the
 * horizontal faces, so loops over {@code i < HORIZONTAL} reuse the same arrays.
 */
public final class Faces {

	public static final int[] DX = {0, 1, 0, -1, 0, 0};
	public static final int[] DY = {0, 0, 0, 0, 1, -1};
	public static final int[] DZ = {-1, 0, 1, 0, 0, 0};

	public static final int ALL = 6;
	public static final int HORIZONTAL = 4;

	private Faces() {
	}
}
