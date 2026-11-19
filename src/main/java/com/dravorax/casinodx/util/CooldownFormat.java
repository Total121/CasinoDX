package com.dravorax.casinodx.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses compact duration strings ({@code 1d}, {@code 24h}, {@code 30m}, {@code 90s}) and formats millis.
 */
public final class CooldownFormat {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);

    private CooldownFormat() {}

    /**
     * Parses a duration such as {@code 1d2h30m}, {@code 24h}, {@code 90s}, or plain millis.
     *
     * @throws IllegalArgumentException when the input cannot be parsed
     */
    public static long parseToMillis(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be blank");
        }
        String trimmed = input.trim().toLowerCase(Locale.ROOT);

        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Duration value is too large: " + trimmed, ex);
            }
        }

        Matcher matcher = TOKEN.matcher(trimmed);
        long total = 0L;
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() != lastEnd && !trimmed.substring(lastEnd, matcher.start()).isBlank()) {
                throw new IllegalArgumentException("Invalid duration token near index " + matcher.start());
            }
            long amount = Long.parseLong(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            total = safeAdd(total, amount * unitToMillis(unit));
            lastEnd = matcher.end();
        }
        if (lastEnd != trimmed.length()) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        if (total <= 0L) {
            throw new IllegalArgumentException("Duration must be positive: " + input);
        }
        return total;
    }

    public static String format(long millis) {
        if (millis <= 0L) {
            return "0s";
        }

        long seconds = millis / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder sb = new StringBuilder();
        appendUnit(sb, days, "d");
        appendUnit(sb, hours, "h");
        appendUnit(sb, minutes, "m");
        appendUnit(sb, seconds, "s");
        if (sb.length() == 0) {
            sb.append("0s");
        }
        return sb.toString().trim();
    }

    public static String formatHuman(long millis) {
        String compact = format(millis);
        return compact.replace("d", " day").replace("h", " hr").replace("m", " min").replace("s", " sec");
    }

    private static long unitToMillis(char unit) {
        return switch (unit) {
            case 'd' -> 86_400_000L;
            case 'h' -> 3_600_000L;
            case 'm' -> 60_000L;
            case 's' -> 1_000L;
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }

    private static long safeAdd(long left, long right) {
        long result = left + right;
        if (result < 0L) {
            throw new IllegalArgumentException("Duration overflow");
        }
        return result;
    }

    private static void appendUnit(StringBuilder sb, long value, String suffix) {
        if (value > 0L) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(value).append(suffix);
        }
    }
}
