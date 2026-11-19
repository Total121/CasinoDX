package com.dravorax.casinodx.game.crash;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameSession;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.game.PayoutService;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import com.dravorax.casinodx.util.Text;
import org.bukkit.entity.Player;

/**
 * One crash flight.
 *
 * <p>{@link #crashPoint} is drawn in the constructor and is immutable for the life of the session.
 * The displayed multiplier is advanced by {@link CrashFlightAnimation}, but a cash-out is validated
 * against {@code crashPoint} rather than against whatever the client last saw, so neither lag nor a
 * crafted click can collect above the crash.
 */
public final class CrashSession extends GameSession {

    /** Where the round is in its life cycle. Only {@link #FLIGHT} accepts a cash-out. */
    public enum Phase {
        /** Cockpit is open, the rocket has not moved, and nothing is at stake yet. */
        WAITING_COUNTDOWN,
        /** The rocket is climbing towards the pre-drawn crash point. */
        FLIGHT,
        /** The round is over, one way or the other. */
        FINISHED
    }

    private final CrashGame game;
    private final double crashPoint;

    private volatile Phase phase = Phase.WAITING_COUNTDOWN;
    private volatile int countdownRemaining;
    private volatile double currentMultiplier;
    private volatile boolean crashed;
    private volatile boolean cashedOut;
    private double cashOutMultiplier;
    private CrashGui gui;

    CrashSession(CasinoDX plugin, Player player, CrashGame game, Money bet) {
        super(plugin, player, GameType.CRASH, bet);
        this.game = game;
        this.crashPoint = game.drawCrashPoint();
        this.currentMultiplier = 1.00;
        this.countdownRemaining = Math.max(0, game.countdownSeconds());
    }

    public CrashGame game() {
        return game;
    }

    public Phase phase() {
        return phase;
    }

    public boolean counting() {
        return phase == Phase.WAITING_COUNTDOWN;
    }

    /** Whole seconds left on the pre-launch countdown, for the cockpit read-out. */
    public int countdownRemaining() {
        return countdownRemaining;
    }

    public double currentMultiplier() {
        return currentMultiplier;
    }

    public boolean crashed() {
        return crashed;
    }

    public boolean cashedOut() {
        return cashedOut;
    }

    public double cashOutMultiplier() {
        return cashOutMultiplier;
    }

    /** Crash point, exposed only after the round ends so the GUI can show it. */
    public double revealedCrashPoint() {
        return isFinished() || crashed ? crashPoint : -1;
    }

    public Money currentPayout() {
        return bet().multiply(currentMultiplier);
    }

    /**
     * True while the rocket is climbing and a cash-out would be honoured. Deliberately false during
     * the pre-launch countdown, which is what stops a click landing before the rocket lifts.
     */
    public boolean inFlight() {
        return phase == Phase.FLIGHT && !crashed && !cashedOut && state() == State.ANIMATING;
    }

    /**
     * Opens the cockpit. With a countdown configured the rocket does not move yet: the player gets
     * {@code countdown-seconds} to read their stake and the recent crash points first.
     *
     * <p>Input is locked for the whole countdown — the session sits in {@link State#ANIMATING} so
     * {@link #acceptsInput()} is false — and leaving during it is a refund, because the player was
     * never given the chance to cash out.
     */
    public void launch() {
        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Player unavailable at launch");
            return;
        }

        CrashGui view = new CrashGui(plugin, player, this);
        this.gui = view;
        plugin.guis().open(view);

        state(State.ANIMATING);

