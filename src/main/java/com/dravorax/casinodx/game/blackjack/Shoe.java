package com.dravorax.casinodx.game.blackjack;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A shuffled multi-deck shoe.
 *
 * <p>The shoe is built and shuffled once, when the round starts. Every card the round will ever
 * deal is therefore already fixed in order before the player sees anything — the deal and dealer
 * animations only walk down a list that already exists. If the shoe runs low mid-round it is
 * rebuilt and reshuffled, which cannot favour either side because the reshuffle is independent of
 * the hands in play.
 */
final class Shoe {

    private static final SecureRandom SHUFFLE_RANDOM = new SecureRandom();

    private final int deckCount;
    private final Deque<Card> cards = new ArrayDeque<>();

    Shoe(int deckCount) {
        this.deckCount = Math.max(1, Math.min(8, deckCount));
        refill();
    }

    private void refill() {
        List<Card> fresh = new ArrayList<>(deckCount * 52);
        for (int deck = 0; deck < deckCount; deck++) {
            for (Card.Suit suit : Card.Suit.values()) {
                for (Card.Rank rank : Card.Rank.values()) {
                    fresh.add(new Card(rank, suit));
                }
            }
        }
        Collections.shuffle(fresh, SHUFFLE_RANDOM);
        cards.clear();
        cards.addAll(fresh);
    }

    /** Draws the next card, reshuffling a fresh shoe if this one is exhausted. */
    Card draw() {
        if (cards.isEmpty()) {
            refill();
        }
        return cards.poll();
    }

    int remaining() {
        return cards.size();
    }
}
