package com.dravorax.casinodx.game.roulette;

import org.bukkit.Material;

import java.util.Locale;

/**
 * A single chip on the roulette table: the kind of bet plus, for straight bets, which pocket.
 *
 * <p>A round holds a whole list of these, one per chip the player has placed, so the value identity
 * of a record matters: {@link RouletteSession#toggleBet(RouletteBet)} looks a bet up by equality to
 * decide whether a click adds a chip or takes one back. Use {@link #of(Kind, int)} to build one —
 * it canonicalises {@link #number()} so that two clicks on the same cell always produce two equal
 * bets.
 *
 * <p>Multipliers are expressed as <em>gross</em> returns (stake included), because the chip is
 * escrowed when the wheel is spun. A straight-up bet quoted as "35:1" therefore appears here as
 * {@code 36.0}: the player gets 35 units of profit plus their 1 unit back.
 *
 * <p>Each classic bet pays as if the wheel had 36 pockets while it actually has 37, and that single
 * green pocket <em>is</em> the house edge: {@code 1 - 36/37 ≈ 2.70%} on every bet below.
 */
public record RouletteBet(Kind kind, int number) {

    public enum Kind {
        /** One specific pocket, 35:1. */
        STRAIGHT("straight", 36.0, "Straight Up"),
        RED("red", 2.0, "Red"),
        BLACK("black", 2.0, "Black"),
        /** Zero only — the green pocket, paid at straight-up odds. */
        GREEN("green", 36.0, "Green Zero"),
        ODD("odd", 2.0, "Odd"),
        EVEN("even", 2.0, "Even"),
        LOW("low", 2.0, "Low 1-18"),
        HIGH("high", 2.0, "High 19-36"),
        /** Twelve consecutive numbers, 2:1. */
        DOZEN("dozen", 3.0, "Dozen"),
        /** One of the three table columns, 2:1. */
        COLUMN("column", 3.0, "Column");

        private final String configKey;
        private final double defaultMultiplier;
        private final String displayName;

        Kind(String configKey, double defaultMultiplier, String displayName) {
            this.configKey = configKey;
            this.defaultMultiplier = defaultMultiplier;
            this.displayName = displayName;
        }

        public String configKey() {
            return configKey;
        }

        public double defaultMultiplier() {
            return defaultMultiplier;
        }

        public String displayName() {
            return displayName;
        }

        /** Whether {@link RouletteBet#number()} carries meaning for this kind. */
        public boolean usesNumber() {
            return this == STRAIGHT || this == DOZEN || this == COLUMN;
        }

        /** Whether this is an outside bet, i.e. everything except a single pocket. */
        public boolean isOutside() {
            return this != STRAIGHT && this != GREEN;
        }

        public static Kind fromString(String raw) {
            if (raw == null) {
                return null;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (Kind kind : values()) {
                if (kind.configKey.equals(normalized)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /**
     * Canonical constructor for table clicks.
     *
     * <p>Numbers are clamped into range and cleared for the kinds that do not use them, and a
     * straight-up bet on zero collapses into {@link Kind#GREEN}. Both spellings of "a chip on the
     * zero cell" therefore compare equal, so the numbers grid and the outside-bet row can never end
     * up holding two chips that mean the same thing.
     */
    public static RouletteBet of(Kind kind, int number) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case STRAIGHT -> RouletteWheel.isValidPocket(number)
                    ? (number == 0 ? new RouletteBet(Kind.GREEN, 0) : new RouletteBet(Kind.STRAIGHT, number))
                    : null;
            case GREEN -> new RouletteBet(Kind.GREEN, 0);
            case DOZEN, COLUMN -> new RouletteBet(kind, Math.max(1, Math.min(3, number)));
            default -> new RouletteBet(kind, -1);
        };
    }

    public static RouletteBet straight(int pocket) {
        return of(Kind.STRAIGHT, pocket);
    }

    public static RouletteBet dozen(int dozen) {
        return of(Kind.DOZEN, dozen);
    }

    public static RouletteBet column(int column) {
        return of(Kind.COLUMN, column);
    }

    public static RouletteBet simple(Kind kind) {
        return of(kind, kind == Kind.GREEN ? 0 : -1);
    }

    /** Whether this wager wins against a spun pocket. */
    public boolean wins(int spun) {
        if (!RouletteWheel.isValidPocket(spun)) {
            return false;
        }
        return switch (kind) {
            case STRAIGHT -> spun == number;
            case GREEN -> spun == 0;
            // Zero loses every even-money and outside bet — that is the whole point of the pocket.
            case RED -> RouletteWheel.isRed(spun);
            case BLACK -> RouletteWheel.isBlack(spun);
            case ODD -> spun != 0 && spun % 2 == 1;
            case EVEN -> spun != 0 && spun % 2 == 0;
            case LOW -> spun >= 1 && spun <= 18;
            case HIGH -> spun >= 19 && spun <= 36;
            case DOZEN -> RouletteWheel.dozenOf(spun) == number;
            case COLUMN -> RouletteWheel.columnOf(spun) == number;
        };
    }

    /** Human-readable label for GUIs, chat and history. */
    public String label() {
        return switch (kind) {
            case STRAIGHT -> "Number " + number;
            case GREEN -> "Zero (green)";
            case DOZEN -> switch (number) {
                case 1 -> "1st Dozen (1-12)";
                case 2 -> "2nd Dozen (13-24)";
                default -> "3rd Dozen (25-36)";
            };
            case COLUMN -> switch (number) {
                case 1 -> "1st Column";
                case 2 -> "2nd Column";
                default -> "3rd Column";
            };
            default -> kind.displayName();
        };
    }

    /** Compact label for the "your chips" list, where a dozen lines have to fit in lore. */
    public String shortLabel() {
        return switch (kind) {
            case STRAIGHT -> "#" + number;
            case GREEN -> "0";
            case DOZEN -> "Dozen " + number;
            case COLUMN -> "Column " + number;
            default -> kind.displayName();
        };
    }

    /** Concrete block that matches how this cell looks on the felt. */
    public Material material() {
        return switch (kind) {
            case STRAIGHT -> RouletteWheel.materialOf(number);
            case GREEN -> Material.LIME_CONCRETE;
            case RED -> Material.RED_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
            case ODD -> Material.WHITE_CONCRETE;
            case EVEN -> Material.LIGHT_GRAY_CONCRETE;
            case LOW -> Material.CYAN_CONCRETE;
            case HIGH -> Material.BLUE_CONCRETE;
            case DOZEN -> Material.ORANGE_CONCRETE;
            case COLUMN -> Material.PURPLE_CONCRETE;
        };
    }

    /** Colour code for lore text matching the cell colour. */
    public String colorCode() {
        return switch (kind) {
            case STRAIGHT -> RouletteWheel.colorCode(number);
            case GREEN -> "&a";
            case RED -> "&c";
            case BLACK -> "&8";
            default -> "&f";
        };
    }

    /** Count of winning pockets, used to display the true odds in the GUI. */
    public int winningPockets() {
        return switch (kind) {
            case STRAIGHT, GREEN -> 1;
            case RED, BLACK, ODD, EVEN, LOW, HIGH -> 18;
            case DOZEN, COLUMN -> 12;
        };
    }

    /** True win probability on a 37-pocket wheel. */
    public double winChance() {
        return winningPockets() / (double) RouletteWheel.POCKETS;
    }

    /** {@code kind:number}, the form {@link RouletteGame} stores in the last-play context. */
    public String encode() {
        return kind.configKey() + ":" + number;
    }
}
