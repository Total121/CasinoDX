package com.dravorax.casinodx.game.coinflip;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.entity.Player;

/**
 * Coinflip — call heads or tails on a single toss.
 *
 * <h2>Where the house edge lives</h2>
 * There are two ways to give the house an edge on a coin: bias the coin, or pay less than even
 * money on a fair coin. <b>CasinoDX uses the second.</b> {@link #flip()} is an unbiased draw from
 * {@link java.security.SecureRandom}, so the player's call wins exactly 50% of the time, and the
 * edge comes entirely from the payout multiplier:
 * <pre>
 *   EV = P(win) * multiplier = 0.5 * multiplier
 *   houseEdge = 1 - EV     ⟹     multiplier = 2 * (1 - houseEdge)
 * </pre>
 * With the default {@code house-edge: 0.0} that is a true 2.00× — a break-even coin. Setting
 * {@code house-edge: 5.0} yields 1.90×, and so on.
 *
 * <p>An explicit {@code payouts.yml} multiplier overrides the derived value, which is how an
 * operator reaches a "40% effective win rate" target without touching the coin: a player who is
 * paid {@code 1.6×} on a fair coin loses value at the same rate as one paid {@code 2×} on a coin
 * that only lands their way 40% of the time. Biasing the coin itself is deliberately not
 * implemented — a fair draw with published odds is auditable, a rigged draw is not.
 *
 * <p>For the same reason there is no "force a win if the player is under quota" logic anywhere in
 * this class. Each flip is independent.
 */
public final class CoinflipGame extends CasinoGame {

    public enum Side {
        HEADS("Heads"),
        TAILS("Tails");

        private final String displayName;

        Side(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public Side other() {
            return this == HEADS ? TAILS : HEADS;
        }
    }

    private double payoutMultiplier;
    private int flipTicks;

    public CoinflipGame(CasinoDX plugin) {
        super(plugin, GameType.COINFLIP);
        reload();
    }

    @Override
    public void reload() {
        // Prefer an explicit multiplier; otherwise derive one from the configured house edge.
        double heads = payouts().getDouble("heads.multiplier", -1);
        double tails = payouts().getDouble("tails.multiplier", -1);

        double configured = Math.max(heads, tails);
        if (heads > 0 && tails > 0 && Math.abs(heads - tails) > 0.0001) {
            plugin.getLogger().warning("[CasinoDX] coinflip heads and tails multipliers differ ("
                    + heads + " vs " + tails + "); using the lower of the two to keep the coin fair.");
            configured = Math.min(heads, tails);
        }

        if (configured <= 1.0) {
            configured = 2.0 * (1.0 - houseEdge());
        }
        // A multiplier at or below 1.0 would mean a win pays less than the stake.
        this.payoutMultiplier = Math.max(1.01, configured);
        this.flipTicks = Math.max(20, Math.min(120, config().getInt("flip-duration-ticks", 50)));
    }

    /** Gross multiplier on a correct call, stake included. */
    public double payoutMultiplier() {
        return payoutMultiplier;
    }

    /** Effective house edge implied by the active multiplier, for display. */
    public double effectiveHouseEdge() {
        return 1.0 - 0.5 * payoutMultiplier;
    }

    public int flipTicks() {
        return flipTicks;
    }

    public Money grossReturn(Money bet) {
        return bet.multiply(payoutMultiplier);
    }

    /** One unbiased toss. The player's choice is not an input — the coin does not know it. */
    Side flip() {
        return randomInt(2) == 0 ? Side.HEADS : Side.TAILS;
    }

    @Override
    public void startSession(Player player, Money bet) {
        startSession(player, bet, null);
    }

    @Override
    public void startSession(Player player, Money bet, String selectionKey) {
        CoinflipSession session = beginSession(player, bet,
                wager -> new CoinflipSession(plugin, player, this, wager));
        if (session == null) {
            return;
        }
        session.openTable();

        // Restores the call, not the toss — the player still presses the button.
        Side restored = decodeSelection(selectionKey);
        if (restored != null) {
            session.choose(restored);
        }
    }

    @Override
    public boolean supportsSelection() {
        return true;
    }

    @Override
    public String describeSelection(String selectionKey) {
        Side side = decodeSelection(selectionKey);
        return side == null ? "" : side.displayName();
    }

    public static String encodeSelection(Side side) {
        return side == null ? null : side.name();
    }

    public static Side decodeSelection(String selectionKey) {
        if (selectionKey == null || selectionKey.isBlank()) {
            return null;
        }
        try {
            return Side.valueOf(selectionKey.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
