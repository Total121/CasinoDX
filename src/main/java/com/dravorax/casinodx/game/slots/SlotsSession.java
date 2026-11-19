package com.dravorax.casinodx.game.slots;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameSession;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.game.PayoutService;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import org.bukkit.entity.Player;

/**
 * One spin of the slot machine: a wager is escrowed, the reels are drawn, and the money resolves.
 *
 * <p>The session is deliberately shorter-lived than the machine it belongs to.
 * {@link SlotsGui} stays open across as many spins as the player wants and asks
 * {@link SlotsGame#beginSpin} for a fresh session — and therefore a fresh escrow — each time SPIN
 * is pressed. That keeps the one-escrow-per-session invariant that the rest of the money-safety
 * machinery relies on, while still giving the player a continuous machine to sit at.
 *
 * <p>{@link #spin()} draws every reel stop from {@link SlotsGame#spinReels()} and scores the
 * result <em>before</em> {@link SlotsSpinAnimation} is registered. From that moment the outcome is
 * immutable for the life of the session: the animation reads {@link #symbols}, and
 * {@link #settleDecidedOutcome(String)} honours it if the round is interrupted, so a player who
 * disconnects on a jackpot still gets paid.
 */
public final class SlotsSession extends GameSession {

    public enum Phase {
        /** Wager escrowed, reels not yet drawn. */
        READY,
        /** Reels are turning. The outcome is already decided and input is locked. */
        SPINNING,
        /** Reels have stopped and the money has been resolved. */
        FINISHED
    }

    private final SlotsGame game;

    private volatile Phase phase = Phase.READY;
    private SlotsSymbol[] symbols;
    private SlotsGame.Outcome outcome;
    private Money payout = Money.ZERO;
    private SlotsGui gui;

    SlotsSession(CasinoDX plugin, Player player, SlotsGame game, Money bet) {
        super(plugin, player, GameType.SLOTS, bet);
        this.game = game;
    }

    public SlotsGame game() {
        return game;
    }

    public Phase phase() {
        return phase;
    }

    public boolean spinning() {
        return phase == Phase.SPINNING;
    }

    /** Binds the machine this spin plays out on. Called before {@link #spin()}. */
    void attach(SlotsGui gui) {
        this.gui = gui;
    }

    /** The drawn reel stops, or {@code null} until the player presses SPIN. */
    public SlotsSymbol[] symbols() {
        return symbols == null ? null : symbols.clone();
    }

    /** Score of the drawn spin, or {@code null} before the reels are drawn. */
    public SlotsGame.Outcome outcome() {
        return outcome;
    }

    /** What the round returned. Only meaningful once {@link #phase()} is {@link Phase#FINISHED}. */
    public Money payout() {
        return payout;
    }

    /** Best case for the escrowed stake, used to advertise the jackpot. */
    public Money jackpotReturn() {
        return bet().multiply(game.topMultiplier());
    }

    /**
     * Spins the reels. Every stop is drawn here; the animation that follows only plays it back.
     */
    public void spin() {
        if (!acceptsInput() || phase != Phase.READY) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }

        this.symbols = game.spinReels();
        this.outcome = game.evaluate(symbols);
        this.phase = Phase.SPINNING;
        state(State.ANIMATING);

        if (gui != null) {
            gui.refresh();
        }
        plugin.sounds().play(player, SoundService.SLOTS_SPIN);
        plugin.animations().register(new SlotsSpinAnimation(player, this, gui, symbols, game.spinTicks()));
    }

    /**
     * Called by {@link SlotsSpinAnimation} once the last reel has locked and the hold has run.
     *
     * <p>The session is retired here, but the machine is not: {@link SlotsGui#onSpinSettled} hands
     * the cabinet back in its READY state with the result still on screen, so the player never
     * passes through a play-again menu to spin again.
     */
    void onSpinFinished() {
        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Left while the reels were turning");
            return;
        }

        settleSpin(player);
        this.phase = Phase.FINISHED;

        boolean won = outcome != null && outcome.isWin();
        plugin.lastPlay().record(playerId(), type(), bet(), null, won,
                outcome == null ? "No spin" : outcome.label(), payout);

        SlotsGui machine = gui;
        plugin.sessions().end(playerId());
        if (machine != null) {
            machine.onSpinSettled(this);
        }
    }

    /** Moves the money for the already-drawn spin. Idempotent through {@link PayoutService}. */
    private void settleSpin(Player player) {
        if (outcome == null) {
            return;
        }
        String label = outcome.label();
        if (outcome.isWin()) {
            Money gross = game.grossReturn(bet(), outcome.multiplier());
            this.payout = gross;
            PayoutService.settleWin(plugin, player, this, bet(), gross, outcome.multiplier(), label);
        } else {
            this.payout = Money.ZERO;
            PayoutService.settleLoss(plugin, player, this, bet(), 0.0, label);
        }
    }

    /**
     * The reels are drawn the instant the player presses SPIN, so an interruption after that point
     * honours the draw rather than refunding it. Before the press nothing has been decided and the
     * default refund is the right answer.
     */
    @Override
    protected boolean settleDecidedOutcome(String reason) {
        if (symbols == null || outcome == null) {
            return false;
        }
        Player player = player();
        if (player != null) {
            settleSpin(player);
            phase = Phase.FINISHED;
            return true;
        }
        if (!outcome.isWin()) {
            return discardEscrow();
        }
        Money gross = game.grossReturn(bet(), outcome.multiplier());
        return PayoutService.settleWinOffline(plugin, playerId(), this, bet(), gross, outcome.multiplier());
    }

    @Override
    protected void onForceEnd(String reason) {
        if (phase != Phase.FINISHED) {
            phase = Phase.FINISHED;
        }
        gui = null;
    }
}
