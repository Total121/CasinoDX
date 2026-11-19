package com.dravorax.casinodx.game.crash;

/**
 * Pure crash-point math used by {@link CrashGame#drawCrashPoint()}.
 */
public final class CrashMath {

    private CrashMath() {
    }

    /**
     * Converts a uniform draw {@code U ∈ [0,1)} into a crash multiplier.
     *
     * @param uniform            value in {@code [0, 1)}
     * @param houseEdgeFraction  house edge as a fraction (e.g. {@code 0.01} for 1%)
     * @param minMultiplier      lower clamp
     * @param maxMultiplier      upper clamp
     */
    public static double crashPointFromUniform(
            double uniform,
            double houseEdgeFraction,
            double minMultiplier,
            double maxMultiplier) {
        double u = uniform;
        if (u >= 0.999999) {
            u = 0.999999;
        }
        double raw = (1.0 - houseEdgeFraction) / (1.0 - u);
        double floored = Math.floor(raw * 100.0) / 100.0;
        return Math.max(minMultiplier, Math.min(maxMultiplier, floored));
    }

    /**
     * Probability that the crash point is at least {@code multiplier}.
     * For the standard distribution: {@code P(crash ≥ x) = (1-e)/x} when {@code x ≥ 1-e}.
     */
    public static double survivalProbability(double multiplier, double houseEdgeFraction) {
        if (multiplier <= 0) {
            return 0;
        }
        double edgeFactor = 1.0 - houseEdgeFraction;
        if (multiplier < edgeFactor) {
            return 1.0;
        }
        return edgeFactor / multiplier;
    }

    /**
     * Expected value of cashing out at {@code multiplier} under the standard crash distribution.
     * Should equal {@code 1 - houseEdgeFraction} for any valid target.
     */
    public static double expectedValue(double multiplier, double houseEdgeFraction) {
        return multiplier * survivalProbability(multiplier, houseEdgeFraction);
    }
}
