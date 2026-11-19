package com.dravorax.casinodx.game;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.money.Money;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * One player's in-progress round of a casino game.
 *
 * <p><b>Money safety contract.</b> The wager is withdrawn before the session is registered, so from
 * the session's point of view the bet is already escrowed. Exactly one of three things may then
 * happen to that escrow, and {@link #claimSettlement()} is the single gate that decides which:
 * <ul>
 *   <li>the round resolves normally and {@link com.dravorax.casinodx.game.PayoutService} pays out,</li>
 *   <li>the round is abandoned and the escrow is refunded, or</li>
 *   <li>the round was already resolved, in which case nothing further happens.</li>
 * </ul>
 * Because {@code claimSettlement()} is a synchronized compare-and-set on {@link #settled}, a
 * cash-out racing with a disconnect, a plugin reload, or a crash tick can never pay twice.
 */
public abstract class GameSession {

    public enum State {
        /** Created but the wager has not been escrowed yet. */
        CREATED,
        /** Wager escrowed; the player may still act. */
        ACTIVE,
        /** Outcome already decided server-side; an animation is visualising it. Input is locked. */
        ANIMATING,
        /** Money movement in progress. Input is locked. */
        SETTLING,
        /** Terminal state — money has been resolved exactly once. */
        FINISHED
    }

    protected final CasinoDX plugin;
    private final UUID playerId;
    private final GameType type;
    private final Money bet;
    private final long startedAt = System.currentTimeMillis();

    /** Guards {@link #settled} and {@link #escrowHeld}; also the lock every payout path takes. */
    private final Object payoutLock = new Object();

    private volatile State state = State.CREATED;
    private boolean escrowHeld;
    private boolean settled;

    /** Wagers escrowed after the round started (blackjack doubles and splits). */
    private Money extraEscrow = Money.ZERO;

    protected GameSession(CasinoDX plugin, Player player, GameType type, Money bet) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.type = type;
        this.bet = bet == null ? Money.ZERO : bet;
    }

    public UUID playerId() {
        return playerId;
    }

    public GameType type() {
        return type;
    }

    public Money bet() {
        return bet;
    }

    public State state() {
        return state;
    }

    public long startedAt() {
        return startedAt;
    }

    public Player player() {
        return Bukkit.getPlayer(playerId);
    }

    /** The player's live name, or their UUID when they are offline. */
    public String playerName() {
        Player player = player();
        return player == null ? playerId.toString() : player.getName();
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    /** True while the player is allowed to interact (no animation or payout in flight). */
    public boolean acceptsInput() {
        return state == State.ACTIVE;
    }

    public boolean isSettled() {
        synchronized (payoutLock) {
            return settled;
        }
    }

    protected void state(State next) {
        if (next != null) {
            this.state = next;
        }
    }

    /**
     * Marks the escrowed wager as held by this session. Called by the game once the economy
     * withdrawal has succeeded.
     */
    public void markEscrowed() {
        synchronized (payoutLock) {
            escrowHeld = true;
        }
        if (state == State.CREATED) {
            state = State.ACTIVE;
        }
    }

    public boolean hasEscrow() {
        synchronized (payoutLock) {
            return escrowHeld;
        }
    }

    /**
     * Records an additional wager that has already been withdrawn, so that an interruption refunds
     * the full amount at risk rather than just the opening bet.
     */
    public void addEscrow(Money amount) {
        if (amount == null || !amount.isPositive()) {
            return;
        }
        synchronized (payoutLock) {
            extraEscrow = extraEscrow.add(amount);
        }
    }

    /** Total amount currently at risk: the opening bet plus any doubles or splits. */
    public Money totalEscrow() {
        synchronized (payoutLock) {
            return bet.add(extraEscrow);
        }
    }

    /**
     * Atomically claims the right to resolve this session's money.
     *
     * @return {@code true} for exactly one caller; every later caller receives {@code false}
     */
    public boolean claimSettlement() {
        synchronized (payoutLock) {
            if (settled) {
                return false;
            }
            settled = true;
            escrowHeld = false;
            state = State.SETTLING;
            return true;
        }
    }

    /** Called by {@link PayoutService} once money movement for this session is complete. */
    public void markResolved() {
        state = State.FINISHED;
    }

    /**
     * Ends the session safely, resolving the escrowed wager exactly once.
     *
     * <p>If the outcome was already decided before the interruption (see
     * {@link #settleDecidedOutcome(String)}), that decision is honoured so a player who was
     * mid-animation on a winning round is not robbed. Otherwise the wager is refunded. If the money was already
     * paid, this is a no-op.
     */
    public final void forceEnd(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "Session ended" : reason;

        synchronized (payoutLock) {
            if (settled) {
                state = State.FINISHED;
                safeCleanup(safeReason);
                return;
            }
        }

        boolean handled = false;
        try {
            handled = settleDecidedOutcome(safeReason);
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Interrupted settlement failed for " + type.displayName()
                    + " session of " + playerName() + ": " + t);
        }
        if (!handled) {
            refundEscrow(safeReason);
        }
        safeCleanup(safeReason);
    }

    /** Refunds the escrowed wager if it is still held, then marks the session finished. */
    private void refundEscrow(String reason) {
        boolean refund;
        Money amount;
        synchronized (payoutLock) {
            refund = escrowHeld && !settled;
            amount = bet.add(extraEscrow);
            if (refund) {
                settled = true;
                escrowHeld = false;
            }
            state = State.FINISHED;
        }
        if (!refund || !amount.isPositive()) {
            return;
        }

        var offline = Bukkit.getOfflinePlayer(playerId);
        plugin.economy().refund(offline, amount);

        Player online = player();
        if (online != null) {
            plugin.messages().send(online, "session-refunded",
                    "amount", plugin.economy().format(amount),
                    "reason", reason);
        } else {
            plugin.getLogger().info("[CasinoDX] Refunded " + plugin.economy().format(amount)
                    + " to offline player " + playerId + " (" + reason + ").");
        }
    }

    private void safeCleanup(String reason) {
        try {
            onForceEnd(reason);
        } catch (Throwable t) {
            plugin.getLogger().warning("[CasinoDX] Cleanup failed for " + type.displayName()
                    + " session of " + playerName() + ": " + t.getMessage());
        }
        try {
            plugin.animations().cancelForPlayer(playerId);
        } catch (Throwable ignored) {
            // Animation engine already shut down.
        }
    }

    /**
     * Hook for a session that was interrupted <em>after</em> its outcome was decided server-side but
     * before the money was resolved — a disconnect halfway through a crash flight, say.
     *
     * <p>Implementations settle the already-known result through
     * {@link PayoutService} (or {@link #discardEscrow()} when the player is gone and the result was
     * a loss) and return {@code true}. Returning {@code false}, the default, refunds the wager,
     * which is the right answer whenever the outcome was still open.
     */
    protected boolean settleDecidedOutcome(String reason) {
        return false;
    }

    /**
     * Consumes the escrowed wager without paying anything, for a decided loss whose player is no
     * longer online to receive messages or statistics.
     *
     * @return {@code true} when this call owned the settlement
     */
    protected final boolean discardEscrow() {
        if (!claimSettlement()) {
            return false;
        }
        markResolved();
        return true;
    }

    /** Release view/animation references. Must not move money. */
    protected abstract void onForceEnd(String reason);
}
