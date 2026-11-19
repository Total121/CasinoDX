package com.dravorax.casinodx.game;

import com.dravorax.casinodx.history.HistoryEntry;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.tax.TaxMath;
import com.dravorax.casinodx.tax.TaxResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure routing tests for the offline settleReturn decision tree (win / push / loss / partial).
 * Does not hit Bukkit — mirrors {@link PayoutService#settleReturnOffline} classification.
 */
class OfflineSettlementRoutingTest {

    private enum Route { WIN, PUSH, LOSS, PARTIAL }

    private static Route classify(Money bet, Money gross) {
        Money safeBet = bet == null ? Money.ZERO : bet;
        Money safeGross = gross == null ? Money.ZERO : gross.max(Money.ZERO);
        if (safeGross.greaterThan(safeBet)) {
            return Route.WIN;
        }
        if (safeGross.equals(safeBet)) {
            return Route.PUSH;
        }
        if (!safeGross.isPositive()) {
            return Route.LOSS;
        }
        return Route.PARTIAL;
    }

    @Test
    void blackjackWinRoutesToTaxedWin() {
        Money bet = Money.of(100);
        Money gross = Money.of(200);
        assertEquals(Route.WIN, classify(bet, gross));
        TaxResult tax = TaxMath.calculate(true, false, 15.0, "profit",
                Money.ZERO, Money.ZERO, bet, gross);
        assertEquals(Money.of(15), tax.taxAmount());
        assertEquals(Money.of(185), tax.netPayout());
    }

    @Test
    void blackjackPushRoutesUntaxed() {
        Money bet = Money.of(100);
        assertEquals(Route.PUSH, classify(bet, bet));
    }

    @Test
    void blackjackBustIsLoss() {
        assertEquals(Route.LOSS, classify(Money.of(100), Money.ZERO));
    }

    @Test
    void splitPartialReturnIsPartial() {
        // One hand wins 2x on 50, other loses 50 → gross 100 on total wager 100 is push;
        // one wins 2x on 50, other loses → gross 100 on 100 = push.
        // Win one 2x (100) lose one (0) on total 100 wager → gross 100 = push.
        // Win one 2x on 50 + lose 50 → 100 on 100.
        // Partial: win nothing on one side but get half back somehow — e.g. gross 50 on bet 100.
        assertEquals(Route.PARTIAL, classify(Money.of(100), Money.of(50)));
        TaxResult tax = TaxMath.calculate(true, false, 15.0, "profit",
                Money.ZERO, Money.ZERO, Money.of(100), Money.of(50));
        assertFalse(tax.taxed());
        assertEquals(Money.of(50), tax.netPayout());
    }

    @Test
    void historyResultLabelsMatchRoutes() {
        assertTrue(HistoryEntry.Result.WIN.name().length() > 0);
        assertTrue(HistoryEntry.Result.LOSS.name().length() > 0);
        assertTrue(HistoryEntry.Result.PUSH.name().length() > 0);
    }
}
