package com.dravorax.casinodx.game.blackjack;

/**
 * A single playing card.
 *
 * <p>Aces carry a base value of 11; {@link Hand} demotes them to 1 as needed, which is what makes
 * "soft" totals work.
 */
public record Card(Rank rank, Suit suit) {

    public enum Suit {
        SPADES("♠", "Spades", "&8", false),
        CLUBS("♣", "Clubs", "&8", false),
        HEARTS("♥", "Hearts", "&c", true),
        DIAMONDS("♦", "Diamonds", "&c", true);

        private final String symbol;
        private final String displayName;
        private final String colorCode;
        private final boolean red;

        Suit(String symbol, String displayName, String colorCode, boolean red) {
            this.symbol = symbol;
            this.displayName = displayName;
            this.colorCode = colorCode;
            this.red = red;
        }

        public String symbol() {
            return symbol;
        }

        public String displayName() {
            return displayName;
        }

        /** Legacy colour code: red for hearts and diamonds, dark grey for spades and clubs. */
        public String colorCode() {
            return colorCode;
        }

        public boolean isRed() {
            return red;
        }
    }

    public enum Rank {
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 8),
        NINE("9", 9),
        TEN("10", 10),
        JACK("J", 10),
        QUEEN("Q", 10),
        KING("K", 10),
        ACE("A", 11);

        private final String label;
        private final int value;

        Rank(String label, int value) {
            this.label = label;
            this.value = value;
        }

        public String label() {
            return label;
        }

        public int value() {
            return value;
        }

        public boolean isAce() {
            return this == ACE;
        }
    }

    public int value() {
        return rank.value();
    }

    public boolean isAce() {
        return rank.isAce();
    }

    /** Short label such as {@code &cA♥}, already carrying the suit colour. */
    public String shortLabel() {
        return suit.colorCode() + rank.label() + suit.symbol();
    }

    @Override
    public String toString() {
        return rank.label() + suit.symbol();
    }
}
