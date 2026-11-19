package com.dravorax.casinodx.game.blackjack;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.entity.Player;

/**
 * Blackjack against the house.
 *
 * <h2>Rules and where the edge comes from</h2>
 * Blackjack's house edge is a property of the rule set, not of a multiplier: the player acts first
 * and busts before the dealer does, which is worth roughly 8% to the house, and the player claws
 * most of that back through the 3:2 natural, doubling, and splitting. The configurable knobs below
 * are the ones that actually move the number:
 * <ul>
 *   <li>{@code blackjack.multiplier} — 2.5 (3:2) is standard; 2.4 (6:5) costs the player ~0.45%.</li>
 *   <li>{@code dealer-hits-soft-17} — hitting soft 17 is worth ~0.22% to the house.</li>
 *   <li>{@code decks} — more decks slightly favour the house.</li>
 *   <li>{@code allow-double-down} / {@code allow-split} — removing either raises the edge sharply.</li>
 * </ul>
 * With the defaults (six decks, 3:2 naturals, dealer hits soft 17, doubling and one split allowed)
 * the edge is about 0.5%, which matches the default {@code house-edge: 0.5}. That config value is
 * documentation: the edge emerges from the rules and is never applied as an extra deduction.
 */
public final class BlackjackGame extends CasinoGame {

    private double blackjackMultiplier;
    private double winMultiplier;
    private boolean dealerHitsSoft17;
    private boolean allowDoubleDown;
    private boolean allowSplit;
    private int deckCount;
    private int dealTicks;

    public BlackjackGame(CasinoDX plugin) {
        super(plugin, GameType.BLACKJACK);
        reload();
    }

    @Override
    public void reload() {
        double blackjack = payouts().getDouble("blackjack.multiplier", 2.5);
        double win = payouts().getDouble("win.multiplier", 2.0);
        this.blackjackMultiplier = blackjack > 1.0 ? blackjack : 2.5;
        this.winMultiplier = win > 1.0 ? win : 2.0;

        this.dealerHitsSoft17 = config().getBoolean("dealer-hits-soft-17", true);
        this.allowDoubleDown = config().getBoolean("allow-double-down", true);
        this.allowSplit = config().getBoolean("allow-split", true);
        this.deckCount = Math.max(1, Math.min(8, config().getInt("decks", 6)));
        this.dealTicks = Math.max(3, Math.min(20, config().getInt("deal-interval-ticks", 7)));
    }

    /** Gross multiplier for a natural blackjack, stake included (2.5 = the classic 3:2). */
    public double blackjackMultiplier() {
        return blackjackMultiplier;
    }

    /** Gross multiplier for an ordinary win, stake included (2.0 = even money). */
    public double winMultiplier() {
        return winMultiplier;
    }

    public boolean dealerHitsSoft17() {
        return dealerHitsSoft17;
    }

    public boolean allowDoubleDown() {
        return allowDoubleDown;
    }

    public boolean allowSplit() {
        return allowSplit;
    }

    public int deckCount() {
        return deckCount;
    }

    /** Ticks between card reveals in the deal and dealer animations. */
    public int dealTicks() {
        return dealTicks;
    }

    /**
     * Whether the dealer must draw again.
     *
     * <p>Stands on hard 17 or better always; whether a <em>soft</em> 17 is hit depends on
     * {@code dealer-hits-soft-17}.
     */
    public boolean dealerMustHit(Hand dealer) {
        int total = dealer.total();
        if (total < 17) {
            return true;
        }
        return total == 17 && dealer.isSoft() && dealerHitsSoft17;
    }

    @Override
    public void startSession(Player player, Money bet) {
        BlackjackSession session = beginSession(player, bet,
                wager -> new BlackjackSession(plugin, player, this, wager));
        if (session == null) {
            return;
        }
        session.openTable();
    }
}
