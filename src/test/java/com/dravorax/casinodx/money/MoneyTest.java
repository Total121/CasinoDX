package com.dravorax.casinodx.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void ofCreatesScaledAmount() {
        Money money = Money.of(10.5);
        assertEquals("10.50", money.format());
    }

    @Test
    void ofFromString() {
        assertEquals(Money.of(12.34), Money.of("12.34"));
        assertEquals(Money.ZERO, Money.of("  "));
    }

    @Test
    void ofFromBigDecimal() {
        assertEquals(Money.of(5), Money.of(new BigDecimal("5.005")));
        assertEquals(Money.ZERO, Money.of((BigDecimal) null));
    }

    @Test
    void ofRejectsNaN() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Money.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void ofNonNegativeRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> Money.ofNonNegative(-1));
        assertThrows(IllegalArgumentException.class, () -> Money.ofNonNegative("-0.01"));
        assertEquals(Money.of(0), Money.ofNonNegative(0));
    }

    @Test
    void addAndSubtract() {
        Money a = Money.of("100.00");
        Money b = Money.of("25.50");
        assertEquals(Money.of("125.50"), a.add(b));
        assertEquals(Money.of("74.50"), a.subtract(b));
    }

    @Test
    void multiply() {
        Money bet = Money.of("1000.00");
        assertEquals(Money.of("2000.00"), bet.multiply(2.0));
        assertEquals(Money.of("1500.00"), bet.multiply(new BigDecimal("1.5")));
        assertThrows(IllegalArgumentException.class, () -> bet.multiply(Double.NaN));
    }

    @Test
    void divide() {
        Money payout = Money.of("150.00");
        assertEquals(Money.of("100.00"), payout.divide(1.5));
        assertThrows(IllegalArgumentException.class, () -> payout.divide(0));
    }

    @Test
    void equalsIgnoresTrailingScale() {
        assertEquals(Money.of("10.00"), Money.of("10.0"));
        assertEquals(Money.of("10.00"), Money.of(new BigDecimal("10")));
    }

    @Test
    void compareAndSign() {
        Money low = Money.of(1);
        Money high = Money.of(2);
        assertTrue(low.lessThan(high));
        assertTrue(high.greaterThan(low));
        assertTrue(Money.ZERO.isZero());
        assertTrue(Money.of(0.01).isPositive());
        assertTrue(Money.of(-1).isNegative());
    }

    @Test
    void formatAlwaysTwoDecimals() {
        assertEquals("0.00", Money.ZERO.format());
        assertEquals("1000.00", Money.of(1000).format());
    }

    @Test
    void negateAndAbs() {
        Money negative = Money.of("-5.25");
        assertEquals(Money.of("5.25"), negative.negate());
        assertEquals(Money.of("5.25"), negative.abs());
    }

    @Test
    void minMax() {
        Money a = Money.of(10);
        Money b = Money.of(20);
        assertEquals(a, a.min(b));
        assertEquals(b, a.max(b));
    }

    @Test
    void hashCodeConsistentWithEquals() {
        Money a = Money.of("10.00");
        Money b = Money.of("10");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, Money.of("10.01"));
    }
}
