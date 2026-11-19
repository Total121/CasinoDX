package com.dravorax.casinodx.game;

import com.dravorax.casinodx.game.crash.CrashMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashMathTest {

    private static final double EDGE = 0.01;
    private static final double MIN = 1.0;
    private static final double MAX = 100.0;

    @Test
    void knownUniformValues() {
        assertEquals(1.00, CrashMath.crashPointFromUniform(0.0, EDGE, MIN, MAX), 0.001);
        assertEquals(1.98, CrashMath.crashPointFromUniform(0.5, EDGE, MIN, MAX), 0.001);
        assertEquals(9.90, CrashMath.crashPointFromUniform(0.9, EDGE, MIN, MAX), 0.001);
    }

    @Test
    void uniformNearOneIsClampedBeforeDivision() {
        double point = CrashMath.crashPointFromUniform(0.9999999, EDGE, MIN, MAX);
        assertTrue(point <= MAX);
        assertTrue(point >= MIN);
        assertEquals(MAX, point, 0.001);
    }

    @Test
    void resultClampedToMinMax() {
        assertEquals(2.0, CrashMath.crashPointFromUniform(0.0, EDGE, 2.0, MAX), 0.001);
        assertEquals(5.0, CrashMath.crashPointFromUniform(0.999, EDGE, MIN, 5.0), 0.001);
    }

    @Test
    void floorsToTwoDecimals() {
        double point = CrashMath.crashPointFromUniform(0.37, EDGE, MIN, MAX);
        assertEquals(Math.floor(point * 100) / 100.0, point, 0.0001);
    }

    @Test
    void expectedValueRoughlyOneMinusEdge() {
        double[] targets = {1.5, 2.0, 5.0, 10.0};
        for (double target : targets) {
            double ev = CrashMath.expectedValue(target, EDGE);
            assertEquals(1.0 - EDGE, ev, 0.001,
                    "EV at " + target + "x should equal 1 - edge");
        }
    }

    @Test
    void survivalProbabilityFormula() {
        assertEquals(0.99 / 2.0, CrashMath.survivalProbability(2.0, EDGE), 0.0001);
        assertEquals(0.99 / 10.0, CrashMath.survivalProbability(10.0, EDGE), 0.0001);
    }
}
