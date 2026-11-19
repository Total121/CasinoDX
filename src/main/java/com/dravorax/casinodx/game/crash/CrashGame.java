package com.dravorax.casinodx.game.crash;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Crash — a rocket climbs, the player cashes out before it explodes.
 *
 * <h2>Crash-point generation</h2>
 * The crash point is drawn once, when the round starts, from the standard provably-fair
 * distribution. For a uniform {@code U ∈ [0,1)} and house edge {@code e}:
 * <pre>
 *   crashPoint = (1 - e) / (1 - U)
 * </pre>
 * That gives {@code P(crash ≥ x) = (1-e)/x} for {@code x ≥ 1-e}, so cashing out at any target
 * multiplier {@code x} has expected value
 * <pre>
 *   EV(x) = x * P(crash ≥ x) = x * (1-e)/x = 1 - e
 * </pre>
 * — identical regardless of strategy, which is exactly the property that makes the game fair. With
 * the default {@code house-edge: 1.0} the return to player is 99%. The result is floored to two
 * decimals and clamped to the configured {@code min-multiplier}/{@code max-multiplier} window.
 *
 * <p><b>The animation never decides the crash.</b> {@link CrashFlightAnimation} ticks the displayed
 * multiplier <em>towards</em> the already-drawn crash point and detonates when it arrives. Lag,
 * dropped ticks, or a cancelled animation cannot move the crash point, and a cash-out is only
 * honoured if the multiplier it is claimed at is below that stored value.
 */
public final class CrashGame extends CasinoGame {

    private static final int RECENT_HISTORY = 7;

    private final Deque<Double> recentCrashes = new ArrayDeque<>();

    private double minMultiplier;
    private double maxMultiplier;
    private int ticksPerStep;
    private int countdownSeconds;

    public CrashGame(CasinoDX plugin) {
        super(plugin, GameType.CRASH);
        reload();
    }

    @Override
    public void reload() {
        double configuredMin = config().getDouble("min-multiplier", 1.0);
        double configuredMax = config().getDouble("max-multiplier", 100.0);
        this.minMultiplier = Math.max(1.0, configuredMin);
        this.maxMultiplier = Math.max(this.minMultiplier + 0.01, configuredMax);
        // How many server ticks each 0.01 multiplier step takes at the start of the flight.
        this.ticksPerStep = Math.max(1, Math.min(10, config().getInt("ticks-per-step", 2)));
        // Pre-launch countdown, so the cockpit is on screen before the rocket moves. Zero skips it.
        this.countdownSeconds = Math.max(0, Math.min(10, config().getInt("countdown-seconds", 3)));
    }

    public double minMultiplier() {
        return minMultiplier;
    }

    public double maxMultiplier() {
        return maxMultiplier;
    }

    public int ticksPerStep() {
        return ticksPerStep;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    /** Countdown length in server ticks. {@code 0} means launch immediately. */
    public int countdownTicks() {
        return countdownSeconds * 20;
    }

    public List<Double> recentCrashes() {
        return new ArrayList<>(recentCrashes);
    }

    void recordCrash(double crashPoint) {
        recentCrashes.addFirst(crashPoint);
        while (recentCrashes.size() > RECENT_HISTORY) {
            recentCrashes.removeLast();
        }
    }

    /**
     * Draws this round's crash point. See the class documentation for the distribution; the result
     * is fixed for the whole round and is never recomputed.
     */
    double drawCrashPoint() {
        return CrashMath.crashPointFromUniform(
                randomDouble(), houseEdge(), minMultiplier, maxMultiplier);
    }

    public Money grossReturn(Money bet, double multiplier) {
        return bet.multiply(multiplier);
    }

    @Override
    public void startSession(Player player, Money bet) {
        CrashSession session = beginSession(player, bet,
                wager -> new CrashSession(plugin, player, this, wager));
        if (session == null) {
            return;
        }
        session.launch();
    }
}
