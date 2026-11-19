package com.dravorax.casinodx.history;

import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;

import java.time.Instant;

/**
 * Immutable record of a single casino round for history GUIs and persistence.
 */
public record HistoryEntry(
        long id,
        GameType game,
        Money bet,
        Result result,
        double multiplier,
        Money gross,
        Money tax,
        Money net,
        long timestampMillis,
        String detail
) {
    public enum Result {
        WIN,
        LOSS,
        PUSH;

        public static Result fromString(String raw) {
            if (raw == null) {
                return LOSS;
            }
            return switch (raw.trim().toUpperCase()) {
                case "WIN" -> WIN;
                case "PUSH" -> PUSH;
                default -> LOSS;
            };
        }
    }

    public HistoryEntry {
        bet = bet == null ? Money.ZERO : bet;
        gross = gross == null ? Money.ZERO : gross;
        tax = tax == null ? Money.ZERO : tax;
        net = net == null ? Money.ZERO : net;
        detail = detail == null ? "" : detail;
    }

    public HistoryEntry(long id, GameType game, Money bet, Result result, double multiplier,
                        Money gross, Money tax, Money net, long timestampMillis) {
        this(id, game, bet, result, multiplier, gross, tax, net, timestampMillis, buildDetail(multiplier, tax));
    }

    public boolean win() {
        return result == Result.WIN;
    }

    public Money payout() {
        return gross;
    }

    public Instant timestamp() {
        return Instant.ofEpochMilli(timestampMillis);
    }

    public String detail() {
        return detail;
    }

    private static String buildDetail(double multiplier, Money tax) {
        StringBuilder sb = new StringBuilder();
        if (multiplier > 0) {
            sb.append("Multiplier: x").append(String.format("%.2f", multiplier));
        }
        if (tax != null && tax.isPositive()) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append("Tax: ").append(tax);
        }
        return sb.toString();
    }
}
