package com.dravorax.casinodx.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable monetary amount with fixed scale ({@value #SCALE}) and banker's rounding.
 */
public final class Money implements Comparable<Money> {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public static final Money ZERO = zero();

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = normalize(amount);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public static Money of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Invalid money amount: " + value);
        }
        return new Money(BigDecimal.valueOf(value));
    }

    public static Money of(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        try {
            return new Money(new BigDecimal(value.trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid money amount: " + value, ex);
        }
    }

    public static Money of(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (!isFinite(value)) {
            throw new IllegalArgumentException("Invalid money amount: " + value);
        }
        return new Money(value);
    }

    public static Money ofNonNegative(double value) {
        Money money = of(value);
        if (money.isNegative()) {
            throw new IllegalArgumentException("Money cannot be negative: " + value);
        }
        return money;
    }

    public static Money ofNonNegative(String value) {
        Money money = of(value);
        if (money.isNegative()) {
            throw new IllegalArgumentException("Money cannot be negative: " + value);
        }
        return money;
    }

    public static Money ofNonNegative(BigDecimal value) {
        Money money = of(value);
        if (money.isNegative()) {
            throw new IllegalArgumentException("Money cannot be negative: " + value);
        }
        return money;
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(double multiplier) {
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("Invalid multiplier: " + multiplier);
        }
        return multiply(BigDecimal.valueOf(multiplier));
    }

    public Money multiply(BigDecimal multiplier) {
        Objects.requireNonNull(multiplier, "multiplier");
        if (!isFinite(multiplier)) {
            throw new IllegalArgumentException("Invalid multiplier: " + multiplier);
        }
        return new Money(amount.multiply(multiplier));
    }

    /**
     * Divides by a positive divisor. Used for fractional payouts such as blackjack 3:2.
     */
    public Money divide(double divisor) {
        if (Double.isNaN(divisor) || Double.isInfinite(divisor) || divisor == 0) {
            throw new IllegalArgumentException("Invalid divisor: " + divisor);
        }
        return new Money(amount.divide(BigDecimal.valueOf(divisor), SCALE + 4, ROUNDING));
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    public Money abs() {
        return new Money(amount.abs());
    }

    public boolean lessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean lessThanOrEqual(Money other) {
        return compareTo(other) <= 0;
    }

    public boolean greaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean greaterThanOrEqual(Money other) {
        return compareTo(other) >= 0;
    }

    public Money min(Money other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) <= 0 ? this : other;
    }

    public Money max(Money other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /**
     * Returns a plain-text representation suitable for GUIs (always two decimal places).
     */
    public String format() {
        return amount.toPlainString();
    }

    /**
     * Converts to {@code double} for legacy economy APIs. Values remain within normal finite range.
     */
    public double toDouble() {
        double value = amount.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalStateException("Money value is not representable as double: " + amount);
        }
        return value;
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    @Override
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money money)) {
            return false;
        }
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return format();
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException("Invalid money amount: " + value);
        }
        return value.setScale(SCALE, ROUNDING);
    }

    private static boolean isFinite(BigDecimal value) {
        try {
            value.doubleValue();
        } catch (NumberFormatException ex) {
            return false;
        }
        return value.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) <= 0
                && value.compareTo(BigDecimal.valueOf(-Double.MAX_VALUE)) >= 0;
    }
}
