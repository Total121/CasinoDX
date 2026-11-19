package com.dravorax.casinodx.util;

import com.dravorax.casinodx.money.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legacy ampersand colors, hex codes, simple MiniMessage-like tags, and casino formatting helpers.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT = Pattern.compile(
            "<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SIMPLE_TAG = Pattern.compile(
            "</?(gold|red|green|aqua|yellow|gray|dark_gray|white|black|bold|italic|reset)>",
            Pattern.CASE_INSENSITIVE);
    private static final char[] SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".toCharArray();

    private Text() {}

    /**
     * Translates {@code &} codes, {@code &#RRGGBB} hex, simple tags, and gradients into legacy section colors.
     */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String withGradients = applyGradients(input);
        String withTags = applySimpleTags(withGradients);
        String withHex = translateHex(withTags);
        return ChatColor.translateAlternateColorCodes('&', withHex);
    }

    /**
     * Prefixes {@code text} with a color taken from the palette and translates the result.
     * The color may be a hex value ({@code #RRGGBB} or {@code &#RRGGBB}) or a legacy code ({@code &6}).
     */
    public static String color(String colorSpec, String text) {
        String body = text == null ? "" : text;
        if (colorSpec == null || colorSpec.isBlank()) {
            return color(body);
        }
        String spec = colorSpec.trim();
        if (spec.startsWith("#")) {
            spec = "&" + spec;
        } else if (spec.length() == 6 && spec.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            spec = "&#" + spec;
        }
        return color(spec + body);
    }

    /** Alias kept for GUI code that predates {@link #color(String)}. */
    public static String legacy(String input) {
        return color(input);
    }

    /**
     * Converts legacy/hex markup into an Adventure component, for Paper APIs that take components
     * (inventory titles, action bars).
     */
    public static Component component(String input) {
        return LegacyComponentSerializer.legacySection().deserialize(color(input));
    }

    /** Alias for {@link #small(String)} used throughout the GUI layer. */
    public static String smallCaps(String input) {
        return small(input);
    }

    /** Latin letters → Unicode small caps. Digits, punctuation, and color codes are preserved. */
    public static String small(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                sb.append(c).append(input.charAt(++i));
            } else if (c >= 'a' && c <= 'z') {
                sb.append(SMALL[c - 'a']);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(SMALL[c - 'A']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String strip(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(color(input));
    }

    public static String formatMoney(Money money) {
        if (money == null) {
            return formatMoney(0.0);
        }
        return formatMoney(money.toDouble());
    }

    public static String formatMoney(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "0.00";
        }
        double abs = Math.abs(amount);
        if (abs < 1_000) {
            return String.format(Locale.US, "%.2f", amount);
        }
        if (abs < 1_000_000) {
            return String.format(Locale.US, "%.2fK", amount / 1_000.0);
        }
        if (abs < 1_000_000_000) {
            return String.format(Locale.US, "%.2fM", amount / 1_000_000.0);
        }
        return String.format(Locale.US, "%.2fB", amount / 1_000_000_000.0);
    }

    public static String formatMultiplier(double multiplier) {
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
            return "0.00x";
        }
        if (multiplier >= 100) {
            return String.format(Locale.US, "%.0fx", multiplier);
        }
        if (multiplier >= 10) {
            return String.format(Locale.US, "%.1fx", multiplier);
        }
        return String.format(Locale.US, "%.2fx", multiplier);
    }

    private static String applySimpleTags(String input) {
        Matcher matcher = SIMPLE_TAG.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = switch (token) {
                case "gold" -> "&#FFD700";
                case "red" -> "&c";
                case "green" -> "&a";
                case "aqua" -> "&b";
                case "yellow" -> "&e";
                case "gray" -> "&7";
                case "dark_gray" -> "&8";
                case "white" -> "&f";
                case "black" -> "&0";
                case "bold" -> "&l";
                case "italic" -> "&o";
                case "reset" -> "&r";
                default -> "";
            };
            if (matcher.group().startsWith("</")) {
                replacement = "&r";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String applyGradients(String input) {
        Matcher matcher = GRADIENT.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String start = matcher.group(1);
            String end = matcher.group(2);
            String text = matcher.group(3);
            matcher.appendReplacement(out, Matcher.quoteReplacement(buildGradient(start, end, text)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String buildGradient(String startHex, String endHex, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int start = parseRgb(startHex);
        int end = parseRgb(endHex);
        String plain = stripSimpleMarkup(text);
        int length = plain.length();
        if (length == 0) {
            return text;
        }

        StringBuilder gradient = new StringBuilder();
        int plainIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                gradient.append(c).append(text.charAt(++i));
                continue;
            }
            double ratio = length == 1 ? 0.5 : (double) plainIndex / (length - 1);
            int color = lerpColor(start, end, ratio);
            gradient.append(hexLegacy(color)).append(c);
            plainIndex++;
        }
        return gradient.toString();
    }

    private static String stripSimpleMarkup(String text) {
        return text.replaceAll("(?i)</?(gold|red|green|aqua|yellow|gray|dark_gray|white|black|bold|italic|reset)>", "");
    }

    private static String translateHex(String input) {
        Matcher matcher = HEX.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder repl = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) {
                repl.append('§').append(Character.toLowerCase(hex.charAt(i)));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(repl.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String hexLegacy(int rgb) {
        String hex = String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder("§x");
        for (int i = 0; i < hex.length(); i++) {
            sb.append('§').append(hex.charAt(i));
        }
        return sb.toString();
    }

    private static int parseRgb(String hex) {
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return 0xFFFFFF;
        }
    }

    private static int lerpColor(int start, int end, double ratio) {
        int sr = (start >> 16) & 0xFF;
        int sg = (start >> 8) & 0xFF;
        int sb = start & 0xFF;
        int er = (end >> 16) & 0xFF;
        int eg = (end >> 8) & 0xFF;
        int eb = end & 0xFF;
        int r = (int) Math.round(sr + (er - sr) * ratio);
        int g = (int) Math.round(sg + (eg - sg) * ratio);
        int b = (int) Math.round(sb + (eb - sb) * ratio);
        return (r << 16) | (g << 8) | b;
    }
}
