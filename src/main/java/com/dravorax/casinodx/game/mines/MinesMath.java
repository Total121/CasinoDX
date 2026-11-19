package com.dravorax.casinodx.game.mines;

/**
 * Pure multiplier math used by {@link MinesGame#multiplierFor(int)}.
 */
public final class MinesMath {

    private MinesMath() {
    }

    /**
     * Fair (zero-edge) gross multiplier after {@code safeReveals} successful picks.
     *
     * <p>Formula: {@code C(n, k) / C(n-m, k)} computed as a running product.
     */
    public static double fairMultiplier(int totalTiles, int safeTileCount, int safeReveals) {
        if (safeReveals <= 0) {
            return 1.0;
        }
        int clamped = Math.min(safeReveals, safeTileCount);
        double product = 1.0;
        for (int i = 0; i < clamped; i++) {
            product *= (double) (totalTiles - i) / (double) (safeTileCount - i);
        }
        return product;
    }

    /**
     * Paid multiplier with house edge applied and rounded to two decimals.
     */
    public static double multiplier(
            int totalTiles,
            int mineCount,
            int safeReveals,
            double houseEdgeFraction) {
        if (safeReveals <= 0) {
            return 1.0;
        }
        int safeTiles = totalTiles - mineCount;
        double fair = fairMultiplier(totalTiles, safeTiles, safeReveals);
        double withEdge = fair * (1.0 - houseEdgeFraction);
        return Math.max(1.0, Math.round(withEdge * 100.0) / 100.0);
    }
}
