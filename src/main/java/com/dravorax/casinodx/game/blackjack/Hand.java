package com.dravorax.casinodx.game.blackjack;

import com.dravorax.casinodx.money.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One blackjack hand — the dealer's, or one of the player's after a split.
 *
 * <p>Each player hand owns its own wager, because a double or a split changes the money at risk on
 * that hand only.
 */
public final class Hand {

    private final List<Card> cards = new ArrayList<>();

    private Money wager;
    private boolean doubled;
    private boolean fromSplit;
    private boolean stood;

    public Hand(Money wager) {
        this.wager = wager == null ? Money.ZERO : wager;
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public int size() {
        return cards.size();
    }

    public Card card(int index) {
        return index < 0 || index >= cards.size() ? null : cards.get(index);
    }

    public void add(Card card) {
        if (card != null) {
            cards.add(card);
        }
    }

    public Money wager() {
        return wager;
    }

    public boolean doubled() {
        return doubled;
    }

    public boolean fromSplit() {
        return fromSplit;
    }

    public void markFromSplit() {
        this.fromSplit = true;
    }

    public boolean stood() {
        return stood;
    }

    public void stand() {
        this.stood = true;
    }

    /** Doubles the wager on this hand. The caller must have escrowed the extra amount already. */
    public void applyDouble(Money extra) {
        this.wager = wager.add(extra == null ? Money.ZERO : extra);
        this.doubled = true;
    }

    /**
     * Best total at or under 21.
     *
     * <p>Aces start at 11 and are demoted to 1 one at a time while the hand is bust, which yields
     * the highest legal total without special-casing.
     */
    public int total() {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            total += card.value();
            if (card.isAce()) {
                aces++;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** True when an ace is still counting as 11, i.e. the total can absorb a 10 without busting. */
    public boolean isSoft() {
        int hard = 0;
        int aces = 0;
        for (Card card : cards) {
            hard += card.value();
            if (card.isAce()) {
                aces++;
            }
        }
        while (hard > 21 && aces > 0) {
            hard -= 10;
            aces--;
        }
        return aces > 0;
    }

    public boolean isBust() {
        return total() > 21;
    }

    /**
     * Natural blackjack: 21 on the opening two cards. A 21 assembled after a split is a plain 21
     * and pays even money, matching standard casino rules.
     */
    public boolean isBlackjack() {
        return cards.size() == 2 && total() == 21 && !fromSplit;
    }

    /** Splittable when it is an untouched pair of equal-value cards. */
    public boolean isSplittable() {
        if (cards.size() != 2 || fromSplit || doubled) {
            return false;
        }
        return cards.get(0).value() == cards.get(1).value();
    }

    /** Doubling is only allowed on the opening two cards. */
    public boolean canDouble() {
        return cards.size() == 2 && !doubled && !stood && !isBust();
    }

    /** Hand is done: stood, doubled (one card only), bust, or already at 21. */
    public boolean isResolved() {
        return stood || isBust() || total() == 21 || (doubled && cards.size() >= 3);
    }

    /** Removes and returns the second card, used when splitting a pair. */
    public Card removeSecondCard() {
        if (cards.size() < 2) {
            return null;
        }
        return cards.remove(1);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(card);
        }
        return sb.toString();
    }
}
