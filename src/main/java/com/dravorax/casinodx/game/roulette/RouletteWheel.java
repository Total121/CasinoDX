package com.dravorax.casinodx.game.roulette;

import org.bukkit.Material;

import java.util.Set;

/**
 * Static facts about a single-zero (European) roulette wheel.
 *
 * <p>European rather than American: 37 pockets, one zero. The house edge of a single-zero wheel is
 * {@code 1/37 ≈ 2.70%} on every standard bet, which is why the default {@code house-edge} in
 * {@code modules/roulette/config.yml} is 2.7 — the edge is already baked into the classic odds and
 * is not applied a second time.
 */
public final class RouletteWheel {

    public static final int POCKETS = 37;

    /** Pocket order going clockwise around a real European wheel. Drives the spin animation. */
    public static final int[] ORDER = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
            10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };

    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    public enum Color {
        RED,
        BLACK,
        GREEN
    }

    private RouletteWheel() {}

    public static Color colorOf(int number) {
        if (number == 0) {
            return Color.GREEN;
        }
        return RED_NUMBERS.contains(number) ? Color.RED : Color.BLACK;
    }

    /** Concrete block matching the pocket colour, for the betting table and spin strip. */
    public static Material materialOf(int number) {
        return switch (colorOf(number)) {
            case RED -> Material.RED_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
        };
    }

    /** Colour code for chat/lore text matching the pocket colour. */
    public static String colorCode(int number) {
        return switch (colorOf(number)) {
            case RED -> "&c";
            case BLACK -> "&8";
            case GREEN -> "&a";
        };
    }

    public static boolean isRed(int number) {
        return colorOf(number) == Color.RED;
    }

    public static boolean isBlack(int number) {
        return colorOf(number) == Color.BLACK;
    }

    /** 1–12 → 1, 13–24 → 2, 25–36 → 3, zero → 0. */
    public static int dozenOf(int number) {
        if (number < 1 || number > 36) {
            return 0;
        }
        return ((number - 1) / 12) + 1;
    }

    /** Table columns run 1,4,7,… / 2,5,8,… / 3,6,9,…; zero belongs to none. */
    public static int columnOf(int number) {
        if (number < 1 || number > 36) {
            return 0;
        }
        return ((number - 1) % 3) + 1;
    }

    public static int indexInOrder(int number) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == number) {
                return i;
            }
        }
        return 0;
    }

    public static boolean isValidPocket(int number) {
        return number >= 0 && number <= 36;
    }
}
