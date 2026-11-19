package com.dravorax.casinodx.tax;

import com.dravorax.casinodx.money.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxCalculatorTest {

    @Test
    void profitModeFifteenPercentExample() {
        Money bet = Money.of(1000);
        Money gross = Money.of(2000);

        TaxResult result = TaxMath.calculate(
                true, false, 15.0, "profit",
                Money.ZERO, Money.ZERO, bet, gross);

        assertEquals(Money.of("2000.00"), result.grossPayout());
        assertEquals(Money.of("150.00"), result.taxAmount());
        assertEquals(Money.of("1850.00"), result.netPayout());
        assertEquals(Money.of("1000.00"), result.profit());
        assertTrue(result.taxed());
    }

    @Test
    void grossModeTaxesTotalPayout() {
        Money bet = Money.of(100);
        Money gross = Money.of(200);

        TaxResult result = TaxMath.calculate(
                true, false, 10.0, "gross",
                Money.ZERO, Money.ZERO, bet, gross);

        assertEquals(Money.of("20.00"), result.taxAmount());
        assertEquals(Money.of("180.00"), result.netPayout());
    }

    @Test
    void exemptPlayerPaysNoTax() {
        TaxResult result = TaxMath.calculate(
                true, true, 15.0, "profit",
                Money.ZERO, Money.ZERO, Money.of(100), Money.of(200));

        assertEquals(Money.ZERO, result.taxAmount());
        assertEquals(Money.of("200.00"), result.netPayout());
        assertFalse(result.taxed());
    }

    @Test
    void disabledTaxReturnsGross() {
        TaxResult result = TaxMath.calculate(
                false, false, 15.0, "profit",
                Money.ZERO, Money.ZERO, Money.of(100), Money.of(200));

        assertEquals(Money.ZERO, result.taxAmount());
        assertEquals(Money.of("200.00"), result.netPayout());
    }

    @Test
    void belowMinimumTaxableIsUntaxed() {
        TaxResult result = TaxMath.calculate(
                true, false, 15.0, "profit",
                Money.of(500), Money.ZERO, Money.of(100), Money.of(200));

        assertEquals(Money.ZERO, result.taxAmount());
        assertEquals(Money.of("200.00"), result.netPayout());
    }

    @Test
    void maximumTaxCapApplied() {
        TaxResult result = TaxMath.calculate(
                true, false, 50.0, "profit",
                Money.ZERO, Money.of(100), Money.of(100), Money.of(500));

        assertEquals(Money.of("100.00"), result.taxAmount());
        assertEquals(Money.of("400.00"), result.netPayout());
    }

    @Test
    void negativeProfitIsUntaxedInProfitMode() {
        TaxResult result = TaxMath.calculate(
                true, false, 15.0, "profit",
                Money.ZERO, Money.ZERO, Money.of(100), Money.of(50));

        assertEquals(Money.ZERO, result.taxAmount());
        assertEquals(Money.of("50.00"), result.netPayout());
        assertEquals(Money.of("-50.00"), result.profit());
    }

    @Test
    void zeroPercentageIsUntaxed() {
        TaxResult result = TaxMath.calculate(
                true, false, 0, "profit",
                Money.ZERO, Money.ZERO, Money.of(100), Money.of(300));

        assertEquals(Money.ZERO, result.taxAmount());
    }

    @Test
    void netNeverNegative() {
        TaxResult result = TaxMath.calculate(
                true, false, 100.0, "gross",
                Money.ZERO, Money.ZERO, Money.of(10), Money.of(10));

        assertEquals(Money.of("10.00"), result.taxAmount());
        assertEquals(Money.ZERO, result.netPayout());
    }

    @Test
    void nullBetAndGrossTreatedAsZero() {
        TaxResult result = TaxMath.calculate(
                true, false, 15.0, "profit",
                Money.ZERO, Money.ZERO, null, null);

        assertEquals(Money.ZERO, result.grossPayout());
        assertEquals(Money.ZERO, result.taxAmount());
    }
}
