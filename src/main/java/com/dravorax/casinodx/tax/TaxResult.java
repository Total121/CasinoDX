package com.dravorax.casinodx.tax;

import com.dravorax.casinodx.money.Money;

/**
 * Outcome of a tax calculation for a single casino round.
 */
public record TaxResult(
        Money grossPayout,
        Money taxAmount,
        Money netPayout,
        Money originalBet,
        Money profit
) {
    public TaxResult {
        grossPayout = grossPayout == null ? Money.ZERO : grossPayout;
        taxAmount = taxAmount == null ? Money.ZERO : taxAmount;
        netPayout = netPayout == null ? Money.ZERO : netPayout;
        originalBet = originalBet == null ? Money.ZERO : originalBet;
        profit = profit == null ? Money.ZERO : profit;
    }

    public boolean taxed() {
        return taxAmount.isPositive();
    }
}
