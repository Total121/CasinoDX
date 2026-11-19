package com.dravorax.casinodx.game;

import com.dravorax.casinodx.game.mines.MinesMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinesMathTest {

    private static final int GRID = 25;
    private static final int MINES = 3;
    private static final int SAFE = GRID - MINES;
    private static final double EDGE = 0.01;

    @Test
    void zeroRevealsReturnsOne() {
        assertEquals(1.0, MinesMath.multiplier(GRID, MINES, 0, EDGE));
        assertEquals(1.0, MinesMath.fairMultiplier(GRID, SAFE, 0));
    }

    @Test
    void fairMultiplierMatchesCombinatorialFormula() {
        // C(25,1)/C(22,1) = 25/22
        assertEquals(25.0 / 22.0, MinesMath.fairMultiplier(GRID, SAFE, 1), 0.0001);

        // C(25,2)/C(22,2) = (25*24)/(22*21)
        double expectedTwo = (25.0 * 24.0) / (22.0 * 21.0);
        assertEquals(expectedTwo, MinesMath.fairMultiplier(GRID, SAFE, 2), 0.0001);
    }

    @Test
    void houseEdgeAppliedToFairMultiplier() {
        double fair = MinesMath.fairMultiplier(GRID, SAFE, 1);
        double paid = MinesMath.multiplier(GRID, MINES, 1, EDGE);
        assertEquals(Math.max(1.0, Math.round(fair * 0.99 * 100.0) / 100.0), paid, 0.0001);
    }

    @Test
    void revealsClampedToSafeTileCount() {
        double atMax = MinesMath.multiplier(GRID, MINES, SAFE + 5, EDGE);
        double atSafe = MinesMath.multiplier(GRID, MINES, SAFE, EDGE);
        assertEquals(atSafe, atMax, 0.0001);
    }

    @Test
    void multiplierNeverBelowOne() {
        double paid = MinesMath.multiplier(9, 8, 1, 0.99);
        assertTrue(paid >= 1.0);
    }

    @Test
    void smallerBoardHigherRiskCurve() {
        double small = MinesMath.fairMultiplier(9, 6, 1);
        double large = MinesMath.fairMultiplier(GRID, SAFE, 1);
        assertTrue(small > large);
    }
}
