package com.dravorax.casinodx.game.blackjack;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameSession;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.game.PayoutService;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * One blackjack round.
 *
 * <p>The shoe is shuffled in the constructor and both opening hands are dealt immediately, so the
 * cards exist before any animation starts. When the player finishes acting, the dealer's entire
 * draw sequence is computed at once and only then animated — the dealer never "decides" a card
 * while the animation is running.
 *
 * <p>Doubles and splits withdraw extra money mid-round; each is registered with
 * {@link GameSession#addEscrow(Money)} so an interruption refunds everything at risk, not just the
 * opening bet.
 */
public final class BlackjackSession extends GameSession {

    /** How far the deal/dealer animation has progressed, so the GUI knows what to show. */
    public enum Phase {
        DEALING,
        PLAYER_TURN,
        DEALER_TURN,
        FINISHED
    }

    private final BlackjackGame game;
    private final Shoe shoe;
    private final Hand dealer;
    private final List<Hand> playerHands = new ArrayList<>();

    private Phase phase = Phase.DEALING;
    private int activeHand;
    private boolean holeCardRevealed;

    /** Cards visible to the player so far, driven by the animations. */
    private int revealedPlayerCards;
    private int revealedDealerCards;

    private BlackjackGui gui;
    private String outcomeSummary = "";

    BlackjackSession(CasinoDX plugin, Player player, BlackjackGame game, Money bet) {
        super(plugin, player, GameType.BLACKJACK, bet);
        this.game = game;
        this.shoe = new Shoe(game.deckCount());
        this.dealer = new Hand(Money.ZERO);

        Hand hand = new Hand(bet);
        playerHands.add(hand);

        // Standard deal order: player, dealer, player, dealer (hole card).
        hand.add(shoe.draw());
        dealer.add(shoe.draw());
        hand.add(shoe.draw());
        dealer.add(shoe.draw());
    }

    public BlackjackGame game() {
        return game;
    }

    public Hand dealerHand() {
        return dealer;
    }

    public List<Hand> playerHands() {
        return List.copyOf(playerHands);
    }

    public Hand activeHand() {
        return activeHand < playerHands.size() ? playerHands.get(activeHand) : null;
    }

    public int activeHandIndex() {
        return activeHand;
    }

    public Phase phase() {
        return phase;
    }

    public boolean holeCardRevealed() {
        return holeCardRevealed;
    }

    public int revealedDealerCards() {
        return revealedDealerCards;
    }

    public int revealedPlayerCards() {
        return revealedPlayerCards;
    }

    public String outcomeSummary() {
        return outcomeSummary;
    }

    public boolean isSplit() {
        return playerHands.size() > 1;
    }

    /** True while the player may hit, stand, double or split. */
    public boolean playerCanAct() {
        return phase == Phase.PLAYER_TURN && acceptsInput() && activeHand() != null;
    }

    public void openTable() {
        BlackjackGui view = new BlackjackGui(plugin, player(), this);
        this.gui = view;
        plugin.guis().open(view);

        state(State.ANIMATING);
        Player player = player();
        if (player != null) {
            plugin.animations().register(new BlackjackDealAnimation(player, this, view, game.dealTicks()));
        }
    }

    /** Called by the deal animation each time one more card should become visible. */
    void revealNextCard(int playerCards, int dealerCards) {
        this.revealedPlayerCards = playerCards;
        this.revealedDealerCards = dealerCards;
        if (gui != null) {
            gui.refresh();
        }
    }

    /** Called when the opening deal animation completes. */
    void onDealFinished() {
        this.revealedPlayerCards = playerHands.get(0).size();
        this.revealedDealerCards = 1;

        // A natural on either side ends the round without the player acting.
        if (playerHands.get(0).isBlackjack() || dealer.isBlackjack()) {
            beginDealerTurn();
            return;
        }

        phase = Phase.PLAYER_TURN;
        state(State.ACTIVE);
        if (gui != null) {
            gui.refresh();
        }
    }

    public void hit() {
        Hand hand = activeHand();
        if (!playerCanAct() || hand == null) {
            return;
        }
        hand.add(shoe.draw());
        revealedPlayerCards = hand.size();

        Player player = player();
        if (player != null) {
            plugin.sounds().play(player, SoundService.CARD_DEAL);
        }

        if (hand.isResolved()) {
            advanceHand();
        } else if (gui != null) {
            gui.refresh();
        }
    }

    public void stand() {
        Hand hand = activeHand();
        if (!playerCanAct() || hand == null) {
            return;
        }
        hand.stand();
        advanceHand();
    }

    /**
     * Doubles the wager on the active hand and takes exactly one more card.
     *
     * <p>The extra stake is withdrawn before the card is drawn, and registered as escrow so an
     * interruption returns it.
     */
    public void doubleDown() {
        Hand hand = activeHand();
        if (!playerCanAct() || hand == null || !game.allowDoubleDown() || !hand.canDouble()) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }

        Money extra = hand.wager();
        if (!withdrawExtra(player, extra, "double down")) {
            return;
        }

        hand.applyDouble(extra);
        hand.add(shoe.draw());
        revealedPlayerCards = hand.size();
        plugin.sounds().play(player, SoundService.CARD_DEAL);
        advanceHand();
    }

    /** Splits a pair into two hands, each taking one fresh card. One split per round. */
    public void split() {
        Hand hand = activeHand();
        if (!playerCanAct() || hand == null || !game.allowSplit()
                || isSplit() || !hand.isSplittable()) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }

        Money extra = hand.wager();
        if (!withdrawExtra(player, extra, "split")) {
            return;
        }

        Card moved = hand.removeSecondCard();
        Hand second = new Hand(extra);
        second.markFromSplit();
        second.add(moved);

        hand.markFromSplit();
        hand.add(shoe.draw());
        second.add(shoe.draw());
        playerHands.add(second);

        revealedPlayerCards = hand.size();
        plugin.sounds().play(player, SoundService.CARD_DEAL);

        if (hand.isResolved()) {
            advanceHand();
        } else if (gui != null) {
            gui.refresh();
        }
    }

    private boolean withdrawExtra(Player player, Money extra, String action) {
        if (!extra.isPositive()) {
            return true;
        }
        if (!plugin.antiExploit().tryBeginTransaction(player.getUniqueId())) {
            return false;
        }
        try {
            if (!plugin.economy().withdraw(player, extra)) {
                plugin.messages().send(player, "insufficient-funds");
                return false;
            }
            addEscrow(extra);
            plugin.messages().send(player, "bet-placed", "amount", plugin.economy().format(extra));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Blackjack " + action + " withdrawal failed for "
                    + player.getName() + ": " + t);
            return false;
        } finally {
            plugin.antiExploit().endTransaction(player.getUniqueId());
        }
    }

    /** Moves to the next unresolved hand, or hands over to the dealer. */
    private void advanceHand() {
        while (activeHand < playerHands.size() && playerHands.get(activeHand).isResolved()) {
            activeHand++;
        }
        if (activeHand < playerHands.size()) {
            revealedPlayerCards = playerHands.get(activeHand).size();
            state(State.ACTIVE);
            if (gui != null) {
                gui.refresh();
            }
            return;
        }
        beginDealerTurn();
    }

    /**
     * Computes the dealer's full hand immediately, then animates revealing it.
     *
     * <p>Deciding all the dealer's cards up front is what keeps the animation honest: by the time
     * the first card flips, the dealer's final total is already fixed.
     */
    private void beginDealerTurn() {
        phase = Phase.DEALER_TURN;
        state(State.ANIMATING);
        holeCardRevealed = true;

        boolean allBust = playerHands.stream().allMatch(Hand::isBust);
        boolean playerNatural = playerHands.size() == 1 && playerHands.get(0).isBlackjack();

        // The dealer only draws when a live player hand could still be beaten.
        if (!allBust && !playerNatural) {
            while (game.dealerMustHit(dealer)) {
                dealer.add(shoe.draw());
            }
        }

        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Left before the dealer played");
            return;
        }
        if (gui != null) {
            gui.refresh();
        }
        plugin.animations().register(new BlackjackDealerAnimation(player, this, gui, game.dealTicks()));
    }

    /** Called by the dealer animation as each dealer card becomes visible. */
    void revealDealerCard(int count) {
        this.revealedDealerCards = count;
        if (gui != null) {
            gui.refresh();
        }
    }

    /** Called when the dealer animation completes. */
    void onDealerFinished() {
        this.revealedDealerCards = dealer.size();
        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Left before the payout");
            return;
        }
        resolve(player);
        phase = Phase.FINISHED;
        if (gui != null) {
            gui.refresh();
        }
        plugin.sessions().end(playerId());
    }

    /**
     * Totals every hand's gross return and settles the round in a single transaction.
     *
     * <p>One settlement for the whole round rather than one per hand keeps
     * {@link GameSession#claimSettlement()} meaningful: a split can win one hand and lose the
     * other, and {@link PayoutService#settleReturn} handles the partial return that produces.
     */
    private void resolve(Player player) {
        RoundOutcome outcome = computeOutcome();
        this.outcomeSummary = outcome.summary();
        PayoutService.settleReturn(plugin, player, this, outcome.totalWager(), outcome.totalReturn(),
                outcome.summary());
        plugin.lastPlay().record(playerId(), type(), bet(), null,
                outcome.totalReturn().greaterThan(outcome.totalWager()),
                outcome.summary(), outcome.totalReturn());
    }

    /**
     * Pure hand comparison against the already-drawn dealer cards. Safe to call offline because it
     * never touches the economy — callers pass the totals into {@link PayoutService}.
     */
    private RoundOutcome computeOutcome() {
        Money totalWager = Money.ZERO;
        Money totalReturn = Money.ZERO;
        StringBuilder summary = new StringBuilder();

        int dealerTotal = dealer.total();
        boolean dealerBust = dealer.isBust();
        boolean dealerNatural = dealer.isBlackjack();

        for (int i = 0; i < playerHands.size(); i++) {
            Hand hand = playerHands.get(i);
            totalWager = totalWager.add(hand.wager());

            Money handReturn;
            String label;
            if (hand.isBust()) {
                handReturn = Money.ZERO;
                label = "bust (" + hand.total() + ")";
            } else if (hand.isBlackjack() && !dealerNatural) {
                handReturn = hand.wager().multiply(game.blackjackMultiplier());
                label = "blackjack";
            } else if (hand.isBlackjack() && dealerNatural) {
                handReturn = hand.wager();
                label = "push (both blackjack)";
            } else if (dealerNatural) {
                handReturn = Money.ZERO;
                label = "dealer blackjack";
            } else if (dealerBust) {
                handReturn = hand.wager().multiply(game.winMultiplier());
                label = "win (dealer bust)";
            } else if (hand.total() > dealerTotal) {
                handReturn = hand.wager().multiply(game.winMultiplier());
                label = "win " + hand.total() + " vs " + dealerTotal;
            } else if (hand.total() == dealerTotal) {
                handReturn = hand.wager();
                label = "push on " + hand.total();
            } else {
                handReturn = Money.ZERO;
                label = "lose " + hand.total() + " vs " + dealerTotal;
            }

            totalReturn = totalReturn.add(handReturn);
            if (!summary.isEmpty()) {
                summary.append(" | ");
            }
            if (playerHands.size() > 1) {
                summary.append("Hand ").append(i + 1).append(": ");
            }
            summary.append(label);
        }

        return new RoundOutcome(totalWager, totalReturn, summary.toString());
    }

    private record RoundOutcome(Money totalWager, Money totalReturn, String summary) {}

    /**
     * Once the dealer's cards are drawn the round's result is fixed, so an interruption at that
     * point settles it. Anything earlier — still dealing, or the player's turn — is genuinely
     * undecided and falls through to a full refund of every wager at risk.
     */
    @Override
    protected boolean settleDecidedOutcome(String reason) {
        if (phase != Phase.DEALER_TURN) {
            return false;
        }
        RoundOutcome outcome = computeOutcome();
        this.outcomeSummary = outcome.summary();
        phase = Phase.FINISHED;

        Player player = player();
        if (player != null) {
            PayoutService.settleReturn(plugin, player, this, outcome.totalWager(), outcome.totalReturn(),
                    outcome.summary());
            return true;
        }
        return PayoutService.settleReturnOffline(plugin, playerId(), this,
                outcome.totalWager(), outcome.totalReturn());
    }

    @Override
    protected void onForceEnd(String reason) {
        phase = Phase.FINISHED;
        gui = null;
    }
}
