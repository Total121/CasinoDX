package com.dravorax.casinodx.tax;

import com.dravorax.casinodx.money.Money;

import java.util.Locale;

/**
 * Pure tax calculation used by {@link TaxService}. Separated so the formula can be unit-tested
 * without Bukkit {@code Player} or configuration objects.
 */
public final class TaxMath {

    private TaxMath() {
    }

    /**
     * Computes tax for a single round.
     *
     * @param enabled       whether tax is globally enabled
     * @param exempt        whether the player is exempt (permission or equivalent)
     * @param percentage    tax rate in percent (e.g. {@code 15.0} for 15%)
     * @param applyTo       {@code "profit"} or {@code "gross"}
     * @param minTaxable    minimum taxable base before any tax is charged
     * @param maxTax        cap on tax amount; zero or negative means no cap
     * @param bet           original wager
     * @param gross         gross payout before tax
     */
    public static TaxResult calculate(
            boolean enabled,
            boolean exempt,
            double percentage,
            String applyTo,
            Money minTaxable,
            Money maxTax,
            Money bet,
            Money gross) {
        Money safeBet = bet == null ? Money.ZERO : bet.max(Money.ZERO);
        Money safeGross = gross == null ? Money.ZERO : gross.max(Money.ZERO);
        Money profit = safeGross.subtract(safeBet);

        if (!enabled || exempt) {
            return new TaxResult(safeGross, Money.ZERO, safeGross, safeBet, profit);
        }

        if (percentage <= 0) {
            return new TaxResult(safeGross, Money.ZERO, safeGross, safeBet, profit);
        }

        ApplyMode mode = ApplyMode.fromString(applyTo);
        Money taxableBase = switch (mode) {
            case GROSS -> safeGross;
            case PROFIT -> profit.isPositive() ? profit : Money.ZERO;
        };

        Money minimum = minTaxable == null ? Money.ZERO : minTaxable.max(Money.ZERO);
        if (!taxableBase.isPositive() || taxableBase.compareTo(minimum) < 0) {
            return new TaxResult(safeGross, Money.ZERO, safeGross, safeBet, profit);
        }

        Money taxAmount = taxableBase.multiply(percentage / 100.0);
        Money cap = maxTax == null ? Money.ZERO : maxTax;
        if (cap.isPositive() && taxAmount.compareTo(cap) > 0) {
            taxAmount = cap;
        }

        Money net = safeGross.subtract(taxAmount);
        if (net.isNegative()) {
            net = Money.ZERO;
        }

        return new TaxResult(safeGross, taxAmount, net, safeBet, profit);
    }

    enum ApplyMode {
        PROFIT,
        GROSS;

        static ApplyMode fromString(String raw) {
            if (raw == null) {
                return PROFIT;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "gross" -> GROSS;
                default -> PROFIT;
            };
        }
    }
}