        int countdownTicks = game.countdownTicks();
        if (countdownTicks <= 0) {
            beginFlight();
            return;
        }
        plugin.animations().register(new CrashCountdownAnimation(player, this, view, countdownTicks));
    }

    /** Called every countdown tick with the number of whole seconds still to go. */
    void updateCountdown(int remainingSeconds) {
        this.countdownRemaining = Math.max(0, remainingSeconds);
        if (gui != null) {
            gui.updateCountdown();
        }
    }

    /**
     * Hands the cockpit over to the flight animation once the countdown has run out. From here on
     * the round behaves exactly as it always has: the rocket climbs towards the crash point drawn
     * in the constructor, and abandoning it counts as riding it to the explosion.
     */
    void beginFlight() {
        if (phase != Phase.WAITING_COUNTDOWN || isSettled()) {
            return;
        }
        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Left during the countdown");
            return;
        }

        this.phase = Phase.FLIGHT;
        this.countdownRemaining = 0;
        state(State.ANIMATING);

        if (gui != null) {
            gui.onFlightStarted();
        }
        plugin.animations().register(new CrashFlightAnimation(player, this, gui, crashPoint, game.ticksPerStep()));
    }

    /** Called every animation step with the multiplier the rocket has reached. */
    void updateMultiplier(double multiplier) {
        this.currentMultiplier = multiplier;
        if (gui != null) {
            gui.updateFlight();
        }
    }

    /**
     * Attempts a cash-out at the current multiplier.
     *
     * <p>The guard below is the whole security model of the game: the multiplier is only accepted if
     * it is strictly under the pre-drawn crash point, so a click that arrives after the rocket blew
     * up can never be paid.
     */
    public void cashOut() {
        if (crashed || cashedOut || isSettled() || !inFlight()) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }

        double multiplier = currentMultiplier;
        if (multiplier >= crashPoint) {
            // The rocket is already gone; treat this as the crash it really is.
            onCrash();
            return;
        }

        this.cashedOut = true;
        this.cashOutMultiplier = multiplier;
        this.phase = Phase.FINISHED;
        plugin.animations().cancelForPlayer(playerId());

        Money gross = bet().multiply(multiplier);
        String label = "Cashed out at " + Text.formatMultiplier(multiplier);
        boolean paid = PayoutService.settleWin(plugin, player, this, bet(), gross, multiplier, label);
        if (paid) {
            plugin.sounds().play(player, SoundService.CASHOUT);
        }
        plugin.lastPlay().record(playerId(), type(), bet(), null, true, label, gross);
        game.recordCrash(crashPoint);
        if (gui != null) {
            gui.showResult(true, multiplier, crashPoint);
        }
        plugin.sessions().end(playerId());
    }

    /** Called by {@link CrashFlightAnimation} when the rocket reaches the crash point. */
    void onCrash() {
        if (crashed || cashedOut) {
            return;
        }
        this.crashed = true;
        this.currentMultiplier = crashPoint;
        this.phase = Phase.FINISHED;

        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Left before the crash");
            return;
        }

        plugin.sounds().play(player, SoundService.CRASH_EXPLODE);
        String label = "Crashed at " + Text.formatMultiplier(crashPoint);
        PayoutService.settleLoss(plugin, player, this, bet(), 0.0, label);
        plugin.lastPlay().record(playerId(), type(), bet(), null, false, label, Money.ZERO);
        game.recordCrash(crashPoint);
        if (gui != null) {
            gui.showResult(false, crashPoint, crashPoint);
        }
        plugin.sessions().end(playerId());
    }

    /**
     * An abandoned flight is not a refund: the crash point was fixed at launch, and leaving without
     * cashing out is the same as riding it to the explosion. Anything else would make "disconnect
     * when the rocket is high" a free roll.
     *
     * <p>The pre-launch countdown is the one exception. The rocket has not moved, no cash-out was
     * ever available, and the player has had no decision to get wrong — so returning {@code false}
     * here sends the round down {@code GameSession}'s refund path.
     */
    @Override
    protected boolean settleDecidedOutcome(String reason) {
        if (phase == Phase.WAITING_COUNTDOWN) {
            return false;
        }
        Player player = player();
        if (player == null) {
            return discardEscrow();
        }
        return PayoutService.settleLoss(plugin, player, this, bet(), 0.0,
                "Abandoned the flight (crash was " + Text.formatMultiplier(crashPoint) + ")");
    }

    @Override
    protected void onForceEnd(String reason) {
        crashed = true;
        phase = Phase.FINISHED;
        gui = null;
    }
}
